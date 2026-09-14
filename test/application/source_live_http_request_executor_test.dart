import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test(
    'ready admission forwards the exact request and returns the response',
    () async {
      final request = _request();
      final transport = _RecordingTransport(
        result: SourceHttpTransportResult(
          status: SourceHttpTransportStatus.success,
          response: SourceHttpResponse(
            statusCode: 200,
            finalUri: request.uri,
            redirectChain: const [],
            body: '{"ok":true}',
            contentType: 'application/json',
          ),
        ),
      );
      final result = await _executor(
        transport,
      ).execute(_plan(request: request));

      expect(result.status, SourceLiveHttpExecutionStatus.completed);
      expect(result.response!.body, '{"ok":true}');
      expect(result.failureStage, isNull);
      expect(transport.requests, [same(request)]);
    },
  );

  test('non-ready package admission short-circuits transport', () async {
    final transport = _RecordingTransport(
      result: SourceHttpTransportResult(
        status: SourceHttpTransportStatus.success,
        response: SourceHttpResponse(
          statusCode: 200,
          finalUri: Uri.parse('https://example.com/unused'),
          redirectChain: const [],
          body: 'unused',
        ),
      ),
    );
    final result = await _executor(transport).execute(
      _plan(installedPackage: _installed(status: SourcePackageStatus.disabled)),
    );

    expect(result.status, SourceLiveHttpExecutionStatus.notCompleted);
    expect(result.failureStage, SourceLiveHttpExecutionFailureStage.admission);
    expect(result.admissionResult!.reasonCode, 'package_disabled');
    expect(result.transportResult, isNull);
    expect(result.response, isNull);
    expect(transport.requests, isEmpty);
  });

  test('transport failures stay typed and do not expose a response', () async {
    final transport = _RecordingTransport(
      result: SourceHttpTransportResult(
        status: SourceHttpTransportStatus.httpError,
        reasonCode: 'http_status_429',
        httpStatus: 429,
      ),
    );
    final result = await _executor(transport).execute(_plan());

    expect(result.status, SourceLiveHttpExecutionStatus.notCompleted);
    expect(result.failureStage, SourceLiveHttpExecutionFailureStage.transport);
    expect(result.transportResult!.status, SourceHttpTransportStatus.httpError);
    expect(result.transportResult!.httpStatus, 429);
    expect(result.response, isNull);
  });

  test('a throwing transport is collapsed to a stable safe failure', () async {
    final result = await _executor(
      _RecordingTransport(error: StateError('secret upstream detail')),
    ).execute(_plan());

    expect(result.status, SourceLiveHttpExecutionStatus.notCompleted);
    expect(result.failureStage, SourceLiveHttpExecutionFailureStage.transport);
    expect(result.transportResult!.reasonCode, 'source_http_failed');
    expect(result.toString(), isNot(contains('secret')));
  });

  test('execution result rejects impossible mixtures and redacts URL data', () {
    final response = SourceHttpResponse(
      statusCode: 200,
      finalUri: Uri.parse('https://example.com/secret'),
      redirectChain: const [],
      body: 'cookie-value',
    );
    expect(
      () => SourceLiveHttpExecutionResult(
        status: SourceLiveHttpExecutionStatus.completed,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceLiveHttpExecutionResult(
        status: SourceLiveHttpExecutionStatus.notCompleted,
        failureStage: SourceLiveHttpExecutionFailureStage.transport,
        transportResult: SourceHttpTransportResult(
          status: SourceHttpTransportStatus.success,
          response: response,
        ),
      ),
      throwsArgumentError,
    );
    final failure = SourceLiveHttpExecutionResult(
      status: SourceLiveHttpExecutionStatus.notCompleted,
      failureStage: SourceLiveHttpExecutionFailureStage.transport,
      transportResult: SourceHttpTransportResult(
        status: SourceHttpTransportStatus.networkError,
        reasonCode: 'network_error',
      ),
    );
    expect(failure.toString(), isNot(contains('https://')));
    expect(failure.toString(), isNot(contains('cookie-value')));
  });
}

SourceLiveHttpRequestExecutor _executor(_RecordingTransport transport) =>
    SourceLiveHttpRequestExecutor(
      requestCoordinator: SourceLiveHttpRequestCoordinator(
        wynimeVersion: Version.parse('1.0.0'),
      ),
      transport: transport,
    );

SourceLiveHttpRequestPlan _plan({
  InstalledSourcePackage? installedPackage,
  SourceHttpRequest? request,
}) {
  final policy = testSourcePolicy();
  return SourceLiveHttpRequestPlan(
    installedPackage: installedPackage ?? _installed(policy: policy),
    programId: 'search',
    request: request ?? _request(policy: policy),
  );
}

SourceHttpRequest _request({SourceSecurityPolicy? policy}) => SourceHttpRequest(
  uri: Uri.parse('https://example.com/search?q=secret'),
  securityPolicy: policy ?? testSourcePolicy(),
  headers: const {'accept': 'text/html'},
  timeout: const Duration(seconds: 2),
);

InstalledSourcePackage _installed({
  SourcePackageManifest? package,
  SourceSecurityPolicy? policy,
  SourcePackageStatus status = SourcePackageStatus.enabled,
}) => InstalledSourcePackage(
  package: package ?? _package(policy: policy),
  status: status,
  requiresConsent: false,
  requiresReconsent: false,
);

SourcePackageManifest _package({SourceSecurityPolicy? policy}) =>
    SourcePackageManifest(
      schemaVersion: 1,
      packageId: 'example.anime',
      displayName: 'Example Anime',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      securityPolicy: policy ?? testSourcePolicy(),
      programs: [
        SourceRuleProgram(
          programId: 'search',
          documentKind: SourceDocumentKind.json,
          rootSelector: SourceSelector(
            kind: SourceSelectorKind.jsonPath,
            expression: r'$',
          ),
          fields: [
            SourceFieldRule(name: 'value', valueKind: SourceValueKind.raw),
          ],
          resultLimit: 1,
        ),
      ],
    );

final class _RecordingTransport implements SourceHttpTransport {
  _RecordingTransport({this.result, this.error});

  final SourceHttpTransportResult? result;
  final Object? error;
  final requests = <SourceHttpRequest>[];

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    requests.add(request);
    final failure = error;
    if (failure != null) {
      throw failure;
    }
    return result!;
  }

  @override
  Future<void> close() async {}
}
