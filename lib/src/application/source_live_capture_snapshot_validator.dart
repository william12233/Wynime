import 'dart:convert';

import '../domain/models/source_live_capture_models.dart';
import '../domain/models/source_security_policy.dart';
import '../domain/models/web_capture_models.dart';
import '../domain/services/web_capture_candidate_classifier.dart';

/// Revalidates a WebView snapshot at every consumer boundary.
///
/// A [SourceLiveCaptureResult] is an in-memory value and therefore is not an
/// unforgeable capability. Consumers that turn it into another authority must
/// bind it to the exact request and run this validator again. The validator
/// retains no snapshot on any rejected outcome.
final class SourceLiveCaptureSnapshotValidator {
  const SourceLiveCaptureSnapshotValidator();

  static const _candidateClassifier = WebCaptureCandidateClassifier();

  SourceLiveCaptureResult validate(
    SourceLiveCaptureRequest request,
    WebCaptureSnapshot snapshot,
  ) {
    final webRequest = request.webCaptureRequest;
    final policy = webRequest.securityPolicy;

    if (snapshot.stopReason != WebCaptureStopReason.completed) {
      return _budgetResult(request, _budgetReason(snapshot.stopReason));
    }
    if (webRequest.captureDocument && snapshot.documentBody == null) {
      return _invalidResult(request, 'document_missing');
    }
    if (!webRequest.captureDocument && snapshot.documentBody != null) {
      return _invalidResult(request, 'document_not_requested');
    }
    if (snapshot.documentBody != null &&
        utf8.encode(snapshot.documentBody!).length >
            policy.budget.maxDocumentBytes) {
      return _budgetResult(request, 'capture_document_budget_exceeded');
    }
    if (!policy.allowsUri(snapshot.finalUri)) {
      return _invalidResult(request, 'final_uri_not_allowed');
    }
    if (snapshot.events.length > webRequest.budget.maxEvents) {
      return _budgetResult(request, 'capture_event_budget_exceeded');
    }
    if (snapshot.candidates.length > webRequest.budget.maxCandidates) {
      return _budgetResult(request, 'capture_candidate_budget_exceeded');
    }

    var headerBytes = webCaptureHeaderBytes(webRequest.initialHeaders);
    var candidateHeaderBytes = 0;
    var previousSequence = -1;
    var redirects = 0;
    final eventSequences = <int>{};
    final redirectChainBySequence = <int, List<Uri>>{};
    final redirectChain = <Uri>[];
    for (final event in snapshot.events) {
      if (event.sequence <= previousSequence ||
          !eventSequences.add(event.sequence)) {
        return _invalidResult(request, 'event_sequence_invalid');
      }
      previousSequence = event.sequence;
      if (!policy.allowsUri(event.uri)) {
        return _invalidResult(request, 'event_uri_not_allowed');
      }
      if (event.isRedirect) {
        redirects += 1;
        if (redirects > policy.budget.maxRedirects) {
          return _budgetResult(request, 'capture_redirect_budget_exceeded');
        }
        if (redirectChain.length < policy.budget.maxRedirects) {
          redirectChain.add(event.uri);
        }
      }
      redirectChainBySequence[event.sequence] = List<Uri>.unmodifiable(
        redirectChain,
      );
      headerBytes += webCaptureHeaderBytes(event.headers);
      if (headerBytes > webRequest.budget.maxHeaderBytes) {
        return _budgetResult(request, 'capture_header_budget_exceeded');
      }
    }

    if (!webRequest.captureMediaRequests && snapshot.candidates.isNotEmpty) {
      return _invalidResult(request, 'media_candidates_not_requested');
    }
    final eventsBySequence = {
      for (final event in snapshot.events) event.sequence: event,
    };
    final expectedCandidatesByKey = <String, WebMediaCandidate>{};
    if (webRequest.captureMediaRequests) {
      for (final event in snapshot.events) {
        final kind = _candidateClassifier.classify(event);
        if (kind == null) {
          continue;
        }
        final normalizedUri = _candidateClassifier.normalizeCandidateUri(
          event.uri,
        );
        expectedCandidatesByKey.putIfAbsent(
          _candidateKey(kind, normalizedUri),
          () => WebMediaCandidate(
            kind: kind,
            uri: normalizedUri,
            headers: event.headers,
            sourceEventSequence: event.sequence,
            pageUri: snapshot.hasCompleteRequestMetadata
                ? snapshot.finalUri
                : null,
            requestMethod: event.method,
            isRedirect: event.isRedirect,
            redirectChain: snapshot.hasCompleteRequestMetadata
                ? (redirectChainBySequence[event.sequence] ?? const [])
                : const [],
          ),
        );
      }
    }
    if (expectedCandidatesByKey.length > webRequest.budget.maxCandidates) {
      return _budgetResult(request, 'capture_candidate_budget_exceeded');
    }
    final expectedCandidates = expectedCandidatesByKey.values.toList(
      growable: false,
    );
    for (
      var candidateIndex = 0;
      candidateIndex < snapshot.candidates.length;
      candidateIndex++
    ) {
      final candidate = snapshot.candidates[candidateIndex];
      if (!policy.allowsUri(candidate.uri)) {
        return _invalidResult(request, 'candidate_uri_not_allowed');
      }
      final sourceEvent = eventsBySequence[candidate.sourceEventSequence];
      if (sourceEvent == null) {
        return _invalidResult(request, 'candidate_event_sequence_invalid');
      }
      if (candidate.uri !=
              _candidateClassifier.normalizeCandidateUri(sourceEvent.uri) ||
          !_sameHeaders(candidate.headers, sourceEvent.headers)) {
        return _invalidResult(request, 'candidate_provenance_invalid');
      }
      candidateHeaderBytes += webCaptureHeaderBytes(candidate.headers);
      if (candidateHeaderBytes > webRequest.budget.maxHeaderBytes) {
        return _budgetResult(request, 'capture_header_budget_exceeded');
      }
      final expectedKind = _candidateClassifier.classify(sourceEvent);
      if (expectedKind != candidate.kind) {
        return _invalidResult(request, 'candidate_kind_invalid');
      }
      final classifiedKind = expectedKind;
      if (classifiedKind == null) {
        return _invalidResult(request, 'candidate_kind_invalid');
      }
      final expectedCandidate =
          expectedCandidatesByKey[_candidateKey(
            classifiedKind,
            _candidateClassifier.normalizeCandidateUri(sourceEvent.uri),
          )];
      if (expectedCandidate == null ||
          expectedCandidate.sourceEventSequence != sourceEvent.sequence ||
          expectedCandidate.sourceEventSequence !=
              candidate.sourceEventSequence) {
        return _invalidResult(request, 'candidate_provenance_invalid');
      }
      if (snapshot.hasCompleteRequestMetadata &&
          !_sameCandidateRequestMetadata(
            candidate,
            sourceEvent,
            snapshot.finalUri,
            redirectChainBySequence[sourceEvent.sequence] ?? const [],
          )) {
        return _invalidResult(request, 'candidate_provenance_invalid');
      }
    }
    if (snapshot.candidates.length != expectedCandidates.length) {
      return _invalidResult(request, 'candidate_provenance_invalid');
    }
    for (
      var candidateIndex = 0;
      candidateIndex < snapshot.candidates.length;
      candidateIndex++
    ) {
      if (!_sameCandidate(
        snapshot.candidates[candidateIndex],
        expectedCandidates[candidateIndex],
      )) {
        return _invalidResult(request, 'candidate_provenance_invalid');
      }
    }

    if (snapshot.cookies.isNotEmpty &&
        !policy.permissions.contains(SourcePermission.cookies)) {
      return _invalidResult(request, 'cookie_permission_missing');
    }
    if (webCaptureCookieBytes(snapshot.cookies) >
        webRequest.budget.maxCookieBytes) {
      return _budgetResult(request, 'capture_cookie_budget_exceeded');
    }
    for (final cookie in snapshot.cookies) {
      if (!webCapturePolicyCoversCookieDomain(policy, cookie.domain)) {
        return _invalidResult(request, 'cookie_domain_not_allowed');
      }
    }

    return SourceLiveCaptureResult(
      packageId: request.packageId,
      packageVersion: request.packageVersion,
      programId: request.programId,
      status: SourceLiveCaptureStatus.captured,
      snapshot: snapshot,
    );
  }

  SourceLiveCaptureResult _invalidResult(
    SourceLiveCaptureRequest request,
    String reasonCode,
  ) => SourceLiveCaptureResult(
    packageId: request.packageId,
    packageVersion: request.packageVersion,
    programId: request.programId,
    status: SourceLiveCaptureStatus.invalidSnapshot,
    reasonCode: reasonCode,
  );

  SourceLiveCaptureResult _budgetResult(
    SourceLiveCaptureRequest request,
    String reasonCode,
  ) => SourceLiveCaptureResult(
    packageId: request.packageId,
    packageVersion: request.packageVersion,
    programId: request.programId,
    status: SourceLiveCaptureStatus.budgetExceeded,
    reasonCode: reasonCode,
  );
}

String _candidateKey(WebCandidateKind kind, Uri uri) =>
    '${kind.name}:${uri.toString()}';

bool _sameHeaders(Map<String, String> left, Map<String, String> right) {
  if (left.length != right.length) {
    return false;
  }
  for (final entry in left.entries) {
    if (right[entry.key] != entry.value) {
      return false;
    }
  }
  return true;
}

bool _sameCandidate(WebMediaCandidate left, WebMediaCandidate right) =>
    left.kind == right.kind &&
    left.uri == right.uri &&
    left.sourceEventSequence == right.sourceEventSequence &&
    _sameHeaders(left.headers, right.headers);

bool _sameCandidateRequestMetadata(
  WebMediaCandidate candidate,
  WebCaptureEvent event,
  Uri pageUri,
  List<Uri> redirectChain,
) =>
    candidate.pageUri == pageUri &&
    candidate.requestMethod == event.method &&
    candidate.isRedirect == event.isRedirect &&
    _sameUris(candidate.redirectChain, redirectChain);

bool _sameUris(Iterable<Uri> left, Iterable<Uri> right) {
  final leftList = left.toList(growable: false);
  final rightList = right.toList(growable: false);
  if (leftList.length != rightList.length) return false;
  for (var index = 0; index < leftList.length; index++) {
    if (leftList[index] != rightList[index]) return false;
  }
  return true;
}

String _budgetReason(WebCaptureStopReason reason) => switch (reason) {
  WebCaptureStopReason.eventBudgetExceeded => 'capture_event_budget_exceeded',
  WebCaptureStopReason.candidateBudgetExceeded =>
    'capture_candidate_budget_exceeded',
  WebCaptureStopReason.headerBudgetExceeded => 'capture_header_budget_exceeded',
  WebCaptureStopReason.redirectBudgetExceeded =>
    'capture_redirect_budget_exceeded',
  WebCaptureStopReason.completed => 'capture_budget_exceeded',
};
