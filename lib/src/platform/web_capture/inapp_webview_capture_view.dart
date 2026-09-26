import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import '../../domain/models/source_security_policy.dart';
import '../../domain/models/web_capture_models.dart';
import '../../domain/services/web_source_browser.dart';
import '../../infrastructure/web_capture/web_capture_accumulator.dart';
import 'inapp_webview_event_mapper.dart';

final class InAppWebViewCaptureSettings {
  const InAppWebViewCaptureSettings._();

  static InAppWebViewSettings build(WebCaptureRequest request) {
    final desktop = request.userAgentPolicy.mode == WebUserAgentMode.desktop;
    return InAppWebViewSettings(
      useShouldOverrideUrlLoading: true,
      useShouldInterceptRequest: true,
      // These interceptors enforce the package allowlist even when the
      // capture is collecting a rendered document rather than media events.
      // The callbacks only retain media candidates when
      // [captureMediaRequests] is enabled.
      useShouldInterceptAjaxRequest: true,
      useShouldInterceptFetchRequest: true,
      useOnDownloadStart: true,
      userAgent: request.userAgentPolicy.value ?? '',
      preferredContentMode: desktop
          ? UserPreferredContentMode.DESKTOP
          : UserPreferredContentMode.RECOMMENDED,
      javaScriptEnabled: true,
      javaScriptCanOpenWindowsAutomatically: false,
      mediaPlaybackRequiresUserGesture: true,
      allowFileAccess: false,
      allowContentAccess: false,
      allowFileAccessFromFileURLs: false,
      allowUniversalAccessFromFileURLs: false,
      safeBrowsingEnabled: true,
      mixedContentMode: MixedContentMode.MIXED_CONTENT_NEVER_ALLOW,
      cacheEnabled: false,
      domStorageEnabled: true,
      databaseEnabled: false,
      supportMultipleWindows: false,
    );
  }
}

final class InAppWebViewCaptureView extends StatefulWidget {
  const InAppWebViewCaptureView({
    required this.request,
    required this.browserPort,
    required this.onSnapshot,
    this.onRuntimeStatus,
    this.onSecurityFailure,
    this.onCaptureFailure,
    this.loadingBuilder,
    this.unavailableBuilder,
    super.key,
  });

  final WebCaptureRequest request;
  final WebSourceBrowserPort browserPort;
  final ValueChanged<WebCaptureSnapshot> onSnapshot;
  final ValueChanged<WebCaptureRuntimeStatus>? onRuntimeStatus;
  final ValueChanged<WebCaptureSecurityException>? onSecurityFailure;

  /// Reports a failure that prevents this view from producing a snapshot.
  ///
  /// Policy-blocked individual requests continue to use [onSecurityFailure]
  /// and remain blocked without terminating the capture.
  final ValueChanged<WebCaptureSecurityException>? onCaptureFailure;
  final WidgetBuilder? loadingBuilder;
  final Widget Function(BuildContext context, WebCaptureRuntimeStatus status)?
  unavailableBuilder;

  @override
  State<InAppWebViewCaptureView> createState() =>
      _InAppWebViewCaptureViewState();
}

final class _InAppWebViewCaptureViewState
    extends State<InAppWebViewCaptureView> {
  static final _blockedResponse = WebResourceResponse(
    contentType: 'text/plain',
    contentEncoding: 'utf-8',
    data: Uint8List(0),
    headers: const {'Cache-Control': 'no-store'},
    statusCode: 403,
    reasonPhrase: 'Blocked by source policy',
  );

  final _mapper = const InAppWebViewEventMapper();
  late WebCaptureAccumulator _accumulator;
  late Future<WebCaptureRuntimeStatus> _runtime;
  var _sequence = 0;
  var _loadStopped = false;
  var _completionStarted = false;
  Uri? _lastLoadedUri;
  Timer? _postLoadTimer;

  @override
  void initState() {
    super.initState();
    _reset();
  }

  @override
  void didUpdateWidget(InAppWebViewCaptureView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.request, widget.request) ||
        !identical(oldWidget.browserPort, widget.browserPort)) {
      _reset();
    }
  }

  void _reset() {
    _postLoadTimer?.cancel();
    _postLoadTimer = null;
    _sequence = 0;
    _loadStopped = false;
    _completionStarted = false;
    _lastLoadedUri = null;
    _accumulator = WebCaptureAccumulator(widget.request);
    _runtime = _prepare();
  }

  Future<WebCaptureRuntimeStatus> _prepare() async {
    try {
      final status = await widget.browserPort.probeRuntime();
      widget.onRuntimeStatus?.call(status);
      if (!status.isAvailable) {
        return status;
      }
      await widget.browserPort.importCookies(widget.request);
      _record(
        WebCaptureEvent(
          sequence: _nextSequence(),
          kind: WebRequestKind.navigation,
          uri: widget.request.initialUri,
          headers: widget.request.initialHeaders,
          isMainFrame: true,
        ),
      );
      return status;
    } on WebCaptureSecurityException catch (error) {
      widget.onSecurityFailure?.call(error);
      widget.onCaptureFailure?.call(error);
      return WebCaptureRuntimeStatus(
        state: WebCaptureRuntimeState.unavailable,
        reasonCode: 'webview_bootstrap_security_failed',
      );
    } on Object {
      final error = WebCaptureSecurityException(
        'webview_bootstrap_failed',
        'The platform WebView could not be prepared.',
      );
      widget.onSecurityFailure?.call(error);
      widget.onCaptureFailure?.call(error);
      return WebCaptureRuntimeStatus(
        state: WebCaptureRuntimeState.unavailable,
        reasonCode: 'webview_bootstrap_failed',
      );
    }
  }

  int _nextSequence() => _sequence++;

  bool _record(WebCaptureEvent event) {
    try {
      final accepted = _accumulator.add(event);
      if (accepted &&
          _loadStopped &&
          widget.request.completionPolicy ==
              WebCaptureCompletionPolicy.firstPlayableCandidateAfterLoad &&
          _accumulator.hasMediaCandidate) {
        final finalUri = _lastLoadedUri;
        if (finalUri != null) {
          unawaited(_complete(finalUri));
        }
      }
      return accepted;
    } on WebCaptureSecurityException catch (error) {
      widget.onSecurityFailure?.call(error);
      return false;
    }
  }

  Future<bool> _recordObservedRequest(WebCaptureEvent event) async {
    if (widget.request.securityPolicy.allowsUri(event.uri)) {
      return _record(event);
    }
    if (!widget.request.allowRuntimeMediaOrigins ||
        event.uri.scheme != 'https' ||
        event.uri.userInfo.isNotEmpty ||
        event.uri.host.isEmpty ||
        event.isMainFrame ||
        event.kind == WebRequestKind.navigation ||
        event.kind == WebRequestKind.iframe) {
      return false;
    }
    try {
      final addresses = await InternetAddress.lookup(
        event.uri.host,
      ).timeout(const Duration(seconds: 3));
      if (addresses.isEmpty ||
          addresses.any((address) => !isPublicWebCaptureAddress(address))) {
        return false;
      }
      return _record(event.withRuntimeOriginValidated());
    } on Object {
      return false;
    }
  }

  Future<void> _complete(
    Uri finalUri, {
    InAppWebViewController? controller,
  }) async {
    if (_completionStarted) return;
    _completionStarted = true;
    _postLoadTimer?.cancel();
    _postLoadTimer = null;
    try {
      String? documentBody;
      if (widget.request.captureDocument) {
        documentBody = await controller?.getHtml();
        if (documentBody == null) {
          throw WebCaptureSecurityException(
            'document_missing',
            'The WebView did not provide a document snapshot.',
          );
        }
      }
      final cookies = <WebCaptureCookie>[];
      if (widget.request.securityPolicy.permissions.contains(
        SourcePermission.cookies,
      )) {
        final cookieUris = <Uri>{finalUri, ..._accumulator.candidateUris};
        final seenCookies = <String>{};
        for (final uri in cookieUris) {
          final exported = await widget.browserPort.exportCookies(
            widget.request,
            uri,
            runtimeOriginGrant: _accumulator.runtimeGrantForUri(uri),
          );
          for (final cookie in exported) {
            final key = '${cookie.domain}|${cookie.path}|${cookie.name}';
            if (seenCookies.add(key)) cookies.add(cookie);
          }
        }
      }
      if (!mounted) {
        // The completion flag is deliberately retained: a late platform
        // callback must not restart a superseded capture.
        return;
      }
      widget.onSnapshot(
        _accumulator.finish(
          finalUri: finalUri,
          cookies: cookies,
          documentBody: documentBody,
        ),
      );
    } on WebCaptureSecurityException catch (error) {
      widget.onSecurityFailure?.call(error);
      widget.onCaptureFailure?.call(error);
    } on Object {
      final error = WebCaptureSecurityException(
        'webview_capture_finalize_failed',
        'The platform WebView could not finalize capture.',
      );
      widget.onSecurityFailure?.call(error);
      widget.onCaptureFailure?.call(error);
    }
  }

  void _failCapture(String code, String message) {
    if (_completionStarted) return;
    _completionStarted = true;
    _postLoadTimer?.cancel();
    _postLoadTimer = null;
    widget.onCaptureFailure?.call(WebCaptureSecurityException(code, message));
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<WebCaptureRuntimeStatus>(
      future: _runtime,
      builder: (context, snapshot) {
        final status = snapshot.data;
        if (status == null) {
          return widget.loadingBuilder?.call(context) ??
              const Center(child: CircularProgressIndicator());
        }
        if (!status.isAvailable) {
          return _unavailable(context, status);
        }
        return _buildWebView();
      },
    );
  }

  Widget _unavailable(BuildContext context, WebCaptureRuntimeStatus status) {
    return widget.unavailableBuilder?.call(context, status) ??
        Center(
          child: Text(
            status.reasonCode ?? 'webview_unavailable',
            textAlign: TextAlign.center,
          ),
        );
  }

  Widget _buildWebView() {
    return InAppWebView(
      initialUrlRequest: URLRequest(
        url: WebUri(widget.request.initialUri.toString()),
        method: 'GET',
        headers: widget.request.initialHeaders,
      ),
      initialSettings: InAppWebViewCaptureSettings.build(widget.request),
      shouldOverrideUrlLoading: (controller, action) async {
        final event = _mapper.navigation(_nextSequence(), action);
        if (event == null || !_record(event)) {
          return NavigationActionPolicy.CANCEL;
        }
        return NavigationActionPolicy.ALLOW;
      },
      shouldInterceptRequest: (controller, request) async {
        final event = _mapper.resource(_nextSequence(), request);
        return event != null && await _recordObservedRequest(event)
            ? null
            : _blockedResponse;
      },
      shouldInterceptAjaxRequest: (controller, request) async {
        final event = _mapper.ajax(_nextSequence(), request);
        if (event == null || !await _recordObservedRequest(event)) {
          request.action = AjaxRequestAction.ABORT;
        } else {
          request.action = AjaxRequestAction.PROCEED;
        }
        return request;
      },
      shouldInterceptFetchRequest: (controller, request) async {
        final event = _mapper.fetch(_nextSequence(), request);
        if (event == null || !await _recordObservedRequest(event)) {
          request.action = FetchRequestAction.ABORT;
        } else {
          request.action = FetchRequestAction.PROCEED;
        }
        return request;
      },
      onLoadStop: (controller, url) async {
        if (url == null) {
          return;
        }
        final finalUri = Uri.tryParse(url.toString());
        if (finalUri == null ||
            !widget.request.securityPolicy.allowsUri(finalUri)) {
          final error = WebCaptureSecurityException(
            'final_uri_not_allowed',
            'Final WebView URI is outside the declared source allowlist.',
          );
          widget.onSecurityFailure?.call(error);
          widget.onCaptureFailure?.call(error);
          return;
        }
        _loadStopped = true;
        _lastLoadedUri = finalUri;
        if (widget.request.completionPolicy ==
            WebCaptureCompletionPolicy.loadStop) {
          await _complete(finalUri, controller: controller);
          return;
        }
        if (widget.request.completionPolicy ==
            WebCaptureCompletionPolicy.documentAfterLoad) {
          _postLoadTimer?.cancel();
          _postLoadTimer = Timer(widget.request.postLoadTimeout, () {
            unawaited(_complete(finalUri, controller: controller));
          });
          return;
        }
        if (widget.request.completionPolicy ==
                WebCaptureCompletionPolicy.firstPlayableCandidateAfterLoad &&
            _accumulator.hasMediaCandidate) {
          await _complete(finalUri, controller: controller);
          return;
        }
        _postLoadTimer?.cancel();
        _postLoadTimer = Timer(widget.request.postLoadTimeout, () {
          if (widget.request.completionPolicy ==
                  WebCaptureCompletionPolicy
                      .firstValidatedPlayableCandidateAfterLoad &&
              _accumulator.hasValidatedPlayableCandidate) {
            unawaited(_complete(finalUri, controller: controller));
          } else {
            _failCapture(
              'browser_capture_timeout',
              'The public player did not expose a validated playable media candidate within the bounded capture window.',
            );
          }
        });
      },
      onDownloadStarting: (controller, request) {
        widget.onSecurityFailure?.call(
          WebCaptureSecurityException(
            'webview_download_blocked',
            'WebView download handling is outside Phase 3 authority.',
          ),
        );
        return DownloadStartResponse(
          handled: true,
          action: DownloadStartResponseAction.CANCEL,
        );
      },
      onReceivedHttpAuthRequest: (controller, challenge) async =>
          HttpAuthResponse(action: HttpAuthResponseAction.CANCEL),
      onReceivedServerTrustAuthRequest: (controller, challenge) async =>
          ServerTrustAuthResponse(action: ServerTrustAuthResponseAction.CANCEL),
      onPermissionRequest: (controller, request) async => PermissionResponse(
        resources: request.resources,
        action: PermissionResponseAction.DENY,
      ),
      onGeolocationPermissionsShowPrompt: (controller, origin) async =>
          GeolocationPermissionShowPromptResponse(
            origin: origin,
            allow: false,
            retain: false,
          ),
      onCreateWindow: (controller, action) async => false,
    );
  }

  @override
  void dispose() {
    _postLoadTimer?.cancel();
    _postLoadTimer = null;
    super.dispose();
  }
}

/// Returns whether a resolved address may be used for an event-derived media
/// origin. Kept pure so private/special-purpose ranges remain regression
/// tested without performing DNS or exposing the resolved address.
bool isPublicWebCaptureAddress(InternetAddress address) {
  if (address.isLoopback || address.isLinkLocal || address.isMulticast) {
    return false;
  }
  final bytes = address.rawAddress;
  if (address.type == InternetAddressType.IPv4) {
    return !_isNonPublicIpv4(bytes);
  }
  if (bytes.every((value) => value == 0)) {
    return false;
  }
  if (_isIpv4Embedded(bytes) || _isWellKnownNat64(bytes)) {
    return !_isNonPublicIpv4(bytes.sublist(12));
  }
  final isLocalUseNat64 =
      bytes[0] == 0x00 &&
      bytes[1] == 0x64 &&
      bytes[2] == 0xff &&
      bytes[3] == 0x9b &&
      bytes[4] == 0x00 &&
      bytes[5] == 0x01;
  final isDiscardOnly =
      bytes[0] == 0x01 && bytes.skip(1).take(7).every((value) => value == 0);
  final isIetfSpecialPurpose =
      bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] <= 0x01;
  final isDocumentationV6 =
      bytes[0] == 0x20 &&
      bytes[1] == 0x01 &&
      bytes[2] == 0x0d &&
      bytes[3] == 0xb8;
  final isSixToFour = bytes[0] == 0x20 && bytes[1] == 0x02;
  final isDocumentation =
      bytes[0] == 0x3f && bytes[1] == 0xff && (bytes[2] & 0xf0) == 0;
  final isUniqueLocal = (bytes[0] & 0xfe) == 0xfc;
  final isSiteLocal = bytes[0] == 0xfe && (bytes[1] & 0xc0) == 0xc0;
  return !(isLocalUseNat64 ||
      isDiscardOnly ||
      isIetfSpecialPurpose ||
      isDocumentationV6 ||
      isSixToFour ||
      isDocumentation ||
      isUniqueLocal ||
      isSiteLocal);
}

bool _isNonPublicIpv4(List<int> bytes) {
  final a = bytes[0];
  final b = bytes[1];
  final c = bytes[2];
  return a == 0 ||
      a == 10 ||
      a == 127 ||
      (a == 100 && b >= 64 && b <= 127) ||
      (a == 169 && b == 254) ||
      (a == 172 && b >= 16 && b <= 31) ||
      (a == 192 && b == 0 && c == 0) ||
      (a == 192 && b == 0 && c == 2) ||
      (a == 192 && b == 31 && c == 196) ||
      (a == 192 && b == 52 && c == 193) ||
      (a == 192 && b == 88 && c == 99) ||
      (a == 192 && b == 168) ||
      (a == 192 && b == 175 && c == 48) ||
      (a == 198 && (b == 18 || b == 19)) ||
      (a == 198 && b == 51 && c == 100) ||
      (a == 203 && b == 0 && c == 113) ||
      a >= 224;
}

bool _isWellKnownNat64(List<int> bytes) =>
    bytes.length == 16 &&
    bytes[0] == 0x00 &&
    bytes[1] == 0x64 &&
    bytes[2] == 0xff &&
    bytes[3] == 0x9b &&
    bytes.skip(4).take(8).every((value) => value == 0);

bool _isIpv4Embedded(List<int> bytes) {
  if (bytes.length != 16 || bytes.take(10).any((value) => value != 0)) {
    return false;
  }
  final compatible = bytes[10] == 0 && bytes[11] == 0;
  final mapped = bytes[10] == 0xff && bytes[11] == 0xff;
  return compatible || mapped;
}
