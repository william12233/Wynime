import '../models/web_capture_models.dart';

/// Classifies one captured request using the same deterministic rules used by
/// the platform capture accumulator and every downstream snapshot consumer.
///
/// This is deliberately pure: it does not inspect response bodies, perform
/// network work or execute source-package code.
final class WebCaptureCandidateClassifier {
  const WebCaptureCandidateClassifier();

  /// Returns the exact URI shape used when the accumulator creates a
  /// [WebMediaCandidate].
  Uri normalizeCandidateUri(Uri uri) => uri.replace(fragment: '');

  WebCandidateKind? classify(WebCaptureEvent event) {
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
    final destination = _headerValue(
      event.headers,
      'sec-fetch-dest',
    )?.toLowerCase();
    final accept = _headerValue(event.headers, 'accept')?.toLowerCase() ?? '';
    final hasRange = _headerValue(event.headers, 'range')?.isNotEmpty == true;
    final rangeHasNoContradictingDestination =
        destination == null ||
        destination.isEmpty ||
        destination == 'video' ||
        destination == 'audio';
    final rangeHasNoContradictingAccept =
        !accept.contains('image/') &&
        !accept.contains('font/') &&
        !accept.contains('text/css') &&
        !accept.contains('javascript');
    if (event.method == 'GET' &&
        (destination == 'video' ||
            destination == 'audio' ||
            accept.contains('video/') ||
            accept.contains('audio/') ||
            (hasRange &&
                rangeHasNoContradictingDestination &&
                rangeHasNoContradictingAccept))) {
      return WebCandidateKind.video;
    }
    return null;
  }

  /// Deterministically ranks captured candidates without using a source name,
  /// host, URL token or package-specific rule. Validation still happens in the
  /// bounded probe; this score only avoids trying obvious segments and
  /// redirectors before a supported media resource.
  int score(WebMediaCandidate candidate) {
    final kindScore = switch (candidate.kind) {
      WebCandidateKind.hls => 400,
      WebCandidateKind.video => 300,
      WebCandidateKind.audio => 250,
      WebCandidateKind.dash => 100,
      WebCandidateKind.mediaSegment => 50,
    };
    final methodScore = candidate.requestMethod == 'GET' ? 40 : 0;
    final redirectScore = candidate.isRedirect ? -20 : 0;
    final provenanceScore = candidate.redirectChain.isEmpty ? 10 : 0;
    return kindScore +
        _evidenceScore(candidate) +
        methodScore +
        redirectScore +
        provenanceScore;
  }

  /// Orders candidates by explicit media evidence, then by the browser event
  /// sequence and normalized URI. The final tie-breakers make selection stable
  /// without relying on map insertion order or source-specific host knowledge.
  int compareCandidates(WebMediaCandidate left, WebMediaCandidate right) {
    final evidence = score(right).compareTo(score(left));
    if (evidence != 0) return evidence;
    final sequence = left.sourceEventSequence.compareTo(
      right.sourceEventSequence,
    );
    if (sequence != 0) return sequence;
    return normalizeCandidateUri(
      left.uri,
    ).toString().compareTo(normalizeCandidateUri(right.uri).toString());
  }

  int _evidenceScore(WebMediaCandidate candidate) {
    final contentType = _headerValue(
      candidate.headers,
      'content-type',
    )?.split(';').first.trim().toLowerCase();
    final path = candidate.uri.path.toLowerCase();
    final destination = _headerValue(
      candidate.headers,
      'sec-fetch-dest',
    )?.toLowerCase();
    final accept =
        _headerValue(candidate.headers, 'accept')?.toLowerCase() ?? '';
    final hasRange =
        _headerValue(candidate.headers, 'range')?.isNotEmpty == true;
    if (contentType == 'application/vnd.apple.mpegurl' ||
        contentType == 'application/x-mpegurl' ||
        contentType == 'audio/mpegurl' ||
        contentType == 'application/dash+xml' ||
        (contentType?.startsWith('video/') ?? false) ||
        (contentType?.startsWith('audio/') ?? false)) {
      return 100;
    }
    if (_endsWithAny(path, const [
      '.m3u8',
      '.mpd',
      '.mp4',
      '.mkv',
      '.webm',
      '.mov',
      '.m4a',
      '.aac',
      '.mp3',
      '.flac',
      '.opus',
    ])) {
      return 80;
    }
    if (destination == 'video' || destination == 'audio') return 60;
    if (accept.contains('video/') || accept.contains('audio/')) return 40;
    if (hasRange) return 10;
    return 0;
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
