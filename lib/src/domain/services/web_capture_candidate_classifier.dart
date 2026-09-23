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
    return kindScore + methodScore + redirectScore + provenanceScore;
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
