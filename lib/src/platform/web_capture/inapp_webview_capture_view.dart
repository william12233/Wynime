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
      useShouldInterceptAjaxRequest: request.captureMediaRequests,
      useShouldInterceptFetchRequest: request.captureMediaRequests,
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
    this.loadingBuilder,
    this.unavailableBuilder,
    super.key,
  });

  final WebCaptureRequest request;
  final WebSourceBrowserPort browserPort;
  final ValueChanged<WebCaptureSnapshot> onSnapshot;
  final ValueChanged<WebCaptureRuntimeStatus>? onRuntimeStatus;
  final ValueChanged<WebCaptureSecurityException>? onSecurityFailure;
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
    _sequence = 0;
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
      return WebCaptureRuntimeStatus(
        state: WebCaptureRuntimeState.unavailable,
        reasonCode: 'webview_bootstrap_security_failed',
      );
    } on Object {
      widget.onSecurityFailure?.call(
        WebCaptureSecurityException(
          'webview_bootstrap_failed',
          'The platform WebView could not be prepared.',
        ),
      );
      return WebCaptureRuntimeStatus(
        state: WebCaptureRuntimeState.unavailable,
        reasonCode: 'webview_bootstrap_failed',
      );
    }
  }

  int _nextSequence() => _sequence++;

  bool _record(WebCaptureEvent event) {
    try {
      return _accumulator.add(event);
    } on WebCaptureSecurityException catch (error) {
      widget.onSecurityFailure?.call(error);
      return false;
    }
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
        return event != null && _record(event) ? null : _blockedResponse;
      },
      shouldInterceptAjaxRequest: (controller, request) async {
        final event = _mapper.ajax(_nextSequence(), request);
        if (event == null || !_record(event)) {
          request.action = AjaxRequestAction.ABORT;
        } else {
          request.action = AjaxRequestAction.PROCEED;
        }
        return request;
      },
      shouldInterceptFetchRequest: (controller, request) async {
        final event = _mapper.fetch(_nextSequence(), request);
        if (event == null || !_record(event)) {
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
          widget.onSecurityFailure?.call(
            WebCaptureSecurityException(
              'final_uri_not_allowed',
              'Final WebView URI is outside the declared source allowlist.',
            ),
          );
          return;
        }
        try {
          final cookies =
              widget.request.securityPolicy.permissions.contains(
                SourcePermission.cookies,
              )
              ? await widget.browserPort.exportCookies(widget.request, finalUri)
              : const <WebCaptureCookie>[];
          widget.onSnapshot(
            _accumulator.finish(finalUri: finalUri, cookies: cookies),
          );
        } on WebCaptureSecurityException catch (error) {
          widget.onSecurityFailure?.call(error);
        }
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
}
