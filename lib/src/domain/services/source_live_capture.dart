import '../models/source_live_capture_models.dart';
import '../models/web_capture_models.dart';

/// Platform/WebView implementations provide one bounded capture snapshot.
///
/// The port has no authority to install packages, persist cookies, resolve
/// playback or expose raw platform exceptions to presentation code.
abstract interface class SourceLiveCapturePort {
  Future<WebCaptureSnapshot> capture(SourceLiveCaptureRequest request);
}

/// Secret-safe platform failure that may cross the capture port boundary.
abstract interface class SourceLiveCaptureFailure implements Exception {
  String get code;
}
