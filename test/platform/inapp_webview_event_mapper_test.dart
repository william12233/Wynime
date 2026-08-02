import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/platform/web_capture/inapp_webview_event_mapper.dart';

void main() {
  const mapper = InAppWebViewEventMapper();

  test('maps main-frame and iframe navigation without plugin leakage', () {
    final main = mapper.navigation(
      0,
      NavigationAction(
        request: URLRequest(
          url: WebUri('https://example.com/watch'),
          method: 'GET',
          headers: {'Referer': 'https://example.com/'},
        ),
        isForMainFrame: true,
        isRedirect: false,
      ),
    );
    final frame = mapper.navigation(
      1,
      NavigationAction(
        request: URLRequest(url: WebUri('https://cdn.example.com/frame')),
        isForMainFrame: false,
      ),
    );

    expect(main?.kind, WebRequestKind.navigation);
    expect(main?.headers['referer'], 'https://example.com/');
    expect(frame?.kind, WebRequestKind.iframe);
  });

  test('maps resource, XHR, and fetch requests into typed events', () {
    final resource = mapper.resource(
      0,
      WebResourceRequest(
        url: WebUri('https://cdn.example.com/master.m3u8'),
        method: 'GET',
        headers: {'Content-Type': 'application/vnd.apple.mpegurl'},
        isForMainFrame: false,
        isRedirect: false,
      ),
    );
    final ajax = mapper.ajax(
      1,
      AjaxRequest(
        url: WebUri('https://api.example.com/episodes'),
        method: 'POST',
        headers: AjaxRequestHeaders({'X-Token': 'secret'}),
      ),
    );
    final fetch = mapper.fetch(
      2,
      FetchRequest(
        url: WebUri('https://api.example.com/manifest'),
        method: 'GET',
        headers: {'Accept': 'application/json', 'ignored': <String>[]},
      ),
    );

    expect(resource?.kind, WebRequestKind.resource);
    expect(ajax?.kind, WebRequestKind.xmlHttpRequest);
    expect(ajax?.headers['x-token'], 'secret');
    expect(fetch?.kind, WebRequestKind.fetch);
    expect(fetch?.headers, {'accept': 'application/json'});
  });

  test('rejects non-HTTP and malformed plugin request values', () {
    final dataRequest = mapper.resource(
      0,
      WebResourceRequest(url: WebUri('data:text/plain,blocked')),
    );
    final invalidMethod = mapper.resource(
      1,
      WebResourceRequest(
        url: WebUri('https://example.com/resource'),
        method: 'GET\nX-Injected: true',
      ),
    );

    expect(dataRequest, isNull);
    expect(invalidMethod, isNull);
  });
}
