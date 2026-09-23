import '../domain/models/source_live_capture_models.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_rule_program.dart';
import '../domain/models/source_runtime_models.dart';
import '../domain/models/web_capture_models.dart';
import '../domain/services/source_package_runtime.dart';

/// Evaluates one bounded WebView document through the existing declarative
/// runtime. The document is never returned to callers or persisted here.
final class SourceLiveDocumentPackageRuntime {
  const SourceLiveDocumentPackageRuntime({required this.fixtureRuntime});

  final SourcePackageRuntime fixtureRuntime;

  SourceRuntimeResult execute({
    required InstalledSourcePackage installedPackage,
    required SourceLiveCaptureRequest request,
    required SourceLiveCaptureResult captureResult,
  }) {
    final snapshot = captureResult.snapshot;
    if (captureResult.packageId != request.packageId ||
        captureResult.packageVersion != request.packageVersion ||
        captureResult.programId != request.programId) {
      return _failure(installedPackage, request, 'capture_identity_mismatch');
    }
    if (captureResult.status != SourceLiveCaptureStatus.captured ||
        snapshot == null) {
      return _failure(
        installedPackage,
        request,
        captureResult.reasonCode ?? 'capture_failed',
      );
    }
    final documentBody = snapshot.documentBody;
    if (documentBody == null) {
      return _failure(installedPackage, request, 'document_missing');
    }

    final redirects = [
      for (final event in snapshot.events)
        if (event.kind == WebRequestKind.navigation && event.isRedirect)
          event.uri,
    ];
    try {
      final result = fixtureRuntime.executeFixture(
        installedPackage: installedPackage,
        programId: request.programId,
        fixture: SourceFixture(
          initialUri: request.webCaptureRequest.initialUri,
          redirectChain: redirects,
          body: documentBody,
        ),
      );
      if (!_matchesIdentity(result, installedPackage, request)) {
        return _failure(installedPackage, request, 'runtime_identity_mismatch');
      }
      return result;
    } on Object {
      return _failure(
        installedPackage,
        request,
        'source_live_document_runtime_failed',
      );
    }
  }

  SourceRuntimeResult _failure(
    InstalledSourcePackage installedPackage,
    SourceLiveCaptureRequest request,
    String code,
  ) {
    return SourceRuntimeResult(
      packageId: installedPackage.package.packageId,
      packageVersion: installedPackage.package.version,
      programId: request.programId,
      status: SourceRuntimeStatus.failed,
      records: const [],
      diagnostics: [
        SourceRuntimeDiagnostic(
          code: _safeCode(code),
          message: 'The captured source document could not be evaluated.',
        ),
      ],
      consumedSteps: 0,
      selectorMatches: 0,
    );
  }

  bool _matchesIdentity(
    SourceRuntimeResult result,
    InstalledSourcePackage installedPackage,
    SourceLiveCaptureRequest request,
  ) {
    return result.packageId == installedPackage.package.packageId &&
        result.packageVersion == installedPackage.package.version &&
        result.programId == request.programId;
  }

  static String _safeCode(String code) =>
      RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(code)
      ? code
      : 'source_live_document_runtime_failed';
}
