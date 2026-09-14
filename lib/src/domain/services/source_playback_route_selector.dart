import '../models/source_identity.dart';
import '../models/source_playable_normalization_models.dart';
import '../models/source_playback_route_models.dart';

abstract interface class SourcePlaybackRouteSelector {
  SourcePlaybackRouteSelectionResult selectRoute({
    required SourcePlayableSourceNormalizationResult normalized,
    required SourceEpisodeIdentity episode,
    String? preferredSourceKey,
  });
}
