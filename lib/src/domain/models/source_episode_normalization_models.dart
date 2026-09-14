import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

import 'source_models.dart';

enum SourceEpisodeNormalizationStatus {
  available,
  notFound,
  disabled,
  consentRequired,
  incompatible,
  failed,
}

final class SourceEpisodeFieldMapping {
  SourceEpisodeFieldMapping({
    required String lineIdField,
    required String subjectIdField,
    required String episodeIdField,
    required String titleField,
  }) : lineIdField = _fieldName(lineIdField, 'lineIdField'),
       subjectIdField = _fieldName(subjectIdField, 'subjectIdField'),
       episodeIdField = _fieldName(episodeIdField, 'episodeIdField'),
       titleField = _fieldName(titleField, 'titleField') {
    final fields = {
      this.lineIdField,
      this.subjectIdField,
      this.episodeIdField,
      this.titleField,
    };
    if (fields.length != 4) {
      throw ArgumentError(
        'Episode identity and title fields must all be different.',
      );
    }
  }

  final String lineIdField;
  final String subjectIdField;
  final String episodeIdField;
  final String titleField;

  static String _fieldName(String value, String name) {
    final normalized = value.trim();
    if (!RegExp(r'^[A-Za-z][A-Za-z0-9_]{0,63}$').hasMatch(normalized)) {
      throw ArgumentError.value(
        value,
        name,
        'Must be an identifier with at most 64 characters.',
      );
    }
    return normalized;
  }
}

final class SourceEpisodeNormalizationDiagnostic {
  SourceEpisodeNormalizationDiagnostic({
    required String code,
    required String message,
    this.recordIndex,
    String? fieldName,
  }) : code = _bounded(code, 'code', 64),
       message = _bounded(message, 'message', 160),
       fieldName = fieldName == null
           ? null
           : _fieldName(fieldName, 'fieldName') {
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

  static String _fieldName(String value, String name) {
    final normalized = value.trim();
    if (!RegExp(r'^[A-Za-z][A-Za-z0-9_]{0,63}$').hasMatch(normalized)) {
      throw ArgumentError.value(
        value,
        name,
        'Must be an identifier with at most 64 characters.',
      );
    }
    return normalized;
  }
}

final class SourceEpisodeNormalizationResult {
  SourceEpisodeNormalizationResult({
    required String packageId,
    required this.packageVersion,
    required String programId,
    required this.status,
    required Iterable<SourceEpisode> results,
    required Iterable<SourceEpisodeNormalizationDiagnostic> diagnostics,
  }) : packageId = packageId.trim(),
       programId = programId.trim(),
       results = UnmodifiableListView(_boundedList(results, 'results')),
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
  }

  final String packageId;
  final Version packageVersion;
  final String programId;
  final SourceEpisodeNormalizationStatus status;
  final UnmodifiableListView<SourceEpisode> results;
  final UnmodifiableListView<SourceEpisodeNormalizationDiagnostic> diagnostics;

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
