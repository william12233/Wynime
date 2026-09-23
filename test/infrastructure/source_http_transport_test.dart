import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/infrastructure/playback/proxy_upstream_client.dart';
import 'package:wynime/src/infrastructure/source_http/dart_io_source_http_transport.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test(
    'success sends one bounded GET and returns only a text response',
    () async {
      final upstream = _RecordingUpstream(
        responses: [
          _response(
            statusCode: 200,
            headers: const {
              'content-type': ['text/html; charset=utf-8'],
              'set-cookie': ['secret-cookie=hidden'],
            },
            body: '<html>ok</html>',
          ),
        ],
      );
      final request = _request(headers: const {'accept': 'text/html'});
      final result = await DartIoSourceHttpTransport(
        upstreamClient: upstream,
      ).send(request);

      expect(result.status, SourceHttpTransportStatus.success);
      expect(result.response!.body, '<html>ok</html>');
      expect(result.response!.contentType, 'text/html; charset=utf-8');
      expect(result.response!.redirectChain, isEmpty);
      expect(upstream.requests, hasLength(1));
      expect(upstream.requests.single.method, 'GET');
      expect(upstream.requests.single.uri, request.uri);
      expect(upstream.requests.single.headers['accept'], 'text/html');
      expect(result.toString(), isNot(contains('secret-cookie')));
    },
  );

  test('allowlisted redirects are followed and bounded', () async {
    final upstream = _RecordingUpstream(
      responses: [
        _response(
          statusCode: 302,
          headers: const {
            'location': ['/episode'],
          },
          body: 'redirect body',
        ),
        _response(statusCode: 200, body: 'episode'),
      ],
    );
    final result = await DartIoSourceHttpTransport(
      upstreamClient: upstream,
    ).send(_request());

    expect(result.status, SourceHttpTransportStatus.success);
    expect(result.response!.body, 'episode');
    expect(result.response!.finalUri, Uri.parse('https://example.com/episode'));
    expect(result.response!.redirectChain, [
      Uri.parse('https://example.com/episode'),
    ]);
    expect(upstream.requests, hasLength(2));
  });

  test(
    'redirect outside the package allowlist fails before the second request',
    () async {
      final upstream = _RecordingUpstream(
        responses: [
          _response(
            statusCode: 302,
            headers: const {
              'location': ['https://other.example/episode'],
            },
          ),
        ],
      );
      final result = await DartIoSourceHttpTransport(
        upstreamClient: upstream,
      ).send(_request());

      expect(result.status, SourceHttpTransportStatus.redirectUriNotAllowed);
      expect(result.reasonCode, 'redirect_uri_not_allowed');
      expect(result.response, isNull);
      expect(
        result.responseEvidence?.toRedactedDiagnostic()['finalHost'],
        'other.example',
      );
      expect(
        result.responseEvidence?.toRedactedDiagnostic()['finalPath'],
        '/episode',
      );
      expect(upstream.requests, hasLength(1));
    },
  );

  test('redirect evidence redacts a non-standard rejected port', () async {
    final upstream = _RecordingUpstream(
      responses: [
        _response(
          statusCode: 302,
          headers: const {
            'location': ['https://other.example:8443/episode?token=secret'],
          },
        ),
      ],
    );
    final result = await DartIoSourceHttpTransport(
      upstreamClient: upstream,
    ).send(_request());

    expect(result.status, SourceHttpTransportStatus.redirectUriNotAllowed);
    expect(
      result.responseEvidence?.toRedactedDiagnostic()['finalHost'],
      'other.example',
    );
    expect(
      result.responseEvidence?.toRedactedDiagnostic()['finalPath'],
      '/episode',
    );
    expect(result.responseEvidence?.toRedactedDiagnostic()['finalPort'], 8443);
    expect(result.toString(), isNot(contains('secret')));
  });

  test('redirect budget and missing location are truthful failures', () async {
    final oneRedirectPolicy = testSourcePolicy(
      budget: testSourceBudget(maxRedirects: 0),
    );
    final budgetUpstream = _RecordingUpstream(
      responses: [
        _response(
          statusCode: 302,
          headers: const {
            'location': ['/episode'],
          },
        ),
      ],
    );
    final budgetResult = await DartIoSourceHttpTransport(
      upstreamClient: budgetUpstream,
    ).send(_request(policy: oneRedirectPolicy));
    expect(
      budgetResult.status,
      SourceHttpTransportStatus.redirectBudgetExceeded,
    );
    expect(budgetResult.reasonCode, 'redirect_budget_exceeded');

    final missingLocation = _RecordingUpstream(
      responses: [_response(statusCode: 302)],
    );
    final missingResult = await DartIoSourceHttpTransport(
      upstreamClient: missingLocation,
    ).send(_request());
    expect(missingResult.status, SourceHttpTransportStatus.invalidResponse);
    expect(missingResult.reasonCode, 'redirect_location_missing');
  });

  test('response byte budget is enforced before text exposure', () async {
    final policy = testSourcePolicy(
      budget: testSourceBudget(maxDocumentBytes: 3),
    );
    final upstream = _RecordingUpstream(
      responses: [_response(statusCode: 200, body: 'four')],
    );
    final result = await DartIoSourceHttpTransport(
      upstreamClient: upstream,
    ).send(_request(policy: policy));

    expect(result.status, SourceHttpTransportStatus.responseTooLarge);
    expect(result.reasonCode, 'response_too_large');
    expect(result.response, isNull);
  });

  test(
    'non-success HTTP and malformed UTF-8 never expose response bodies',
    () async {
      final httpErrorUpstream = _RecordingUpstream(
        responses: [_response(statusCode: 503, body: 'upstream secret error')],
      );
      final httpError = await DartIoSourceHttpTransport(
        upstreamClient: httpErrorUpstream,
      ).send(_request());
      expect(httpError.status, SourceHttpTransportStatus.httpError);
      expect(httpError.httpStatus, 503);
      expect(httpError.response, isNull);
      expect(
        httpError.responseEvidence?.toRedactedDiagnostic()['finalHost'],
        'example.com',
      );
      expect(httpError.toString(), isNot(contains('upstream secret')));

      final malformedUpstream = _RecordingUpstream(
        responses: [
          _response(statusCode: 200, bodyBytes: const [0xc3, 0x28]),
        ],
      );
      final malformed = await DartIoSourceHttpTransport(
        upstreamClient: malformedUpstream,
      ).send(_request());
      expect(malformed.status, SourceHttpTransportStatus.invalidResponse);
      expect(malformed.reasonCode, 'response_not_utf8');
      expect(malformed.response, isNull);
    },
  );

  test('upstream, timeout and close failures remain typed', () async {
    final security = _RecordingUpstream(
      error: const ProxyUpstreamSecurityException(
        'upstream_address_not_public',
        'private address detail',
      ),
    );
    final securityResult = await DartIoSourceHttpTransport(
      upstreamClient: security,
    ).send(_request());
    expect(securityResult.status, SourceHttpTransportStatus.policyRejected);
    expect(securityResult.reasonCode, 'upstream_address_not_public');
    expect(securityResult.toString(), isNot(contains('private address')));

    final timeout = _RecordingUpstream(
      error: TimeoutException('secret timeout'),
    );
    final timeoutResult = await DartIoSourceHttpTransport(
      upstreamClient: timeout,
    ).send(_request());
    expect(timeoutResult.status, SourceHttpTransportStatus.timeout);
    expect(timeoutResult.reasonCode, 'request_timeout');

    final transport = DartIoSourceHttpTransport(
      upstreamClient: _RecordingUpstream(),
    );
    await transport.close();
    final closed = await transport.send(_request());
    expect(closed.status, SourceHttpTransportStatus.closed);
    expect(closed.reasonCode, 'transport_closed');
  });

  test('close wins when upstream failure completes after close', () async {
    final upstream = _DeferredUpstream();
    final transport = DartIoSourceHttpTransport(upstreamClient: upstream);
    final pending = transport.send(_request());

    await upstream.started.future;
    await transport.close();
    upstream.result.completeError(const SocketException('late network error'));

    final result = await pending;
    expect(result.status, SourceHttpTransportStatus.closed);
    expect(result.reasonCode, 'transport_closed');
  });

  test('close wins after an error response body is discarded', () async {
    final listening = Completer<void>();
    final body = StreamController<List<int>>(
      onListen: () {
        listening.complete();
      },
    );
    final upstream = _RecordingUpstream(
      responses: [
        ProxyUpstreamResponse(
          statusCode: 503,
          headers: const {},
          body: body.stream,
        ),
      ],
    );
    final transport = DartIoSourceHttpTransport(upstreamClient: upstream);
    final pending = transport.send(_request());

    await upstream.started.future;
    await listening.future;
    await transport.close();
    body.add(const []);
    await body.close();

    final result = await pending;
    expect(result.status, SourceHttpTransportStatus.closed);
    expect(result.reasonCode, 'transport_closed');
  });
}

SourceHttpRequest _request({
  SourceSecurityPolicy? policy,
  Map<String, String> headers = const {},
}) => SourceHttpRequest(
  uri: Uri.parse('https://example.com/search?q=secret'),
  securityPolicy: policy ?? testSourcePolicy(),
  headers: headers,
  timeout: const Duration(seconds: 2),
);

ProxyUpstreamResponse _response({
  required int statusCode,
  Map<String, List<String>> headers = const {},
  String body = '',
  List<int>? bodyBytes,
}) => ProxyUpstreamResponse(
  statusCode: statusCode,
  headers: headers,
  body: Stream<List<int>>.value(bodyBytes ?? body.codeUnits),
);

final class _RecordingUpstream implements ProxyUpstreamClient {
  _RecordingUpstream({this._responses = const [], this.error});

  final List<ProxyUpstreamResponse> _responses;
  final Object? error;
  final requests = <ProxyUpstreamRequest>[];
  final started = Completer<void>();
  var _index = 0;
  var closeCount = 0;

  @override
  Future<ProxyUpstreamResponse> send(ProxyUpstreamRequest request) async {
    requests.add(request);
    if (!started.isCompleted) {
      started.complete();
    }
    final failure = error;
    if (failure != null) {
      return Future<ProxyUpstreamResponse>.error(failure);
    }
    if (_index >= _responses.length) {
      return Future<ProxyUpstreamResponse>.error(
        StateError('missing test response'),
      );
    }
    return _responses[_index++];
  }

  @override
  Future<void> close() async {
    closeCount++;
  }
}

final class _DeferredUpstream implements ProxyUpstreamClient {
  final started = Completer<void>();
  final result = Completer<ProxyUpstreamResponse>();

  @override
  Future<ProxyUpstreamResponse> send(ProxyUpstreamRequest request) {
    if (!started.isCompleted) {
      started.complete();
    }
    return result.future;
  }

  @override
  Future<void> close() async {}
}
