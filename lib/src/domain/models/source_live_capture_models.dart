import 'package:pub_semver/pub_semver.dart';

import 'web_capture_models.dart';

/// The lifecycle outcome of one admitted live source capture.
enum SourceLiveCaptureStatus {
  captured,
  invalidSnapshot,
  budgetExceeded,
  failed,
  superseded,
  closed,
}

/// Binds a WebView capture request to the exact source package program that
/// is allowed to receive its result.
final class SourceLiveCaptureRequest {
  SourceLiveCaptureRequest({
    required String packageId,
    required this.packageVersion,
    required String programId,
    required this.webCaptureRequest,
  }) : packageId = _packageId(packageId),
       programId = _programId(programId);

  final String packageId;
  final Version packageVersion;
  final String programId;
  final WebCaptureRequest webCaptureRequest;
}

/// A bounded, provenance-preserving result for one live capture operation.
///
/// A snapshot is retained only for an accepted capture. All other outcomes
/// carry one safe reason token and never retain a partially trusted snapshot.
final class SourceLiveCaptureResult {
  SourceLiveCaptureResult({
    required String packageId,
    required this.packageVersion,
    required String programId,
    required this.status,
    this.snapshot,
    this.reasonCode,
  }) : packageId = _packageId(packageId),
       programId = _programId(programId) {
    final isCaptured = status == SourceLiveCaptureStatus.captured;
    if (isCaptured) {
      if (snapshot == null ||
          snapshot!.stopReason != WebCaptureStopReason.completed ||
          reasonCode != null) {
        throw ArgumentError(
          'A captured result requires a completed snapshot and no reason.',
        );
      }
      return;
    }

    if (snapshot != null || reasonCode == null || !_safeToken(reasonCode!)) {
      throw ArgumentError(
        'A non-captured result requires one safe reason and no snapshot.',
      );
    }
  }

  final String packageId;
  final Version packageVersion;
  final String programId;
  final SourceLiveCaptureStatus status;
  final WebCaptureSnapshot? snapshot;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'packageId': packageId,
    'packageVersion': packageVersion.toString(),
    'programId': programId,
    'status': status.name,
    'hasSnapshot': snapshot != null,
    'eventCount': snapshot?.events.length,
    'candidateCount': snapshot?.candidates.length,
    'cookieCount': snapshot?.cookies.length,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

String _packageId(String value) {
  final normalized = value.trim();
  if (value != normalized ||
      normalized.length > 128 ||
      !RegExp(r'^[a-z0-9]+(?:[._-][a-z0-9]+)*$').hasMatch(normalized)) {
    throw ArgumentError.value(
      value,
      'packageId',
      'Must be a lower-case package identifier.',
    );
  }
  return normalized;
}

String _programId(String value) {
  final normalized = value.trim();
  if (value != normalized ||
      !RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(normalized)) {
    throw ArgumentError.value(
      value,
      'programId',
      'Must be a lower-case identifier.',
    );
  }
  return normalized;
}

bool _safeToken(String value) =>
    RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value);
