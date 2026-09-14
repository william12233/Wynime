import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

enum SourceRuntimeStatus {
  available,
  notFound,
  disabled,
  consentRequired,
  incompatible,
  failed,
}

final class SourceRuntimeRecord {
  SourceRuntimeRecord(Map<String, String> values)
    : values = UnmodifiableMapView(Map<String, String>.unmodifiable(values));

  final UnmodifiableMapView<String, String> values;
}

final class SourceRuntimeDiagnostic {
  SourceRuntimeDiagnostic({
    required String code,
    required String message,
    this.recordIndex,
    String? fieldName,
  }) : code = _bounded(code, 'code', 64),
       message = _bounded(message, 'message', 160),
       fieldName = fieldName == null
           ? null
           : _bounded(fieldName, 'fieldName', 64) {
    if (recordIndex != null && recordIndex! < 0) {
      throw ArgumentError.value(
        recordIndex,
        'recordIndex',
        'Must not be negative.',
      );
    }
  }

  final String code;
  final String message;
  final int? recordIndex;
  final String? fieldName;

  static String _bounded(String value, String name, int maxLength) {
    final normalized = value.trim();
    if (normalized.isEmpty || normalized.length > maxLength) {
      throw ArgumentError.value(
        value,
        name,
        'Must contain between 1 and $maxLength characters.',
      );
    }
    return normalized;
  }
}

final class SourceRuntimeResult {
  SourceRuntimeResult({
    required String packageId,
    required this.packageVersion,
    required String programId,
    required this.status,
    required Iterable<SourceRuntimeRecord> records,
    required Iterable<SourceRuntimeDiagnostic> diagnostics,
    required this.consumedSteps,
    required this.selectorMatches,
  }) : packageId = packageId.trim(),
       programId = programId.trim(),
       records = UnmodifiableListView(_boundedList(records, 'records')),
       diagnostics = UnmodifiableListView(
         _boundedList(diagnostics, 'diagnostics'),
       ) {
    if (this.packageId.isEmpty || this.packageId.length > 128) {
      throw ArgumentError.value(
        packageId,
        'packageId',
        'Must contain between 1 and 128 characters.',
      );
    }
    if (this.programId.isEmpty || this.programId.length > 64) {
      throw ArgumentError.value(
        programId,
        'programId',
        'Must contain between 1 and 64 characters.',
      );
    }
    if (consumedSteps < 0 || selectorMatches < 0) {
      throw ArgumentError('Runtime metrics must not be negative.');
    }
  }

  final String packageId;
  final Version packageVersion;
  final String programId;
  final SourceRuntimeStatus status;
  final UnmodifiableListView<SourceRuntimeRecord> records;
  final UnmodifiableListView<SourceRuntimeDiagnostic> diagnostics;
  final int consumedSteps;
  final int selectorMatches;

  static List<T> _boundedList<T>(Iterable<T> values, String name) {
    const maxLength = 1000;
    final result = <T>[];
    final iterator = values.iterator;
    while (iterator.moveNext()) {
      if (result.length == maxLength) {
        throw ArgumentError.value(
          values,
          name,
          'Must contain at most $maxLength items.',
        );
      }
      result.add(iterator.current);
    }
    return List<T>.unmodifiable(result);
  }
}
