import 'dart:io';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import '../../domain/models/web_capture_models.dart';
import '../../domain/services/web_source_browser.dart';

final class InAppWebViewBrowserPort implements WebSourceBrowserPort {
  InAppWebViewBrowserPort({CookieManager? cookieManager})
    : _cookieManager = cookieManager ?? CookieManager.instance();

  final CookieManager _cookieManager;

  @override
  Future<WebCaptureRuntimeStatus> probeRuntime() async {
    if (Platform.isAndroid) {
      return WebCaptureRuntimeStatus(
        state: WebCaptureRuntimeState.available,
        platform: WebCapturePlatform.androidWebView,
      );
    }
    if (Platform.isWindows) {
      try {
        final version = await WebViewEnvironment.getAvailableVersion();
        if (version == null || version.trim().isEmpty) {
          return WebCaptureRuntimeStatus(
            state: WebCaptureRuntimeState.unavailable,
            platform: WebCapturePlatform.windowsWebView2,
            reasonCode: 'webview2_runtime_missing',
          );
        }
        return WebCaptureRuntimeStatus(
          state: WebCaptureRuntimeState.available,
          platform: WebCapturePlatform.windowsWebView2,
          runtimeVersion: version,
        );
      } on Object {
        return WebCaptureRuntimeStatus(
          state: WebCaptureRuntimeState.unavailable,
          platform: WebCapturePlatform.windowsWebView2,
          reasonCode: 'webview2_probe_failed',
        );
      }
    }
    return WebCaptureRuntimeStatus(
      state: WebCaptureRuntimeState.unsupported,
      reasonCode: 'platform_not_supported',
    );
  }

  @override
  Future<void> importCookies(WebCaptureRequest request) async {
    for (final cookie in request.initialCookies) {
      final scopeUri = _cookieScopeUri(request, cookie.domain, cookie.path);
      final success = await _cookieManager.setCookie(
        url: WebUri(scopeUri.toString()),
        name: cookie.name,
        value: cookie.value,
        domain: cookie.domain,
        path: cookie.path,
        expiresDate: cookie.expiresAt?.millisecondsSinceEpoch,
        isSecure: cookie.isSecure,
        isHttpOnly: cookie.isHttpOnly,
      );
      if (!success) {
        throw WebCaptureSecurityException(
          'cookie_import_failed',
          'The platform WebView rejected a declared cookie.',
        );
      }
    }
  }

  @override
  Future<List<WebCaptureCookie>> exportCookies(
    WebCaptureRequest request,
    Uri uri,
  ) async {
    _requireAllowedUri(request, uri);
    final platformCookies = await _cookieManager.getCookies(
      url: WebUri(uri.toString()),
    );
    final cookies = <WebCaptureCookie>[];
    for (final cookie in platformCookies) {
      final domain = (cookie.domain ?? uri.host).trim();
      final normalizedDomain = domain.startsWith('.')
          ? domain.substring(1)
          : domain;
      if (!webCapturePolicyCoversCookieDomain(
        request.securityPolicy,
        normalizedDomain,
      )) {
        continue;
      }
      try {
        cookies.add(
          WebCaptureCookie(
            name: cookie.name,
            value: cookie.value?.toString() ?? '',
            domain: normalizedDomain,
            path: cookie.path ?? '/',
            isSecure: cookie.isSecure ?? uri.scheme == 'https',
            isHttpOnly: cookie.isHttpOnly ?? false,
            expiresAt: cookie.expiresDate == null
                ? null
                : DateTime.fromMillisecondsSinceEpoch(
                    cookie.expiresDate!,
                    isUtc: true,
                  ),
          ),
        );
      } on ArgumentError {
        throw WebCaptureSecurityException(
          'platform_cookie_invalid',
          'The platform WebView returned an invalid cookie.',
        );
      }
    }
    if (webCaptureCookieBytes(cookies) > request.budget.maxCookieBytes) {
      throw WebCaptureSecurityException(
        'cookie_budget_exceeded',
        'Platform cookies exceed the declared capture budget.',
      );
    }
    return List<WebCaptureCookie>.unmodifiable(cookies);
  }

  @override
  Future<void> clearCookies(WebCaptureRequest request, Uri uri) async {
    _requireAllowedUri(request, uri);
    final cookies = await _cookieManager.getCookies(
      url: WebUri(uri.toString()),
    );
    for (final cookie in cookies) {
      final domain = cookie.domain ?? uri.host;
      final normalizedDomain = domain.startsWith('.')
          ? domain.substring(1)
          : domain;
      if (!webCapturePolicyCoversCookieDomain(
        request.securityPolicy,
        normalizedDomain,
      )) {
        continue;
      }
      final path = cookie.path ?? '/';
      final scopeUri = _cookieScopeUri(request, normalizedDomain, path);
      await _cookieManager.deleteCookie(
        url: WebUri(scopeUri.toString()),
        name: cookie.name,
        domain: domain,
        path: path,
      );
    }
  }

  static Uri _cookieScopeUri(
    WebCaptureRequest request,
    String domain,
    String path,
  ) {
    final uri = Uri(
      scheme: request.initialUri.scheme,
      host: domain,
      path: path,
    );
    _requireAllowedUri(request, uri);
    return uri;
  }

  static void _requireAllowedUri(WebCaptureRequest request, Uri uri) {
    if (!request.securityPolicy.allowsUri(uri)) {
      throw WebCaptureSecurityException(
        'cookie_uri_not_allowed',
        'Cookie operation URI is outside the source allowlist.',
      );
    }
  }
}
