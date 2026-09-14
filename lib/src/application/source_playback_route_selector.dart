import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_playable_normalization_models.dart';
import 'package:wynime/src/domain/models/source_playback_route_models.dart';
import 'package:wynime/src/domain/services/source_playback_route_selector.dart';

final class DeterministicSourcePlaybackRouteSelector
    implements SourcePlaybackRouteSelector {
  const DeterministicSourcePlaybackRouteSelector();

  @override
  SourcePlaybackRouteSelectionResult selectRoute({
    required SourcePlayableSourceNormalizationResult normalized,
    required SourceEpisodeIdentity episode,
    String? preferredSourceKey,
  }) {
    final status = _statusFor(normalized.status);
    if (status != null) {
      return SourcePlaybackRouteSelectionResult(status: status);
    }
    if (normalized.results.isEmpty) {
      return SourcePlaybackRouteSelectionResult(
        status: SourcePlaybackRouteSelectionStatus.notFound,
      );
    }
    if (!_hasSafeIdentity(normalized.packageId, normalized.programId)) {
      return SourcePlaybackRouteSelectionResult(
        status: SourcePlaybackRouteSelectionStatus.failed,
        reasonCode: 'invalid_normalized_identity',
      );
    }
    for (final source in normalized.results) {
      if (source.episode != episode ||
          source.episode.sourceId != normalized.packageId) {
        return SourcePlaybackRouteSelectionResult(
          status: SourcePlaybackRouteSelectionStatus.failed,
          reasonCode: 'episode_identity_mismatch',
        );
      }
      if (!_isSupportedKind(source)) {
        return SourcePlaybackRouteSelectionResult(
          status: SourcePlaybackRouteSelectionStatus.failed,
          reasonCode: 'unsupported_candidate_kind',
        );
      }
    }

    final requestedKey = preferredSourceKey?.trim();
    if (requestedKey != null && !_isSafeSourceKey(requestedKey)) {
      return SourcePlaybackRouteSelectionResult(
        status: SourcePlaybackRouteSelectionStatus.failed,
        reasonCode: 'invalid_preferred_source_key',
      );
    }
    final selected = requestedKey == null
        ? normalized.results.first
        : normalized.results
              .where((source) => source.sourceKey == requestedKey)
              .firstOrNull;
    if (selected == null) {
      return SourcePlaybackRouteSelectionResult(
        status: SourcePlaybackRouteSelectionStatus.preferredSourceNotFound,
        reasonCode: 'preferred_source_not_found',
      );
    }

    return SourcePlaybackRouteSelectionResult(
      status: SourcePlaybackRouteSelectionStatus.selected,
      route: SourcePlaybackRoute(
        packageId: normalized.packageId,
        packageVersion: normalized.packageVersion,
        programId: normalized.programId,
        source: selected,
      ),
    );
  }

  static SourcePlaybackRouteSelectionStatus? _statusFor(
    SourcePlayableSourceNormalizationStatus status,
  ) {
    return switch (status) {
      SourcePlayableSourceNormalizationStatus.available => null,
      SourcePlayableSourceNormalizationStatus.notFound =>
        SourcePlaybackRouteSelectionStatus.notFound,
      SourcePlayableSourceNormalizationStatus.disabled =>
        SourcePlaybackRouteSelectionStatus.disabled,
      SourcePlayableSourceNormalizationStatus.consentRequired =>
        SourcePlaybackRouteSelectionStatus.consentRequired,
      SourcePlayableSourceNormalizationStatus.incompatible =>
        SourcePlaybackRouteSelectionStatus.incompatible,
      SourcePlayableSourceNormalizationStatus.failed =>
        SourcePlaybackRouteSelectionStatus.failed,
    };
  }

  static bool _hasSafeIdentity(String packageId, String programId) {
    return RegExp(r'^[a-z0-9]+(?:[._-][a-z0-9]+)*$').hasMatch(packageId) &&
        packageId.length <= 128 &&
        RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(programId);
  }

  static bool _isSupportedKind(SourcePlayableSource source) {
    return switch (source.kind.name) {
      'hls' || 'video' || 'audio' => true,
      _ => false,
    };
  }

  static bool _isSafeSourceKey(String value) {
    return value.isNotEmpty &&
        value.length <= 128 &&
        !value.codeUnits.any(
          (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
        );
  }
}
