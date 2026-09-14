import '../models/source_http_models.dart';

/// Typed boundary for bounded source HTTP. Implementations own network I/O;
/// Domain callers receive only a redacted typed failure or a bounded text
/// response kept in memory.
abstract interface class SourceHttpTransport {
  Future<SourceHttpTransportResult> send(SourceHttpRequest request);

  Future<void> close();
}
