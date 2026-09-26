import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../domain/models/source_live_capture_models.dart';
import '../../domain/models/web_capture_models.dart';
import '../../domain/services/source_live_capture.dart';
import '../../domain/services/web_source_browser.dart';
import 'inapp_webview_capture_view.dart';

/// Builds the lower-level WebView capture widget for one admitted request.
///
/// The default builder is the production [InAppWebViewCaptureView]. Tests may
/// inject a callback-driven widget without creating a platform WebView.
typedef InAppWebViewCaptureWidgetBuilder =
    Widget Function(
      BuildContext context, {
      required WebCaptureRequest request,
      required WebSourceBrowserPort browserPort,
      required ValueChanged<WebCaptureSnapshot> onSnapshot,
      required ValueChanged<WebCaptureRuntimeStatus> onRuntimeStatus,
      required ValueChanged<WebCaptureSecurityException> onSecurityFailure,
      required ValueChanged<WebCaptureSecurityException> onCaptureFailure,
      WidgetBuilder? loadingBuilder,
      Widget Function(BuildContext context, WebCaptureRuntimeStatus status)?
      unavailableBuilder,
    });

/// A platform-owned bridge between the mounted WebView and the typed live
/// capture port.
///
/// [capture] creates one generation-scoped completion. [buildView] must be
/// mounted by the owning Flutter widget so the platform WebView can produce
/// that completion. The port never executes source-provided code, performs
/// network I/O itself, persists cookies or resolves playback.
final class InAppWebViewSourceLiveCapturePort implements SourceLiveCapturePort {
  InAppWebViewSourceLiveCapturePort({
    required this.browserPort,
    InAppWebViewCaptureWidgetBuilder? viewBuilder,
    this.onRuntimeStatus,
    this.onSecurityFailure,
  }) : _viewBuilder = viewBuilder ?? _defaultViewBuilder;

  final WebSourceBrowserPort browserPort;
  final InAppWebViewCaptureWidgetBuilder _viewBuilder;

  ValueChanged<WebCaptureRuntimeStatus>? onRuntimeStatus;
  ValueChanged<WebCaptureSecurityException>? onSecurityFailure;

  _PendingCapture? _pending;
  var _generation = 0;
  var _closed = false;

  bool get isClosed => _closed;

  @override
  Future<WebCaptureSnapshot> capture(SourceLiveCaptureRequest request) {
    if (_closed) {
      return Future<WebCaptureSnapshot>.error(
        const InAppWebViewSourceLiveCaptureException(
          'capture_closed',
          'The WebView capture port is closed.',
        ),
      );
    }

    final previous = _pending;
    if (previous != null && !previous.completed) {
      previous.completed = true;
      previous.completer.completeError(
        const InAppWebViewSourceLiveCaptureException(
          'capture_superseded',
          'The WebView capture was superseded.',
        ),
      );
    }

    final pending = _PendingCapture(
      generation: ++_generation,
      request: request,
    );
    _pending = pending;
    return pending.completer.future;
  }

  /// Builds the WebView for the current capture generation.
  ///
  /// A keyed subtree forces a fresh lower-level capture view whenever the
  /// port starts a new generation, so an old accumulator cannot receive the
  /// new request's callbacks.
  Widget buildView(
    BuildContext context, {
    WidgetBuilder? loadingBuilder,
    Widget Function(BuildContext context, WebCaptureRuntimeStatus status)?
    unavailableBuilder,
  }) {
    final pending = _pending;
    if (_closed || pending == null) {
      return const SizedBox.shrink();
    }

    final generation = pending.generation;
    late Widget child;
    try {
      child = _viewBuilder(
        context,
        request: pending.request.webCaptureRequest,
        browserPort: browserPort,
        onSnapshot: (snapshot) => _complete(generation, snapshot),
        onRuntimeStatus: (status) => _runtime(generation, status),
        onSecurityFailure: (error) => _security(generation, error),
        onCaptureFailure: (error) => _fail(generation, error),
        loadingBuilder: loadingBuilder,
        unavailableBuilder: unavailableBuilder,
      );
    } on Object {
      _fail(
        generation,
        WebCaptureSecurityException(
          'capture_view_build_failed',
          'The platform WebView capture surface could not be built.',
        ),
      );
      return const SizedBox.shrink();
    }
    return KeyedSubtree(
      key: ValueKey<String>('source-live-capture-$generation'),
      child: child,
    );
  }

  /// Updates observers without replacing the active capture generation.
  void updateObservers({
    ValueChanged<WebCaptureRuntimeStatus>? onRuntimeStatus,
    ValueChanged<WebCaptureSecurityException>? onSecurityFailure,
  }) {
    this.onRuntimeStatus = onRuntimeStatus;
    this.onSecurityFailure = onSecurityFailure;
  }

  /// Invalidates the current generation and completes it as an error.
  void close() {
    if (_closed) {
      return;
    }
    _closed = true;
    ++_generation;
    final pending = _pending;
    _pending = null;
    if (pending == null || pending.completed) {
      return;
    }
    pending.completed = true;
    pending.completer.completeError(
      const InAppWebViewSourceLiveCaptureException(
        'capture_closed',
        'The WebView capture port is closed.',
      ),
    );
  }

  void _runtime(int generation, WebCaptureRuntimeStatus status) {
    if (!_isCurrent(generation)) {
      return;
    }
    _notifyRuntimeStatus(status);
    if (!status.isAvailable) {
      _fail(
        generation,
        WebCaptureSecurityException(
          'capture_runtime_unavailable',
          'The platform WebView runtime is unavailable.',
        ),
      );
    }
  }

  void _security(int generation, WebCaptureSecurityException error) {
    if (!_isCurrent(generation)) {
      return;
    }
    _notifySecurityFailure(error);
  }

  void _complete(int generation, WebCaptureSnapshot snapshot) {
    final pending = _currentPending(generation);
    if (pending == null || pending.completed) {
      return;
    }
    pending.completed = true;
    pending.completer.complete(snapshot);
  }

  void _fail(int generation, WebCaptureSecurityException error) {
    final pending = _currentPending(generation);
    if (pending == null || pending.completed) {
      return;
    }
    pending.completed = true;
    pending.completer.completeError(
      InAppWebViewSourceLiveCaptureException(
        _failureCode(error.code),
        'The platform WebView could not complete capture.',
      ),
    );
  }

  _PendingCapture? _currentPending(int generation) {
    if (!_isCurrent(generation)) {
      return null;
    }
    return _pending;
  }

  bool _isCurrent(int generation) =>
      !_closed && _pending?.generation == generation;

  void _notifyRuntimeStatus(WebCaptureRuntimeStatus status) {
    try {
      onRuntimeStatus?.call(status);
    } on Object {
      // Observer failures must not strand the platform completion. The
      // capture result remains governed by the runtime status itself.
    }
  }

  void _notifySecurityFailure(WebCaptureSecurityException error) {
    try {
      onSecurityFailure?.call(error);
    } on Object {
      // A diagnostic observer cannot change the capture admission outcome.
    }
  }
}

final class InAppWebViewSourceLiveCaptureException
    implements SourceLiveCaptureFailure {
  const InAppWebViewSourceLiveCaptureException(this.code, this.message);

  @override
  final String code;
  final String message;

  @override
  String toString() =>
      'InAppWebViewSourceLiveCaptureException($code): <redacted>';
}

final class _PendingCapture {
  _PendingCapture({required this.generation, required this.request});

  final int generation;
  final SourceLiveCaptureRequest request;
  final completer = Completer<WebCaptureSnapshot>();
  var completed = false;
}

String _failureCode(String code) {
  if (RegExp(r'^[a-z0-9_]{1,64}$').hasMatch(code)) {
    return code;
  }
  return 'capture_failed';
}

Widget _defaultViewBuilder(
  BuildContext _, {
  required WebCaptureRequest request,
  required WebSourceBrowserPort browserPort,
  required ValueChanged<WebCaptureSnapshot> onSnapshot,
  required ValueChanged<WebCaptureRuntimeStatus> onRuntimeStatus,
  required ValueChanged<WebCaptureSecurityException> onSecurityFailure,
  required ValueChanged<WebCaptureSecurityException> onCaptureFailure,
  WidgetBuilder? loadingBuilder,
  Widget Function(BuildContext context, WebCaptureRuntimeStatus status)?
  unavailableBuilder,
}) => InAppWebViewCaptureView(
  request: request,
  browserPort: browserPort,
  onSnapshot: onSnapshot,
  onRuntimeStatus: onRuntimeStatus,
  onSecurityFailure: onSecurityFailure,
  onCaptureFailure: onCaptureFailure,
  loadingBuilder: loadingBuilder,
  unavailableBuilder: unavailableBuilder,
);
