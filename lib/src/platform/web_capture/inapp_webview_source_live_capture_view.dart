import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../application/source_live_capture_coordinator.dart';
import '../../domain/models/source_live_capture_models.dart';
import '../../domain/models/web_capture_models.dart';
import '../../domain/services/web_source_browser.dart';
import 'inapp_webview_source_live_capture_port.dart';

/// Composes one admitted source capture with the platform WebView port.
///
/// This widget is intentionally a reusable capture surface, not a Search or
/// playback route. Callers decide when a source package/program is eligible;
/// this surface only reports the coordinator's typed result.
final class InAppWebViewSourceLiveCapture extends StatefulWidget {
  const InAppWebViewSourceLiveCapture({
    required this.request,
    required this.browserPort,
    required this.onResult,
    this.onRuntimeStatus,
    this.onSecurityFailure,
    this.captureViewBuilder,
    this.loadingBuilder,
    this.unavailableBuilder,
    super.key,
  });

  final SourceLiveCaptureRequest request;
  final WebSourceBrowserPort browserPort;
  final ValueChanged<SourceLiveCaptureResult> onResult;
  final ValueChanged<WebCaptureRuntimeStatus>? onRuntimeStatus;
  final ValueChanged<WebCaptureSecurityException>? onSecurityFailure;
  final InAppWebViewCaptureWidgetBuilder? captureViewBuilder;
  final WidgetBuilder? loadingBuilder;
  final Widget Function(BuildContext context, WebCaptureRuntimeStatus status)?
  unavailableBuilder;

  @override
  State<InAppWebViewSourceLiveCapture> createState() =>
      _InAppWebViewSourceLiveCaptureState();
}

final class _InAppWebViewSourceLiveCaptureState
    extends State<InAppWebViewSourceLiveCapture> {
  late InAppWebViewSourceLiveCapturePort _port;
  late SourceLiveCaptureCoordinator _coordinator;
  var _widgetGeneration = 0;

  @override
  void initState() {
    super.initState();
    _createCapture();
    _startCapture();
  }

  @override
  void didUpdateWidget(InAppWebViewSourceLiveCapture oldWidget) {
    super.didUpdateWidget(oldWidget);
    final requestChanged = !identical(oldWidget.request, widget.request);
    final browserChanged = !identical(
      oldWidget.browserPort,
      widget.browserPort,
    );
    final builderChanged = !identical(
      oldWidget.captureViewBuilder,
      widget.captureViewBuilder,
    );
    if (requestChanged || browserChanged || builderChanged) {
      ++_widgetGeneration;
      _coordinator.close();
      _port.close();
      _createCapture();
      _startCapture();
      return;
    }
    _port.updateObservers(
      onRuntimeStatus: widget.onRuntimeStatus,
      onSecurityFailure: widget.onSecurityFailure,
    );
  }

  void _createCapture() {
    _port = InAppWebViewSourceLiveCapturePort(
      browserPort: widget.browserPort,
      viewBuilder: widget.captureViewBuilder,
      onRuntimeStatus: widget.onRuntimeStatus,
      onSecurityFailure: widget.onSecurityFailure,
    );
    _coordinator = SourceLiveCaptureCoordinator(port: _port);
  }

  void _startCapture() {
    final generation = ++_widgetGeneration;
    final future = _coordinator.capture(widget.request);
    unawaited(_deliver(future, generation));
  }

  Future<void> _deliver(
    Future<SourceLiveCaptureResult> future,
    int generation,
  ) async {
    late SourceLiveCaptureResult result;
    try {
      result = await future;
    } on Object {
      result = SourceLiveCaptureResult(
        packageId: widget.request.packageId,
        packageVersion: widget.request.packageVersion,
        programId: widget.request.programId,
        status: SourceLiveCaptureStatus.failed,
        reasonCode: 'capture_failed',
      );
    }
    if (!mounted || generation != _widgetGeneration) {
      return;
    }
    widget.onResult(result);
  }

  @override
  Widget build(BuildContext context) => _port.buildView(
    context,
    loadingBuilder: widget.loadingBuilder,
    unavailableBuilder: widget.unavailableBuilder,
  );

  @override
  void dispose() {
    ++_widgetGeneration;
    _coordinator.close();
    _port.close();
    super.dispose();
  }
}
