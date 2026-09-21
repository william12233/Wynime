import '../domain/models/source_http_models.dart';
import '../domain/models/source_rule_program.dart';
import '../domain/models/source_runtime_models.dart';
import '../domain/services/source_package_runtime.dart';
import 'source_live_http_request_coordinator.dart';
import 'source_live_http_request_executor.dart';

/// Composes one admitted live HTTP response with the existing declarative
/// source-package runtime.
///
/// The HTTP response is converted to a [SourceFixture] only in memory. This
/// boundary does not infer requests, execute source code, normalize records,
/// persist response data, or expose raw response data to its caller.
final class SourceLiveHttpPackageRuntime {
  const SourceLiveHttpPackageRuntime({
    required this.httpExecutor,
    required this.fixtureRuntime,
  });

  final SourceLiveHttpRequestExecutor httpExecutor;
  final SourcePackageRuntime fixtureRuntime;

  Future<SourceRuntimeResult> execute(SourceLiveHttpRequestPlan plan) async {
    final results = await executePrograms(
      requestPlan: plan,
      programIds: [plan.programId],
    );
    return results[plan.programId] ??
        _failure(plan, 'source_live_execution_invalid');
  }

  /// Executes one admitted GET and evaluates the bounded response against
  /// several already-declared programs. This is the only multi-result hook;
  /// all programs receive the same in-memory response and no raw response is
  /// returned to the caller.
  Future<Map<String, SourceRuntimeResult>> executePrograms({
    required SourceLiveHttpRequestPlan requestPlan,
    required Iterable<String> programIds,
  }) async {
    final ids = <String>[];
    final seen = <String>{};
    for (final value in programIds) {
      final id = value.trim();
      if (!RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(id) || !seen.add(id)) {
        return {
          requestPlan.programId: _failure(
            requestPlan,
            'source_live_programs_invalid',
          ),
        };
      }
      if (ids.length == 4) {
        return {
          requestPlan.programId: _failure(
            requestPlan,
            'source_live_programs_invalid',
          ),
        };
      }
      ids.add(id);
    }
    if (ids.isEmpty) {
      return {
        requestPlan.programId: _failure(
          requestPlan,
          'source_live_programs_invalid',
        ),
      };
    }

    SourceLiveHttpExecutionResult execution;
    try {
      execution = await httpExecutor.execute(requestPlan);
    } on Object {
      return {
        for (final id in ids)
          id: _failureForProgram(requestPlan, id, 'source_live_runtime_failed'),
      };
    }

    if (execution.status == SourceLiveHttpExecutionStatus.completed) {
      final response = execution.response;
      if (response == null) {
        return {
          for (final id in ids)
            id: _failureForProgram(
              requestPlan,
              id,
              'source_live_response_missing',
            ),
        };
      }

      final fixture = SourceFixture(
        initialUri: requestPlan.request.uri,
        redirectChain: response.redirectChain,
        body: response.body,
      );
      final results = <String, SourceRuntimeResult>{};
      for (final id in ids) {
        try {
          final evaluated = fixtureRuntime.executeFixture(
            installedPackage: requestPlan.installedPackage,
            programId: id,
            fixture: fixture,
          );
          final plan = SourceLiveHttpRequestPlan(
            installedPackage: requestPlan.installedPackage,
            programId: id,
            request: requestPlan.request,
          );
          results[id] = _matchesPlanIdentity(evaluated, plan)
              ? evaluated
              : _failureForProgram(
                  requestPlan,
                  id,
                  'runtime_identity_mismatch',
                );
        } on Object {
          results[id] = _failureForProgram(
            requestPlan,
            id,
            'source_live_runtime_failed',
          );
        }
      }
      return Map<String, SourceRuntimeResult>.unmodifiable(results);
    }

    final admission = execution.admissionResult;
    if (admission != null) {
      return {
        for (final id in ids)
          id: _admissionFailureForProgram(requestPlan, id, admission.status),
      };
    }

    final transport = execution.transportResult;
    if (transport != null) {
      final code = _transportFailureCode(transport);
      return {
        for (final id in ids)
          _failureKey(id): _failureForProgram(requestPlan, id, code),
      };
    }

    return {
      for (final id in ids)
        id: _failureForProgram(
          requestPlan,
          id,
          'source_live_execution_invalid',
        ),
    };
  }

  bool _matchesPlanIdentity(
    SourceRuntimeResult result,
    SourceLiveHttpRequestPlan plan,
  ) {
    final package = plan.installedPackage.package;
    return result.packageId == package.packageId &&
        result.packageVersion.toString() == package.version.toString() &&
        result.programId == plan.programId;
  }

  SourceRuntimeResult _admissionFailure(
    SourceLiveHttpRequestPlan plan,
    SourceLiveHttpRequestStatus status,
  ) {
    return switch (status) {
      SourceLiveHttpRequestStatus.consentRequired => _result(
        plan,
        status: SourceRuntimeStatus.consentRequired,
        code: 'consent_required',
      ),
      SourceLiveHttpRequestStatus.disabled => _result(
        plan,
        status: SourceRuntimeStatus.disabled,
        code: 'package_disabled',
      ),
      SourceLiveHttpRequestStatus.incompatible => _result(
        plan,
        status: SourceRuntimeStatus.incompatible,
        code: 'incompatible_wynime_version',
      ),
      SourceLiveHttpRequestStatus.programNotFound => _failure(
        plan,
        'program_not_found',
      ),
      SourceLiveHttpRequestStatus.invalidRequest => _failure(
        plan,
        'source_http_request_invalid',
      ),
      SourceLiveHttpRequestStatus.failed => _failure(
        plan,
        'package_preflight_failed',
      ),
      SourceLiveHttpRequestStatus.ready => _failure(
        plan,
        'source_live_execution_invalid',
      ),
    };
  }

  SourceRuntimeResult _failure(SourceLiveHttpRequestPlan plan, String code) =>
      _result(plan, status: SourceRuntimeStatus.failed, code: code);

  SourceRuntimeResult _failureForProgram(
    SourceLiveHttpRequestPlan plan,
    String programId,
    String code,
  ) {
    return _result(
      SourceLiveHttpRequestPlan(
        installedPackage: plan.installedPackage,
        programId: programId,
        request: plan.request,
      ),
      status: SourceRuntimeStatus.failed,
      code: code,
    );
  }

  SourceRuntimeResult _admissionFailureForProgram(
    SourceLiveHttpRequestPlan plan,
    String programId,
    SourceLiveHttpRequestStatus status,
  ) {
    final candidate = SourceLiveHttpRequestPlan(
      installedPackage: plan.installedPackage,
      programId: programId,
      request: plan.request,
    );
    return _admissionFailure(candidate, status);
  }

  String _failureKey(String value) => value;

  SourceRuntimeResult _result(
    SourceLiveHttpRequestPlan plan, {
    required SourceRuntimeStatus status,
    required String code,
  }) {
    final package = plan.installedPackage.package;
    final safeCode = _safeCode(code);
    return SourceRuntimeResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: plan.programId,
      status: status,
      records: const [],
      diagnostics: [
        SourceRuntimeDiagnostic(code: safeCode, message: _messageFor(safeCode)),
      ],
      consumedSteps: 0,
      selectorMatches: 0,
    );
  }

  String _transportFailureCode(SourceHttpTransportResult transport) {
    return switch (transport.status) {
      SourceHttpTransportStatus.closed => 'transport_closed',
      SourceHttpTransportStatus.policyRejected => 'source_policy_rejected',
      SourceHttpTransportStatus.redirectUriNotAllowed =>
        'redirect_uri_not_allowed',
      SourceHttpTransportStatus.redirectBudgetExceeded =>
        'redirect_budget_exceeded',
      SourceHttpTransportStatus.responseTooLarge => 'response_too_large',
      SourceHttpTransportStatus.httpError =>
        transport.httpStatus == null
            ? 'http_error'
            : 'http_status_${transport.httpStatus}',
      SourceHttpTransportStatus.timeout =>
        transport.reasonCode == 'response_timeout'
            ? 'response_timeout'
            : 'request_timeout',
      SourceHttpTransportStatus.networkError => 'network_error',
      SourceHttpTransportStatus.invalidResponse => 'invalid_response',
      SourceHttpTransportStatus.failed => 'source_http_failed',
      SourceHttpTransportStatus.success => 'source_live_execution_invalid',
    };
  }

  static String _safeCode(String code) {
    if (code.length <= 64 && RegExp(r'^[a-z][a-z0-9_]*$').hasMatch(code)) {
      return code;
    }
    return 'source_live_runtime_failed';
  }

  static String _messageFor(String code) {
    if (code.startsWith('http_status_')) {
      return 'The live source returned an HTTP error.';
    }
    return switch (code) {
      'consent_required' => 'The source package requires explicit consent.',
      'package_disabled' => 'The source package is not enabled.',
      'incompatible_wynime_version' =>
        'The source package is incompatible with this Wynime version.',
      'program_not_found' => 'The requested source program was not found.',
      'source_http_request_invalid' =>
        'The live source request did not pass package policy.',
      'package_preflight_failed' =>
        'The source package could not pass its preflight checks.',
      'transport_closed' => 'The live source transport is closed.',
      'source_policy_rejected' =>
        'The live source response was rejected by package policy.',
      'redirect_uri_not_allowed' =>
        'The live source redirect is outside package policy.',
      'redirect_budget_exceeded' =>
        'The live source redirect budget was exceeded.',
      'response_too_large' => 'The live source response exceeds its budget.',
      'request_timeout' ||
      'response_timeout' => 'The live source request timed out.',
      'network_error' => 'The live source network request failed.',
      'invalid_response' => 'The live source response was invalid.',
      'http_error' => 'The live source returned an HTTP error.',
      'source_http_failed' => 'The live source request failed.',
      'source_live_response_missing' =>
        'The live source response was unavailable.',
      'runtime_identity_mismatch' =>
        'The live source runtime returned an unexpected identity.',
      'source_live_execution_invalid' =>
        'The live source execution result was invalid.',
      'source_live_runtime_failed' => 'The live source evaluation failed.',
      _ => 'The live source evaluation failed.',
    };
  }
}
