import '../../domain/models/source_security_policy.dart';
import '../../domain/models/web_capture_models.dart';

final class WebCaptureAccumulator {
  WebCaptureAccumulator(this.request)
    : _headerBytes = webCaptureHeaderBytes(request.initialHeaders);

  final WebCaptureRequest request;
  final List<WebCaptureEvent> _events = [];
  final Map<String, WebMediaCandidate> _candidates = {};
  var _headerBytes = 0;
  var _redirects = 0;
  var _lastSequence = -1;
  WebCaptureStopReason? _stopReason;

  bool get isStopped => _stopReason != null;

  bool add(WebCaptureEvent event) {
    if (isStopped) {
      return false;
    }
    if (event.sequence <= _lastSequence) {
      throw WebCaptureSecurityException(
        'event_sequence_invalid',
        'Captured event sequence must increase strictly.',
      );
    }
    if (!request.securityPolicy.allowsUri(event.uri)) {
      throw WebCaptureSecurityException(
        'uri_not_allowed',
        'Captured request is outside the declared source allowlist.',
      );
    }
    if (_events.length >= request.budget.maxEvents) {
      _stopReason = WebCaptureStopReason.eventBudgetExceeded;
      return false;
    }
    if (event.isRedirect) {
      _redirects += 1;
      if (_redirects > request.securityPolicy.budget.maxRedirects) {
        _stopReason = WebCaptureStopReason.redirectBudgetExceeded;
        return false;
      }
    }
    final eventHeaderBytes = webCaptureHeaderBytes(event.headers);
    if (_headerBytes + eventHeaderBytes > request.budget.maxHeaderBytes) {
      _stopReason = WebCaptureStopReason.headerBudgetExceeded;
      return false;
    }

    _lastSequence = event.sequence;
    _headerBytes += eventHeaderBytes;
    _events.add(event);

    if (request.captureMediaRequests) {
      final kind = _classifyCandidate(event);
      if (kind != null) {
        final normalized = event.uri.replace(fragment: '');
        final key = '${kind.name}:${normalized.toString()}';
        if (!_candidates.containsKey(key)) {
          if (_candidates.length >= request.budget.maxCandidates) {
            _stopReason = WebCaptureStopReason.candidateBudgetExceeded;
            return false;
          }
          _candidates[key] = WebMediaCandidate(
            kind: kind,
            uri: normalized,
            headers: event.headers,
            sourceEventSequence: event.sequence,
          );
        }
      }
    }
    return true;
  }

  WebCaptureSnapshot finish({
    required Uri finalUri,
    Iterable<WebCaptureCookie> cookies = const [],
  }) {
    if (!request.securityPolicy.allowsUri(finalUri)) {
      throw WebCaptureSecurityException(
        'final_uri_not_allowed',
        'Final WebView URI is outside the declared source allowlist.',
      );
    }
    final cookieList = List<WebCaptureCookie>.unmodifiable(cookies);
    if (cookieList.isNotEmpty &&
        !request.securityPolicy.permissions.contains(
          SourcePermission.cookies,
        )) {
      throw WebCaptureSecurityException(
        'cookie_permission_missing',
        'Cookie export requires the cookies permission.',
      );
    }
    if (webCaptureCookieBytes(cookieList) > request.budget.maxCookieBytes) {
      throw WebCaptureSecurityException(
        'cookie_budget_exceeded',
        'Captured cookies exceed maxCookieBytes.',
      );
    }
    for (final cookie in cookieList) {
      if (!webCapturePolicyCoversCookieDomain(
        request.securityPolicy,
        cookie.domain,
      )) {
        throw WebCaptureSecurityException(
          'cookie_domain_not_allowed',
          'Captured cookie is outside the declared source allowlist.',
        );
      }
    }

    return WebCaptureSnapshot(
      events: _events,
      candidates: _candidates.values,
      cookies: cookieList,
      stopReason: _stopReason ?? WebCaptureStopReason.completed,
      finalUri: finalUri,
    );
  }

  WebCandidateKind? _classifyCandidate(WebCaptureEvent event) {
    final contentType = _headerValue(
      event.headers,
      'content-type',
    )?.split(';').first.trim().toLowerCase();
    if (contentType == 'application/vnd.apple.mpegurl' ||
        contentType == 'application/x-mpegurl' ||
        contentType == 'audio/mpegurl') {
      return WebCandidateKind.hls;
    }
    if (contentType == 'application/dash+xml') {
      return WebCandidateKind.dash;
    }
    if (contentType?.startsWith('video/') ?? false) {
      return WebCandidateKind.video;
    }
    if (contentType?.startsWith('audio/') ?? false) {
      return WebCandidateKind.audio;
    }

    final path = event.uri.path.toLowerCase();
    if (path.endsWith('.m3u8')) {
      return WebCandidateKind.hls;
    }
    if (path.endsWith('.mpd')) {
      return WebCandidateKind.dash;
    }
    if (_endsWithAny(path, const ['.mp4', '.mkv', '.webm', '.mov'])) {
      return WebCandidateKind.video;
    }
    if (_endsWithAny(path, const ['.m4a', '.aac', '.mp3', '.flac', '.opus'])) {
      return WebCandidateKind.audio;
    }
    if (_endsWithAny(path, const ['.ts', '.m4s', '.cmfv', '.cmfa'])) {
      return WebCandidateKind.mediaSegment;
    }
    return null;
  }

  static String? _headerValue(Map<String, String> headers, String name) {
    for (final entry in headers.entries) {
      if (entry.key.toLowerCase() == name) {
        return entry.value;
      }
    }
    return null;
  }

  static bool _endsWithAny(String value, Iterable<String> suffixes) =>
      suffixes.any(value.endsWith);
}
