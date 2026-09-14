import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

import 'source_identity.dart';
import 'source_live_capture_models.dart';
import 'source_package_manager_models.dart';
import 'source_playable_normalization_models.dart';
import 'web_capture_models.dart';

enum SourceLiveCapturePlayableSourceStatus {
  available,
  notFound,
  consentRequired,
  disabled,
  incompatible,
  failed,
}

/// Explicit metadata supplied by the caller for one captured media
/// candidate. Source keys and labels are never inferred from a URL or event.
final class SourceLiveCapturePlayableSourceMapping {
  SourceLiveCapturePlayableSourceMapping({
    required this.candidateIndex,
    required String sourceKey,
    required String label,
  }) : sourceKey = _boundedText(sourceKey, 'sourceKey', 128),
       label = _boundedText(label, 'label', 256) {
    if (candidateIndex < 0) {
      throw ArgumentError.value(
        candidateIndex,
        'candidateIndex',
        'Must not be negative.',
      );
    }
  }

  final int candidateIndex;
  final String sourceKey;
  final String label;
}

/// The exact request/result pair handed off by the package-aware capture
/// surface, plus explicit episode and candidate metadata.
final class SourceLiveCapturePlayableSourcePlan {
  SourceLiveCapturePlayableSourcePlan({
    required this.installedPackage,
    required String programId,
    required this.episode,
    required this.captureRequest,
    required this.captureResult,
    required Iterable<SourceLiveCapturePlayableSourceMapping> mappings,
  }) : programId = _programId(programId),
       mappings = UnmodifiableListView(
         _boundedList(mappings, 'mappings', 1000),
       );

  final InstalledSourcePackage installedPackage;
  final String programId;
  final SourceEpisodeIdentity episode;
  final SourceLiveCaptureRequest captureRequest;
  final SourceLiveCaptureResult captureResult;
  final UnmodifiableListView<SourceLiveCapturePlayableSourceMapping> mappings;

  String get identityKey =>
      '${installedPackage.package.packageId}@${installedPackage.package.version}/'
      '$programId/${_identityPart(episode.sourceId)}/'
      '${_identityPart(episode.lineId)}/${_identityPart(episode.subjectId)}/'
      '${_identityPart(episode.episodeId)}';

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

  static String _identityPart(String value) => '${value.length}:$value';

  static List<T> _boundedList<T>(Iterable<T> values, String name, int maximum) {
    final result = <T>[];
    final iterator = values.iterator;
    while (iterator.moveNext()) {
      if (result.length == maximum) {
        throw ArgumentError.value(
          values,
          name,
          'Must contain at most $maximum items.',
        );
      }
      result.add(iterator.current);
    }
    return List<T>.unmodifiable(result);
  }
}

/// One normalized source candidate with its exact captured media authority.
/// The candidate headers and sequence remain in [candidate]; cookies remain
/// once at the result-level capture snapshot to avoid copying sensitive data
/// into every source row.
final class SourceLiveCapturePlayableSource {
  SourceLiveCapturePlayableSource({
    required this.candidateIndex,
    required this.source,
    required this.candidate,
  }) {
    if (candidateIndex < 0) {
      throw ArgumentError.value(
        candidateIndex,
        'candidateIndex',
        'Must not be negative.',
      );
    }
    if (source.kind != candidate.kind || source.mediaUri != candidate.uri) {
      throw ArgumentError(
        'Normalized source must retain the captured candidate identity.',
      );
    }
  }

  final int candidateIndex;
  final SourcePlayableSource source;
  final WebMediaCandidate candidate;

  Map<String, Object?> toRedactedDiagnostic() => {
    'candidateIndex': candidateIndex,
    'sourceKey': source.sourceKey,
    'kind': source.kind.name,
    'mediaScheme': candidate.uri.scheme,
    'mediaHost': candidate.uri.host,
    'sourceEventSequence': candidate.sourceEventSequence,
    'headerNames': candidate.headers.keys.toList(growable: false),
  };

  @override
  String toString() => toRedactedDiagnostic().toString();
}

/// Immutable result of converting one accepted capture into playable-source
/// metadata without invoking routing, session resolution or a player.
/// The available result retains the exact request/result pair so downstream
/// live handoffs cannot silently replace the capture policy or initial URI.
final class SourceLiveCapturePlayableSourceResult {
  SourceLiveCapturePlayableSourceResult({
    required this.packageId,
    required this.packageVersion,
    required this.programId,
    required this.status,
    required Iterable<SourceLiveCapturePlayableSource> sources,
    required Iterable<SourcePlayableSourceNormalizationDiagnostic> diagnostics,
    this.captureRequest,
    this.captureResult,
    this.reasonCode,
  }) : sources = UnmodifiableListView(_boundedList(sources, 'sources', 1000)),
       diagnostics = UnmodifiableListView(
         _boundedList(diagnostics, 'diagnostics', 1000),
       ) {
    if (packageId.isEmpty ||
        packageId.length > 128 ||
        !RegExp(r'^[a-z0-9]+(?:[._-][a-z0-9]+)*$').hasMatch(packageId)) {
      throw ArgumentError.value(
        packageId,
        'packageId',
        'Must be a lower-case package identifier.',
      );
    }
    if (programId.isEmpty ||
        programId.length > 64 ||
        !RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(programId)) {
      throw ArgumentError.value(
        programId,
        'programId',
        'Must be a lower-case program identifier.',
      );
    }
    final isAvailable =
        status == SourceLiveCapturePlayableSourceStatus.available;
    if (isAvailable) {
      if (this.sources.isEmpty ||
          captureRequest == null ||
          captureResult?.status != SourceLiveCaptureStatus.captured ||
          captureResult?.snapshot == null ||
          reasonCode != null) {
        throw ArgumentError(
          'An available result requires sources, an accepted request/capture and no reason.',
        );
      }
      if (captureRequest!.packageId != packageId ||
          captureRequest!.packageVersion != packageVersion ||
          captureRequest!.programId != programId ||
          captureResult!.packageId != packageId ||
          captureResult!.packageVersion != packageVersion ||
          captureResult!.programId != programId) {
        throw ArgumentError(
          'An available result must retain one matching capture request/result pair.',
        );
      }
    } else if (this.sources.isNotEmpty ||
        captureRequest != null ||
        captureResult != null) {
      throw ArgumentError(
        'A non-available result must not retain playable sources or capture data.',
      );
    }
    if (reasonCode != null && !_safeToken(reasonCode!)) {
      throw ArgumentError.value(
        reasonCode,
        'reasonCode',
        'Must be a bounded diagnostic token.',
      );
    }
    if (status == SourceLiveCapturePlayableSourceStatus.failed &&
        reasonCode == null) {
      throw ArgumentError('A failed result requires a diagnostic code.');
    }
    if (status != SourceLiveCapturePlayableSourceStatus.available &&
        status != SourceLiveCapturePlayableSourceStatus.failed &&
        reasonCode == null) {
      throw ArgumentError('A blocked or not-found result requires a code.');
    }
  }

  final String packageId;
  final Version packageVersion;
  final String programId;
  final SourceLiveCapturePlayableSourceStatus status;
  final UnmodifiableListView<SourceLiveCapturePlayableSource> sources;
  final UnmodifiableListView<SourcePlayableSourceNormalizationDiagnostic>
  diagnostics;
  final SourceLiveCaptureRequest? captureRequest;
  final SourceLiveCaptureResult? captureResult;
  final String? reasonCode;

  UnmodifiableListView<SourceLiveCapturePlayableSource> get playableSources =>
      sources;

  Map<String, Object?> toRedactedDiagnostic() => {
    'packageId': packageId,
    'packageVersion': packageVersion.toString(),
    'programId': programId,
    'status': status.name,
    'sourceCount': sources.length,
    'candidateCount': captureResult?.snapshot?.candidates.length,
    'eventCount': captureResult?.snapshot?.events.length,
    'cookieCount': captureResult?.snapshot?.cookies.length,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static List<T> _boundedList<T>(Iterable<T> values, String name, int maximum) {
    final result = <T>[];
    final iterator = values.iterator;
    while (iterator.moveNext()) {
      if (result.length == maximum) {
        throw ArgumentError.value(
          values,
          name,
          'Must contain at most $maximum items.',
        );
      }
      result.add(iterator.current);
    }
    return List<T>.unmodifiable(result);
  }

  static bool _safeToken(String value) =>
      RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value);
}

String _boundedText(String value, String name, int maximum) {
  final normalized = value.trim();
  if (normalized.isEmpty ||
      normalized.length > maximum ||
      normalized.codeUnits.any(
        (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
      )) {
    throw ArgumentError.value(
      value,
      name,
      'Must contain bounded text without control characters.',
    );
  }
  return normalized;
}
