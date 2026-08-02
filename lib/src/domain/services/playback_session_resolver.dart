import 'dart:collection';

import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';

final class PlaybackSessionResolutionRequest {
  PlaybackSessionResolutionRequest({
    required this.episode,
    required this.pageUri,
    required this.candidate,
    required this.securityPolicy,
    required this.adRemovalPlan,
    Iterable<WebCaptureCookie> cookies = const [],
    this.userAgent,
    this.expiresAt,
    this.refresh,
    Iterable<MediaTrack> subtitles = const [],
    Iterable<MediaTrack> audioTracks = const [],
  }) : cookies = UnmodifiableListView(
         List<WebCaptureCookie>.unmodifiable(cookies),
       ),
       subtitles = UnmodifiableListView(
         List<MediaTrack>.unmodifiable(subtitles),
       ),
       audioTracks = UnmodifiableListView(
         List<MediaTrack>.unmodifiable(audioTracks),
       ) {
    if (adRemovalPlan.key.episode != episode) {
      throw ArgumentError('AdRemovalPlan and request episode must match.');
    }
    if (!securityPolicy.allowsUri(pageUri)) {
      throw ArgumentError.value(
        pageUri,
        'pageUri',
        'Page URI is outside the source allowlist.',
      );
    }
  }

  final SourceEpisodeIdentity episode;
  final Uri pageUri;
  final WebMediaCandidate candidate;
  final SourceSecurityPolicy securityPolicy;
  final AdRemovalPlan adRemovalPlan;
  final UnmodifiableListView<WebCaptureCookie> cookies;
  final String? userAgent;
  final DateTime? expiresAt;
  final PlaybackSessionRefresher? refresh;
  final UnmodifiableListView<MediaTrack> subtitles;
  final UnmodifiableListView<MediaTrack> audioTracks;
}

abstract interface class PlaybackSessionResolver {
  Future<PlaybackSession> resolve(PlaybackSessionResolutionRequest request);
}
