import 'dart:collection';

import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/source_identity.dart';

typedef PlaybackSessionRefresher = Future<PlaybackSession> Function();

final class MediaTrack {
  MediaTrack({required this.id, required this.label, this.languageCode})
    : assert(id.trim().isNotEmpty, 'id must not be empty.'),
      assert(label.trim().isNotEmpty, 'label must not be empty.');

  final String id;
  final String label;
  final String? languageCode;
}

final class PlaybackSession {
  PlaybackSession({
    required this.sessionId,
    required this.episode,
    required this.mediaUri,
    required this.pageUri,
    required this.adRemovalPlan,
    Map<String, String> headers = const {},
    Map<String, String> cookies = const {},
    this.referer,
    this.origin,
    this.userAgent,
    this.expiresAt,
    Iterable<MediaTrack> subtitles = const [],
    Iterable<MediaTrack> audioTracks = const [],
    this.refresh,
  }) : assert(sessionId.trim().isNotEmpty, 'sessionId must not be empty.'),
       assert(
         adRemovalPlan.key.episode == episode,
         'AdRemovalPlan must describe the same source, line, subject, and episode.',
       ),
       headers = UnmodifiableMapView(headers),
       cookies = UnmodifiableMapView(cookies),
       subtitles = UnmodifiableListView(subtitles),
       audioTracks = UnmodifiableListView(audioTracks);

  final String sessionId;
  final SourceEpisodeIdentity episode;
  final Uri mediaUri;
  final Uri pageUri;
  final UnmodifiableMapView<String, String> headers;
  final UnmodifiableMapView<String, String> cookies;
  final Uri? referer;
  final Uri? origin;
  final String? userAgent;
  final DateTime? expiresAt;
  final UnmodifiableListView<MediaTrack> subtitles;
  final UnmodifiableListView<MediaTrack> audioTracks;
  final AdRemovalPlan adRemovalPlan;
  final PlaybackSessionRefresher? refresh;

  bool get isExpired =>
      expiresAt != null && !expiresAt!.isAfter(DateTime.now().toUtc());
}
