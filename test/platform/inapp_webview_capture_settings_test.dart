import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/platform/web_capture/inapp_webview_capture_view.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  WebCaptureRequest request() => WebCaptureRequest(
    initialUri: Uri.parse('https://example.com/watch'),
    securityPolicy: testSourcePolicy(
      permissions: {
        SourcePermission.network,
        SourcePermission.webView,
        SourcePermission.desktopUserAgent,
        SourcePermission.mediaRequestInspection,
      },
    ),
    budget: WebCaptureBudget(
      maxEvents: 100,
      maxCandidates: 20,
      maxHeaderBytes: 16 * 1024,
      maxCookieBytes: 0,
    ),
    userAgentPolicy: WebUserAgentPolicy(
      mode: WebUserAgentMode.desktop,
      value: 'Mozilla/5.0 Wynime Desktop Capture',
    ),
    captureMediaRequests: true,
  );

  test('WebView settings fail closed on privileged browser features', () {
    final settings = InAppWebViewCaptureSettings.build(request());

    expect(settings.useShouldOverrideUrlLoading, isTrue);
    expect(settings.useShouldInterceptRequest, isTrue);
    expect(settings.useShouldInterceptAjaxRequest, isTrue);
    expect(settings.useShouldInterceptFetchRequest, isTrue);
    expect(settings.useOnDownloadStart, isTrue);
    expect(settings.allowFileAccess, isFalse);
    expect(settings.allowContentAccess, isFalse);
    expect(settings.allowFileAccessFromFileURLs, isFalse);
    expect(settings.allowUniversalAccessFromFileURLs, isFalse);
    expect(settings.javaScriptCanOpenWindowsAutomatically, isFalse);
    expect(settings.mediaPlaybackRequiresUserGesture, isTrue);
    expect(
      settings.mixedContentMode,
      MixedContentMode.MIXED_CONTENT_NEVER_ALLOW,
    );
    expect(settings.cacheEnabled, isFalse);
    expect(settings.supportMultipleWindows, isFalse);
    expect(settings.preferredContentMode, UserPreferredContentMode.DESKTOP);
  });
}
