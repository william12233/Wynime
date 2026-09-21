import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

import 'source_models.dart';

enum SourceSubjectDetailsStatus {
  available,
  notFound,
  disabled,
  consentRequired,
  incompatible,
  challengeRequired,
  failed,
}

final class SourceSubjectDetailsDiagnostic {
  SourceSubjectDetailsDiagnostic({
    required String code,
    required String message,
  }) : code = _safeToken(code, 'code', 64),
       message = _safeText(message, 'message', 160);

  final String code;
  final String message;
}

final class SourceSubjectDetailsResult {
  SourceSubjectDetailsResult({
    required String packageId,
    required this.packageVersion,
    required String programId,
    required this.status,
    this.details,
    Iterable<SourceSubjectDetailsDiagnostic> diagnostics = const [],
  }) : packageId = _safeText(packageId, 'packageId', 128),
       programId = _safeText(programId, 'programId', 64),
       diagnostics = UnmodifiableListView(_boundedDiagnostics(diagnostics)) {
    if (status == SourceSubjectDetailsStatus.available && details == null) {
      throw ArgumentError('Available subject details require details.');
    }
    if (status != SourceSubjectDetailsStatus.available && details != null) {
      throw ArgumentError(
        'Only available subject details may contain details.',
      );
    }
  }

  final String packageId;
  final Version packageVersion;
  final String programId;
  final SourceSubjectDetailsStatus status;
  final SourceSubjectDetails? details;
  final UnmodifiableListView<SourceSubjectDetailsDiagnostic> diagnostics;

  static List<SourceSubjectDetailsDiagnostic> _boundedDiagnostics(
    Iterable<SourceSubjectDetailsDiagnostic> values,
  ) {
    final result = <SourceSubjectDetailsDiagnostic>[];
    for (final value in values) {
      if (result.length == 64) {
        throw ArgumentError.value(
          values,
          'diagnostics',
          'A result may contain at most 64 diagnostics.',
        );
      }
      result.add(value);
    }
    return List<SourceSubjectDetailsDiagnostic>.unmodifiable(result);
  }
}

String _safeText(String value, String name, int maxLength) {
  final normalized = value.trim();
  if (normalized.isEmpty ||
      normalized.length > maxLength ||
      normalized != value) {
    throw ArgumentError.value(value, name, 'Must be a bounded trimmed value.');
  }
  return normalized;
}

String _safeToken(String value, String name, int maxLength) {
  final normalized = _safeText(value, name, maxLength);
  if (!RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(normalized)) {
    throw ArgumentError.value(value, name, 'Must be a safe diagnostic token.');
  }
  return normalized;
}
