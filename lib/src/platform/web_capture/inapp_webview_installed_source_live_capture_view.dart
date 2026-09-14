import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:pub_semver/pub_semver.dart';

import '../../application/source_live_capture_package_coordinator.dart';
import '../../domain/models/source_live_capture_models.dart';
import '../../domain/models/source_live_capture_package_models.dart';
import '../../domain/models/web_capture_models.dart';
import '../../domain/services/web_source_browser.dart';
import 'inapp_webview_source_live_capture_port.dart';

/// Mounts live capture only after installed-package admission succeeds.
///
/// The lower-level [InAppWebViewSourceLiveCapture] surface accepts an already
/// admitted request. This package-aware surface is the production handoff for
/// an [InstalledSourcePackage]: it performs the package preflight first and
/// mounts no WebView when the package is not eligible.
final class InAppWebViewInstalledSourceLiveCapture extends StatefulWidget {
  const InAppWebViewInstalledSourceLiveCapture({
    required this.wynimeVersion,
    required this.plan,
    required this.browserPort,
    required this.onAdmission,
    required this.onResult,
    this.onRuntimeStatus,
    this.onSecurityFailure,
    this.captureViewBuilder,
    this.loadingBuilder,
    this.unavailableBuilder,
    super.key,
  });

  final Version wynimeVersion;
  final SourceLiveCapturePackagePlan plan;
  final WebSourceBrowserPort browserPort;
  final ValueChanged<SourceLiveCapturePackageResult> onAdmission;
  final ValueChanged<SourceLiveCaptureResult> onResult;
  final ValueChanged<WebCaptureRuntimeStatus>? onRuntimeStatus;
  final ValueChanged<WebCaptureSecurityException>? onSecurityFailure;
  final InAppWebViewCaptureWidgetBuilder? captureViewBuilder;
  final WidgetBuilder? loadingBuilder;
  final Widget Function(BuildContext context, WebCaptureRuntimeStatus status)?
  unavailableBuilder;

  @override
  State<InAppWebViewInstalledSourceLiveCapture> createState() =>
      _InAppWebViewInstalledSourceLiveCaptureState();
}

final class _InAppWebViewInstalledSourceLiveCaptureState
    extends State<InAppWebViewInstalledSourceLiveCapture> {
  late InAppWebViewSourceLiveCapturePort _port;
  late SourceLiveCapturePackageCoordinator _packageCoordinator;
  SourceLiveCapturePackageResult? _admission;
  var _widgetGeneration = 0;

  @override
  void initState() {
    super.initState();
    _createCapture();
    _startCapture();
  }

  @override
  void didUpdateWidget(InAppWebViewInstalledSourceLiveCapture oldWidget) {
    super.didUpdateWidget(oldWidget);
    final planChanged = !identical(oldWidget.plan, widget.plan);
    final versionChanged =
        oldWidget.wynimeVersion.toString() != widget.wynimeVersion.toString();
    final browserChanged = !identical(
      oldWidget.browserPort,
      widget.browserPort,
    );
    final builderChanged = !identical(
      oldWidget.captureViewBuilder,
      widget.captureViewBuilder,
    );
    if (planChanged || versionChanged || browserChanged || builderChanged) {
      ++_widgetGeneration;
      _packageCoordinator.close();
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
    _packageCoordinator = SourceLiveCapturePackageCoordinator(
      wynimeVersion: widget.wynimeVersion,
      port: _port,
    );
  }

  void _startCapture() {
    final generation = ++_widgetGeneration;
    late SourceLiveCapturePackageResult admission;
    try {
      admission = _packageCoordinator.prepare(widget.plan);
    } on Object {
      admission = SourceLiveCapturePackageResult(
        packageId: widget.plan.installedPackage.package.packageId,
        packageVersion: widget.plan.installedPackage.package.version,
        programId: widget.plan.programId,
        status: SourceLiveCapturePackageStatus.failed,
        reasonCode: 'package_preflight_failed',
      );
    }
    _admission = admission;
    _notifyAdmission(admission);

    final request = admission.request;
    if (request == null) {
      return;
    }
    final future = _packageCoordinator.capture(widget.plan);
    unawaited(_deliver(future, generation, request));
  }

  Future<void> _deliver(
    Future<SourceLiveCaptureResult> future,
    int generation,
    SourceLiveCaptureRequest request,
  ) async {
    late SourceLiveCaptureResult result;
    try {
      result = await future;
    } on Object {
      result = SourceLiveCaptureResult(
        packageId: request.packageId,
        packageVersion: request.packageVersion,
        programId: request.programId,
        status: SourceLiveCaptureStatus.failed,
        reasonCode: 'capture_failed',
      );
    }
    if (!mounted || generation != _widgetGeneration) {
      return;
    }
    _notifyResult(result);
  }

  void _notifyAdmission(SourceLiveCapturePackageResult result) {
    try {
      widget.onAdmission(result);
    } on Object {
      // An observer failure must not turn an admitted capture into a
      // different platform outcome or strand its completion.
    }
  }

  void _notifyResult(SourceLiveCaptureResult result) {
    try {
      widget.onResult(result);
    } on Object {
      // Result observers do not own capture lifecycle or platform cleanup.
    }
  }

  @override
  Widget build(BuildContext context) {
    final request = _admission?.request;
    if (request == null) {
      return const SizedBox.shrink();
    }
    return _port.buildView(
      context,
      loadingBuilder: widget.loadingBuilder,
      unavailableBuilder: widget.unavailableBuilder,
    );
  }

  @override
  void dispose() {
    ++_widgetGeneration;
    _packageCoordinator.close();
    _port.close();
    super.dispose();
  }
}
