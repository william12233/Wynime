import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import '../../domain/models/web_capture_models.dart';

final class InAppWebViewEventMapper {
  const InAppWebViewEventMapper();

  WebCaptureEvent? navigation(int sequence, NavigationAction action) {
    final request = action.request;
    final url = request.url;
    if (url == null) {
      return null;
    }
    return _map(
      sequence: sequence,
      kind: action.isForMainFrame
          ? WebRequestKind.navigation
          : WebRequestKind.iframe,
      url: url,
      method: request.method,
      headers: request.headers,
      isMainFrame: action.isForMainFrame,
      isRedirect: action.isRedirect ?? false,
    );
  }

  WebCaptureEvent? resource(int sequence, WebResourceRequest request) {
    return _map(
      sequence: sequence,
      kind: request.isForMainFrame == true
          ? WebRequestKind.navigation
          : WebRequestKind.resource,
      url: request.url,
      method: request.method,
      headers: request.headers,
      isMainFrame: request.isForMainFrame ?? false,
      isRedirect: request.isRedirect ?? false,
    );
  }

  WebCaptureEvent? ajax(int sequence, AjaxRequest request) {
    final url = request.url;
    if (url == null) {
      return null;
    }
    return _map(
      sequence: sequence,
      kind: WebRequestKind.xmlHttpRequest,
      url: url,
      method: request.method,
      headers: _stringHeaders(request.headers?.getHeaders()),
    );
  }

  WebCaptureEvent? fetch(int sequence, FetchRequest request) {
    final url = request.url;
    if (url == null) {
      return null;
    }
    return _map(
      sequence: sequence,
      kind: WebRequestKind.fetch,
      url: url,
      method: request.method,
      headers: _stringHeaders(request.headers),
    );
  }

  static WebCaptureEvent? _map({
    required int sequence,
    required WebRequestKind kind,
    required WebUri url,
    String? method,
    Map<String, String>? headers,
    bool isMainFrame = false,
    bool isRedirect = false,
  }) {
    final uri = Uri.tryParse(url.toString());
    if (uri == null) {
      return null;
    }
    try {
      return WebCaptureEvent(
        sequence: sequence,
        kind: kind,
        uri: uri,
        method: method ?? 'GET',
        headers: headers ?? const {},
        isMainFrame: isMainFrame,
        isRedirect: isRedirect,
      );
    } on ArgumentError {
      return null;
    } on FormatException {
      return null;
    }
  }

  static Map<String, String> _stringHeaders(Map<String, dynamic>? values) {
    if (values == null || values.isEmpty) {
      return const {};
    }
    final result = <String, String>{};
    for (final entry in values.entries) {
      final value = entry.value;
      if (value is String || value is num || value is bool) {
        result[entry.key] = value.toString();
      }
    }
    return result;
  }
}
