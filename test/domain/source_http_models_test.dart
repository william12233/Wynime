import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test('request freezes safe headers and redacts URL and header values', () {
    final request = SourceHttpRequest(
      uri: Uri.parse('https://example.com/search?q=secret'),
      securityPolicy: testSourcePolicy(),
      headers: const {'Accept': 'text/html', 'X-Trace': 'private-value'},
    );

    expect(request.method, SourceHttpMethod.get);
    expect(request.headers['accept'], 'text/html');
    expect(request.toString(), contains('example.com'));
    expect(request.toString(), isNot(contains('/search')));
    expect(request.toString(), isNot(contains('secret')));
    expect(request.toString(), isNot(contains('private-value')));
  });

  test('request rejects fragments, disallowed URI and forbidden headers', () {
    expect(
      () => SourceHttpRequest(
        uri: Uri.parse('https://example.com/search#fragment'),
        securityPolicy: testSourcePolicy(),
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpRequest(
        uri: Uri.parse('https://other.example/search'),
        securityPolicy: testSourcePolicy(),
      ),
      throwsArgumentError,
    );
    for (final name in const [
      'Authorization',
      'Cookie',
      'Host',
      'Content-Length',
      'Transfer-Encoding',
    ]) {
      expect(
        () => SourceHttpRequest(
          uri: Uri.parse('https://example.com/search'),
          securityPolicy: testSourcePolicy(),
          headers: {name: 'value'},
        ),
        throwsArgumentError,
        reason: name,
      );
    }
  });

  test('request rejects a body, invalid headers and invalid timeout', () {
    final uri = Uri.parse('https://example.com/search');
    final policy = testSourcePolicy();
    expect(
      () => SourceHttpRequest(
        uri: uri,
        securityPolicy: policy,
        bodyBytes: const [1],
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpRequest(
        uri: uri,
        securityPolicy: policy,
        headers: const {'bad name': 'value'},
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpRequest(
        uri: uri,
        securityPolicy: policy,
        headers: const {'x-test': 'line\nfeed'},
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpRequest(
        uri: uri,
        securityPolicy: policy,
        timeout: Duration(milliseconds: 999),
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpRequest(
        uri: uri,
        securityPolicy: policy,
        timeout: const Duration(seconds: 121),
      ),
      throwsArgumentError,
    );
  });

  test('request enforces bounded header and body byte values', () {
    final uri = Uri.parse('https://example.com/search');
    final policy = testSourcePolicy();
    final hugeHeader = List<String>.filled(70 * 1024, 'x').join();
    expect(
      () => SourceHttpRequest(
        uri: uri,
        securityPolicy: policy,
        headers: {'x-huge': hugeHeader},
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpRequest(
        uri: uri,
        securityPolicy: policy,
        bodyBytes: List<int>.filled(SourceHttpRequest.maxBodyBytes + 1, 0),
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpRequest(
        uri: uri,
        securityPolicy: policy,
        bodyBytes: const [256],
      ),
      throwsArgumentError,
    );
  });

  test('response and result constructors preserve truthful invariants', () {
    final response = SourceHttpResponse(
      statusCode: 200,
      finalUri: Uri.parse('https://example.com/search'),
      redirectChain: const [],
      body: '<html></html>',
      contentType: 'text/html; charset=utf-8',
    );
    final success = SourceHttpTransportResult(
      status: SourceHttpTransportStatus.success,
      response: response,
    );
    expect(success.response, same(response));
    expect(success.toString(), contains('responseBodyBytes'));
    expect(success.toString(), isNot(contains('<html>')));

    expect(
      () => SourceHttpTransportResult(
        status: SourceHttpTransportStatus.success,
        reasonCode: 'success',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpTransportResult(
        status: SourceHttpTransportStatus.networkError,
        reasonCode: 'raw error with spaces',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpTransportResult(
        status: SourceHttpTransportStatus.networkError,
        reasonCode: 'network_error',
        httpStatus: 503,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpResponse(
        statusCode: 302,
        finalUri: Uri.parse('https://example.com/search'),
        redirectChain: const [],
        body: '',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpResponse(
        statusCode: 200,
        finalUri: Uri.parse('ftp://example.com/document'),
        redirectChain: const [],
        body: 'ok',
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceHttpResponse(
        statusCode: 200,
        finalUri: Uri.parse('https://example.com/document'),
        redirectChain: [Uri.parse('https://example.com:444/document')],
        body: 'ok',
      ),
      throwsArgumentError,
    );
  });

  test('HTTP model does not broaden an HTTP-only policy silently', () {
    expect(
      () => SourceHttpRequest(
        uri: Uri.parse('http://example.com/search'),
        securityPolicy: testSourcePolicy(),
      ),
      throwsArgumentError,
    );
  });
}
