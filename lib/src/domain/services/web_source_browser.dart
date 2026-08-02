import '../models/web_capture_models.dart';

abstract interface class WebSourceBrowserPort {
  Future<WebCaptureRuntimeStatus> probeRuntime();

  Future<void> importCookies(WebCaptureRequest request);

  Future<List<WebCaptureCookie>> exportCookies(
    WebCaptureRequest request,
    Uri uri,
  );

  Future<void> clearCookies(WebCaptureRequest request, Uri uri);
}
