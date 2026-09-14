import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

import 'source_identity.dart';
import 'web_capture_models.dart';

enum SourcePlayableSourceNormalizationStatus {
  available,
  notFound,
  disabled,
  consentRequired,
  incompatible,
  failed,
}

final class SourcePlayableSourceFieldMapping {
  SourcePlayableSourceFieldMapping({
    required String sourceKeyField,
    required String labelField,
    required String kindField,
    required String mediaUriField,
    required String pageUriField,
  }) : sourceKeyField = _fieldName(sourceKeyField, 'sourceKeyField'),
       labelField = _fieldName(labelField, 'labelField'),
       kindField = _fieldName(kindField, 'kindField'),
       mediaUriField = _fieldName(mediaUriField, 'mediaUriField'),
       pageUriField = _fieldName(pageUriField, 'pageUriField') {
    final fields = {
      this.sourceKeyField,
      this.labelField,
      this.kindField,
      this.mediaUriField,
      this.pageUriField,
    };
    if (fields.length != 5) {
      throw ArgumentError(
        'Playable source mapping fields must all be different.',
      );
    }
  }

  final String sourceKeyField;
  final String labelField;
  final String kindField;
  final String mediaUriField;
  final String pageUriField;

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

final class SourcePlayableSource {
  SourcePlayableSource({
    required this.episode,
    required String sourceKey,
    required String label,
    required this.kind,
    required Uri mediaUri,
    required Uri pageUri,
  }) : sourceKey = _requiredText(sourceKey, 'sourceKey', 128),
       label = _requiredText(label, 'label', 256),
       mediaUri = _safeRemoteUri(mediaUri, 'mediaUri'),
       pageUri = _safeRemoteUri(pageUri, 'pageUri');

  final SourceEpisodeIdentity episode;
  final String sourceKey;
  final String label;
  final WebCandidateKind kind;
  final Uri mediaUri;
  final Uri pageUri;

  Map<String, Object?> toRedactedDiagnostic() => {
    'episode': {
      'sourceIdPresent': episode.sourceId.isNotEmpty,
      'lineIdLength': episode.lineId.length,
      'subjectIdLength': episode.subjectId.length,
      'episodeIdLength': episode.episodeId.length,
    },
    'kind': kind.name,
    'mediaScheme': mediaUri.scheme,
    'mediaHost': mediaUri.host,
    'mediaPathSegmentCount': mediaUri.pathSegments.length,
    'pageScheme': pageUri.scheme,
    'pageHost': pageUri.host,
    'pagePathSegmentCount': pageUri.pathSegments.length,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

final class SourcePlayableSourceNormalizationDiagnostic {
  SourcePlayableSourceNormalizationDiagnostic({
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
    if (_hasControlCharacter(normalized)) {
      throw ArgumentError.value(value, name, 'Must not contain controls.');
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

final class SourcePlayableSourceNormalizationResult {
  SourcePlayableSourceNormalizationResult({
    required String packageId,
    required this.packageVersion,
    required String programId,
    required this.status,
    required Iterable<SourcePlayableSource> results,
    required Iterable<SourcePlayableSourceNormalizationDiagnostic> diagnostics,
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
  final SourcePlayableSourceNormalizationStatus status;
  final UnmodifiableListView<SourcePlayableSource> results;
  final UnmodifiableListView<SourcePlayableSourceNormalizationDiagnostic>
  diagnostics;

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

String _requiredText(String value, String name, int maxLength) {
  final normalized = value.trim();
  if (normalized.isEmpty || normalized.length > maxLength) {
    throw ArgumentError.value(
      value,
      name,
      'Must contain between 1 and $maxLength characters.',
    );
  }
  if (_hasControlCharacter(normalized)) {
    throw ArgumentError.value(value, name, 'Must not contain controls.');
  }
  return normalized;
}

Uri _safeRemoteUri(Uri value, String name) {
  final scheme = value.scheme.toLowerCase();
  if ((scheme != 'https' && scheme != 'http') ||
      value.host.isEmpty ||
      value.userInfo.isNotEmpty) {
    throw ArgumentError.value(
      value,
      name,
      'Must be an HTTP(S) URI without user-info.',
    );
  }
  return value;
}

bool _hasControlCharacter(String value) {
  return value.codeUnits.any(
    (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
  );
}
