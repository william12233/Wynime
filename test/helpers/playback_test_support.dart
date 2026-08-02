import 'package:wynime/src/domain/models/ad_removal_plan.dart';
import 'package:wynime/src/domain/models/manifest_fingerprint.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';

SourceEpisodeIdentity testEpisode({String episodeId = 'episode-1'}) =>
    SourceEpisodeIdentity(
      sourceId: 'source',
      lineId: 'line',
      subjectId: 'subject',
      episodeId: episodeId,
    );

AdRemovalPlan testAdRemovalPlan(SourceEpisodeIdentity episode) => AdRemovalPlan(
  key: AdRemovalPlanKey(
    episode: episode,
    manifestFingerprint: ManifestFingerprint(
      algorithm: 'sha256',
      value: 'phase4-placeholder-fingerprint',
    ),
  ),
  mode: AdRemovalMode.off,
);

SourceSecurityPolicy testSourcePolicy({
  Iterable<SourceDomainRule>? domains,
  Set<SourcePermission> permissions = const {
    SourcePermission.network,
    SourcePermission.cookies,
  },
}) => SourceSecurityPolicy(
  allowedDomains:
      domains ??
      [SourceDomainRule(host: 'media.example', includeSubdomains: true)],
  permissions: permissions,
  budget: SourceResourceBudget(
    maxDocumentBytes: 1024 * 1024,
    maxRecords: 100,
    maxSelectorMatches: 1000,
    maxEvaluationSteps: 10000,
    maxRegexPatternChars: 128,
    maxRegexInputChars: 2048,
    maxRedirects: 3,
  ),
);

PlaybackSession testPlaybackSession({
  String sessionId = 'session-1',
  SourceEpisodeIdentity? episode,
  Uri? mediaUri,
  Uri? pageUri,
  Uri? playbackUri,
  Map<String, String> headers = const {},
  Map<String, String> cookies = const {},
  Uri? referer,
  Uri? origin,
  String? userAgent,
  DateTime? expiresAt,
  PlaybackSessionRefresher? refresh,
}) {
  final resolvedEpisode = episode ?? testEpisode();
  return PlaybackSession(
    sessionId: sessionId,
    episode: resolvedEpisode,
    mediaUri: mediaUri ?? Uri.parse('https://media.example/video/master.m3u8'),
    pageUri: pageUri ?? Uri.parse('https://media.example/episode/1'),
    playbackUri: playbackUri,
    headers: headers,
    cookies: cookies,
    referer: referer,
    origin: origin,
    userAgent: userAgent,
    expiresAt: expiresAt,
    adRemovalPlan: testAdRemovalPlan(resolvedEpisode),
    refresh: refresh,
  );
}

PlaybackProxyBudget testProxyBudget({
  int maxPlaylistBytes = 64 * 1024,
  int maxResponseBytes = 1024 * 1024,
  int maxRequestHeaderBytes = 64 * 1024,
  int maxCookieBytes = 64 * 1024,
  int maxRedirects = 3,
  int maxRegisteredResources = 100,
  Duration upstreamTimeout = const Duration(seconds: 5),
}) => PlaybackProxyBudget(
  maxPlaylistBytes: maxPlaylistBytes,
  maxResponseBytes: maxResponseBytes,
  maxRequestHeaderBytes: maxRequestHeaderBytes,
  maxCookieBytes: maxCookieBytes,
  maxRedirects: maxRedirects,
  maxRegisteredResources: maxRegisteredResources,
  upstreamTimeout: upstreamTimeout,
);
