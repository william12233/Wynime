import 'dart:convert';
import 'dart:math';

import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';

abstract interface class PlaybackSessionIdGenerator {
  String nextId();
}

final class SecurePlaybackSessionIdGenerator
    implements PlaybackSessionIdGenerator {
  SecurePlaybackSessionIdGenerator({Random? random})
    : _random = random ?? Random.secure();

  final Random _random;

  @override
  String nextId() {
    final bytes = List<int>.generate(24, (_) => _random.nextInt(256));
    return base64Url.encode(bytes).replaceAll('=', '');
  }
}

final class DefaultPlaybackSessionResolver implements PlaybackSessionResolver {
  DefaultPlaybackSessionResolver({PlaybackSessionIdGenerator? idGenerator})
    : _idGenerator = idGenerator ?? SecurePlaybackSessionIdGenerator();

  final PlaybackSessionIdGenerator _idGenerator;

  @override
  Future<PlaybackSession> resolve(
    PlaybackSessionResolutionRequest request,
  ) async {
    final candidate = request.candidate;
    if (candidate.kind == WebCandidateKind.mediaSegment) {
      throw PlaybackSessionResolutionException(
        'segment_candidate_rejected',
        'A media segment cannot become an authoritative PlaybackSession.',
      );
    }
    if (candidate.kind == WebCandidateKind.dash) {
      throw PlaybackSessionResolutionException(
        'dash_deferred',
        'DASH playback is outside the Phase 4 HLS and direct-media MVP.',
      );
    }
    if (!request.securityPolicy.allowsUri(candidate.uri)) {
      throw PlaybackSessionResolutionException(
        'candidate_outside_allowlist',
        'The selected media candidate is outside the source allowlist.',
      );
    }
    if (request.cookies.isNotEmpty &&
        !request.securityPolicy.permissions.contains(
          SourcePermission.cookies,
        )) {
      throw PlaybackSessionResolutionException(
        'cookies_permission_missing',
        'Captured cookies require the cookies permission.',
      );
    }

    final headers = <String, String>{...candidate.headers};
    final candidateUserAgent = _nonEmpty(headers.remove('user-agent'));
    headers.remove('cookie');
    final referer = _safeReferer(headers.remove('referer'), request.pageUri);
    final origin = _safeOrigin(headers.remove('origin'), request.pageUri);
    final requestedUserAgent = _nonEmpty(request.userAgent);
    final userAgent = requestedUserAgent ?? candidateUserAgent;

    final cookies = _cookiesForUri(
      request.cookies,
      candidate.uri,
      request.securityPolicy,
    );

    return PlaybackSession(
      sessionId: _idGenerator.nextId(),
      episode: request.episode,
      mediaUri: candidate.uri,
      pageUri: request.pageUri,
      headers: headers,
      cookies: cookies,
      referer: referer,
      origin: origin,
      userAgent: userAgent,
      expiresAt: request.expiresAt,
      subtitles: request.subtitles,
      audioTracks: request.audioTracks,
      adRemovalPlan: request.adRemovalPlan,
      refresh: request.refresh,
    );
  }
}

final class PlaybackSessionResolutionException implements Exception {
  PlaybackSessionResolutionException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'PlaybackSessionResolutionException($code): $message';
}

Map<String, String> _cookiesForUri(
  Iterable<WebCaptureCookie> cookies,
  Uri uri,
  SourceSecurityPolicy policy,
) {
  final result = <String, String>{};
  final now = DateTime.now().toUtc();
  for (final cookie in cookies) {
    if (cookie.expiresAt != null && !cookie.expiresAt!.toUtc().isAfter(now)) {
      continue;
    }
    if (cookie.isSecure && uri.scheme != 'https') {
      continue;
    }
    if (!_domainMatches(uri.host, cookie.domain) ||
        !_pathMatches(uri.path, cookie.path) ||
        !webCapturePolicyCoversCookieDomain(policy, cookie.domain)) {
      continue;
    }
    result[cookie.name] = cookie.value;
  }
  return result;
}

bool _domainMatches(String host, String cookieDomain) {
  final normalizedHost = host.toLowerCase();
  final normalizedDomain = cookieDomain.toLowerCase();
  return normalizedHost == normalizedDomain ||
      normalizedHost.endsWith('.$normalizedDomain');
}

bool _pathMatches(String requestPath, String cookiePath) {
  final request = requestPath.isEmpty ? '/' : requestPath;
  if (request == cookiePath) {
    return true;
  }
  if (!request.startsWith(cookiePath)) {
    return false;
  }
  return cookiePath.endsWith('/') ||
      (request.length > cookiePath.length &&
          request.codeUnitAt(cookiePath.length) == 0x2f);
}

Uri _safeReferer(String? raw, Uri fallback) {
  if (raw == null || raw.trim().isEmpty) {
    return fallback;
  }
  final parsed = Uri.tryParse(raw.trim());
  if (parsed == null ||
      (parsed.scheme != 'https' && parsed.scheme != 'http') ||
      parsed.host.isEmpty ||
      parsed.userInfo.isNotEmpty) {
    throw PlaybackSessionResolutionException(
      'invalid_referer',
      'Captured Referer is not a safe HTTP(S) URI.',
    );
  }
  return parsed;
}

Uri _safeOrigin(String? raw, Uri fallback) {
  final parsed = raw == null || raw.trim().isEmpty
      ? _originOf(fallback)
      : Uri.tryParse(raw.trim());
  if (parsed == null ||
      (parsed.scheme != 'https' && parsed.scheme != 'http') ||
      parsed.host.isEmpty ||
      parsed.userInfo.isNotEmpty ||
      (parsed.path.isNotEmpty && parsed.path != '/') ||
      parsed.hasQuery ||
      parsed.hasFragment) {
    throw PlaybackSessionResolutionException(
      'invalid_origin',
      'Captured Origin is not a valid HTTP(S) origin.',
    );
  }
  return _originOf(parsed);
}

Uri _originOf(Uri uri) => Uri(
  scheme: uri.scheme,
  host: uri.host,
  port: uri.hasPort ? uri.port : null,
);

String? _nonEmpty(String? value) {
  final normalized = value?.trim();
  return normalized == null || normalized.isEmpty ? null : normalized;
}
