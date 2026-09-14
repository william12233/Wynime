import 'dart:collection';
import 'dart:convert';

import 'source_episode_normalization_models.dart';
import 'source_playable_normalization_models.dart';
import 'source_search_normalization_models.dart';

/// The live operations that a source package may declare.
///
/// The enum is deliberately closed. Adding another operation is a capability
/// change and therefore requires a new schema decision instead of being
/// silently accepted by an older package runtime.
enum SourcePackageLiveOperationKind { search, episode, playableSource }

/// A bounded URI template used by a package-declared live GET operation.
///
/// Only placeholders in the path or query are accepted. The scheme, authority,
/// port and fragment are fixed package data. Placeholder values are encoded as
/// individual URI components when [expand] is called.
final class SourcePackageUriTemplate {
  SourcePackageUriTemplate(String template)
    : template = _validate(template),
      placeholders = UnmodifiableSetView(_placeholders(template));

  static const maxLength = 2048;
  static const maxExpandedBytes = 16 * 1024;
  static final _placeholderPattern = RegExp(r'\{([a-z][A-Za-z0-9_]*)\}');
  static final _schemePattern = RegExp(
    r'^([a-z][a-z0-9+.-]*)://',
    caseSensitive: false,
  );
  static final _publicDnsHostPattern = RegExp(
    r'^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*'
    r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$',
  );

  final String template;
  final UnmodifiableSetView<String> placeholders;

  Uri expand(Map<String, String> values) {
    for (final placeholder in placeholders) {
      final value = values[placeholder];
      if (value == null || !_validInput(value)) {
        throw ArgumentError(
          'Placeholder $placeholder must be bounded, non-empty and free of controls.',
        );
      }
    }

    final expanded = template.replaceAllMapped(_placeholderPattern, (match) {
      return Uri.encodeComponent(values[match.group(1)!]!);
    });
    if (utf8.encode(expanded).length > maxExpandedBytes) {
      throw ArgumentError(
        'The expanded URI exceeds the $maxExpandedBytes-byte limit.',
      );
    }
    final uri = Uri.tryParse(expanded);
    if (uri == null || !_isSafeExpandedUri(uri)) {
      throw ArgumentError('The expanded URI is not a safe HTTP(S) URI.');
    }
    return uri;
  }

  Map<String, Object?> toRedactedDiagnostic() => {
    'placeholderCount': placeholders.length,
    'placeholders': placeholders.toList()..sort(),
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static String _validate(String value) {
    final normalized = value.trim();
    if (value != normalized ||
        normalized.isEmpty ||
        normalized.length > maxLength ||
        _hasControlCharacter(normalized) ||
        normalized.contains('#')) {
      throw ArgumentError(
        'The URI template must be bounded and contain no controls or fragments.',
      );
    }

    final schemeMatch = _schemePattern.firstMatch(normalized);
    if (schemeMatch == null) {
      throw ArgumentError(
        'A URI template must use an explicit HTTP(S) scheme and authority.',
      );
    }
    final scheme = schemeMatch.group(1)!.toLowerCase();
    if (scheme != 'https' && scheme != 'http') {
      throw ArgumentError('Only HTTP(S) URI templates are supported.');
    }

    final authorityEnd = _firstIndexAfter(
      normalized,
      schemeMatch.end,
      const <String>['/', '?'],
    );
    final authority = normalized.substring(schemeMatch.end, authorityEnd);
    if (authority.contains('{') || authority.contains('}')) {
      throw ArgumentError('Placeholders are not allowed in the URI authority.');
    }

    final safeParse = normalized.replaceAllMapped(
      _placeholderPattern,
      (_) => 'placeholder',
    );
    if (safeParse.contains('{') || safeParse.contains('}')) {
      throw ArgumentError('Only named placeholders are supported.');
    }

    final uri = Uri.tryParse(safeParse);
    if (uri == null || !_isSafeExpandedUri(uri)) {
      throw ArgumentError(
        'The fixed URI authority is not a safe public HTTP(S) URI.',
      );
    }
    return normalized;
  }

  static Set<String> _placeholders(String value) {
    final result = <String>{};
    var cursor = 0;
    for (final match in _placeholderPattern.allMatches(value)) {
      final before = value.substring(cursor, match.start);
      if (before.contains('{') || before.contains('}')) {
        throw ArgumentError('Only named placeholders are supported.');
      }
      result.add(match.group(1)!);
      cursor = match.end;
    }
    final remainder = value.substring(cursor);
    if (remainder.contains('{') || remainder.contains('}')) {
      throw ArgumentError('Only named placeholders are supported.');
    }
    return Set<String>.unmodifiable(result);
  }

  static bool _isSafeExpandedUri(Uri uri) {
    final scheme = uri.scheme.toLowerCase();
    if ((scheme != 'https' && scheme != 'http') ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.fragment.isNotEmpty ||
        !_usesStandardPort(uri, scheme)) {
      return false;
    }
    final host = uri.host.toLowerCase().replaceFirst(RegExp(r'\.$'), '');
    return _publicDnsHostPattern.hasMatch(host) &&
        host != 'localhost' &&
        !host.endsWith('.localhost') &&
        !host.endsWith('.local') &&
        !_isIpv4Literal(host) &&
        !host.contains(':');
  }

  static bool _usesStandardPort(Uri uri, String scheme) {
    if (!uri.hasPort) return true;
    return (scheme == 'https' && uri.port == 443) ||
        (scheme == 'http' && uri.port == 80);
  }

  static bool _isIpv4Literal(String host) {
    final parts = host.split('.');
    if (parts.length != 4) return false;
    return parts.every((part) {
      final value = int.tryParse(part);
      return value != null && value >= 0 && value <= 255;
    });
  }

  static int _firstIndexAfter(String value, int start, List<String> values) {
    final indexes = values
        .map((separator) => value.indexOf(separator, start))
        .where((index) => index >= start)
        .toList(growable: false);
    if (indexes.isEmpty) return value.length;
    indexes.sort();
    return indexes.first;
  }

  static bool _validInput(String value) {
    final normalized = value.trim();
    return normalized == value &&
        normalized.isNotEmpty &&
        normalized.length <= 256 &&
        !_hasControlCharacter(normalized);
  }

  static bool _hasControlCharacter(String value) => value.codeUnits.any(
    (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
  );
}

/// One package-declared binding from a live operation to an existing rule
/// program and an existing normalizer field mapping.
final class SourcePackageLiveOperation {
  SourcePackageLiveOperation({
    required this.kind,
    required String programId,
    required String uriTemplate,
    required this.mapping,
  }) : programId = _programId(programId),
       requestTemplate = SourcePackageUriTemplate(uriTemplate) {
    if (!_mappingMatchesKind()) {
      throw ArgumentError.value(
        mapping,
        'mapping',
        'The field mapping type must match the declared live operation.',
      );
    }
  }

  final SourcePackageLiveOperationKind kind;
  final String programId;
  final SourcePackageUriTemplate requestTemplate;
  final Object mapping;

  List<String> get mappingFieldNames {
    if (mapping is SourceSearchFieldMapping) {
      final value = mapping as SourceSearchFieldMapping;
      return [value.subjectIdField, value.titleField];
    }
    if (mapping is SourceEpisodeFieldMapping) {
      final value = mapping as SourceEpisodeFieldMapping;
      return [
        value.lineIdField,
        value.subjectIdField,
        value.episodeIdField,
        value.titleField,
      ];
    }
    final value = mapping as SourcePlayableSourceFieldMapping;
    return [
      value.sourceKeyField,
      value.labelField,
      value.kindField,
      value.mediaUriField,
      value.pageUriField,
    ];
  }

  Map<String, Object?> toRedactedDiagnostic() => {
    'kind': kind.name,
    'programId': programId,
    'placeholderCount': requestTemplate.placeholders.length,
    'mappingFieldCount': mappingFieldNames.length,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  bool _mappingMatchesKind() {
    return switch (kind) {
      SourcePackageLiveOperationKind.search =>
        mapping is SourceSearchFieldMapping,
      SourcePackageLiveOperationKind.episode =>
        mapping is SourceEpisodeFieldMapping,
      SourcePackageLiveOperationKind.playableSource =>
        mapping is SourcePlayableSourceFieldMapping,
    };
  }

  static String _programId(String value) {
    final normalized = value.trim();
    if (value != normalized ||
        !RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(normalized)) {
      throw ArgumentError.value(
        value,
        'programId',
        'Must be a lower-case source program identifier.',
      );
    }
    return normalized;
  }
}
