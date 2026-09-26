import 'dart:convert';

import '../../domain/models/source_security_policy.dart';
import '../../domain/models/web_capture_models.dart';
import '../../domain/services/web_capture_candidate_classifier.dart';

final class WebCaptureAccumulator {
  WebCaptureAccumulator(this.request)
    : _headerBytes = webCaptureHeaderBytes(request.initialHeaders);

  final WebCaptureRequest request;
  static const _candidateClassifier = WebCaptureCandidateClassifier();
  final List<WebCaptureEvent> _events = [];
  final Map<String, WebMediaCandidate> _candidates = {};
  var _headerBytes = 0;
  var _redirects = 0;
  var _lastSequence = -1;
  WebCaptureStopReason? _stopReason;
  final List<Uri> _redirectChain = [];

  bool get isStopped => _stopReason != null;

  /// Whether at least one bounded media candidate has been observed.
  ///
  /// The getter intentionally exposes only presence, not URI or header data,
  /// so the platform view can decide when to complete an interactive capture
  /// without widening the diagnostic surface.
  bool get hasMediaCandidate => _candidates.isNotEmpty;
  Iterable<Uri> get candidateUris =>
      _candidates.values.map((candidate) => candidate.uri);

  RuntimeMediaOriginGrant? runtimeGrantForUri(Uri uri) {
    for (final candidate in _candidates.values) {
      if (candidate.uri == uri) return candidate.runtimeOriginGrant;
    }
    return null;
  }

  /// A candidate is validated enough for bounded completion only when the
  /// WebView observed a GET for a supported playable resource. Redirectors,
  /// DASH, and raw media segments remain available to downstream ranking but
  /// must not terminate capture early.
  bool get hasValidatedPlayableCandidate => _candidates.values.any(
    (candidate) =>
        candidate.requestMethod == 'GET' &&
        candidate.kind != WebCandidateKind.dash &&
        candidate.kind != WebCandidateKind.mediaSegment,
  );

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
    final packageAllowed = request.securityPolicy.allowsUri(event.uri);
    final runtimeAllowed = _allowsRuntimeMediaEvent(event);
    if (!packageAllowed && !runtimeAllowed) {
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
      if (_redirectChain.length < request.securityPolicy.budget.maxRedirects) {
        _redirectChain.add(event.uri);
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
      final kind = _candidateClassifier.classify(event);
      if (kind != null) {
        final normalized = _candidateClassifier.normalizeCandidateUri(
          event.uri,
        );
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
            pageUri: request.initialUri,
            requestMethod: event.method,
            isRedirect: event.isRedirect,
            redirectChain: _redirectChain,
            runtimeOriginGrant: runtimeAllowed
                ? RuntimeMediaOriginGrant(
                    acquisitionId: request.acquisitionId!,
                    origin: _originOf(event.uri),
                    sourceEventSequence: event.sequence,
                    // The grant is in-memory, acquisition-bound, and is lost
                    // when the process/session ends. Keep it long enough for a
                    // normal feature-length playback while still preventing a
                    // captured origin from becoming durable package authority.
                    expiresAt: DateTime.now().toUtc().add(
                      const Duration(hours: 6),
                    ),
                  )
                : null,
          );
        }
      }
    }
    return true;
  }

  bool _allowsRuntimeMediaEvent(WebCaptureEvent event) {
    if (!request.allowRuntimeMediaOrigins ||
        request.acquisitionId == null ||
        !event.runtimeOriginValidated ||
        event.isMainFrame ||
        event.kind == WebRequestKind.navigation ||
        event.kind == WebRequestKind.iframe ||
        event.method != 'GET' ||
        event.uri.scheme != 'https') {
      return false;
    }
    return _candidateClassifier.classify(event) != null;
  }

  WebCaptureSnapshot finish({
    required Uri finalUri,
    Iterable<WebCaptureCookie> cookies = const [],
    String? documentBody,
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
    final grants = _candidates.values
        .map((candidate) => candidate.runtimeOriginGrant)
        .whereType<RuntimeMediaOriginGrant>()
        .toList(growable: false);
    for (final cookie in cookieList) {
      if (!webCapturePolicyCoversCookieDomain(
            request.securityPolicy,
            cookie.domain,
          ) &&
          !grants.any(
            (grant) => grant.coversCookieDomain(
              cookie.domain,
              acquisitionId: request.acquisitionId!,
            ),
          )) {
        throw WebCaptureSecurityException(
          'cookie_domain_not_allowed',
          'Captured cookie is outside the declared source allowlist.',
        );
      }
    }
    if (!request.captureDocument && documentBody != null) {
      throw WebCaptureSecurityException(
        'document_not_requested',
        'A document was captured without an explicit document request.',
      );
    }
    if (documentBody != null &&
        utf8.encode(documentBody).length >
            request.securityPolicy.budget.maxDocumentBytes) {
      throw WebCaptureSecurityException(
        'document_budget_exceeded',
        'Captured document exceeds the source document budget.',
      );
    }

    final candidates =
        _candidates.values
            .map((candidate) => candidate.withPageUri(finalUri))
            .toList(growable: true)
          ..sort(_candidateClassifier.compareCandidates);
    return WebCaptureSnapshot(
      events: _events,
      candidates: List<WebMediaCandidate>.unmodifiable(candidates),
      cookies: cookieList,
      stopReason: _stopReason ?? WebCaptureStopReason.completed,
      finalUri: finalUri,
      hasCompleteRequestMetadata: true,
      documentBody: documentBody,
    );
  }
}

Uri _originOf(Uri uri) => Uri(
  scheme: uri.scheme,
  host: uri.host,
  port: uri.hasPort ? uri.port : null,
);
