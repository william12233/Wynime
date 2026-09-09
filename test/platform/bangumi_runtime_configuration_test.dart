import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/platform/bangumi/bangumi_runtime_configuration.dart';

void main() {
  test('an absent broker origin keeps Bangumi unavailable', () {
    final configuration = BangumiRuntimeConfiguration.fromRawOrigin('');

    expect(configuration.isConfigured, isFalse);
    expect(configuration.brokerOrigin, isNull);
    expect(configuration.verifiedAppLinkHost, isNull);
  });

  test('uses one valid HTTPS origin for the worker and app link host', () {
    final configuration = BangumiRuntimeConfiguration.fromRawOrigin(
      'https://Wynime-Broker-Test.example.workers.dev/',
    );

    expect(configuration.isConfigured, isTrue);
    expect(
      configuration.brokerOrigin,
      Uri.parse('https://wynime-broker-test.example.workers.dev'),
    );
    expect(
      configuration.verifiedAppLinkHost,
      'wynime-broker-test.example.workers.dev',
    );
  });

  test('rejects malformed, non-HTTPS, and non-origin values', () {
    for (final value in <String>[
      'https://',
      'http://wynime-broker.example.workers.dev',
      'https://user:password@wynime-broker.example.workers.dev',
      'https://wynime-broker.example.workers.dev:8443',
      'https://wynime-broker.example.workers.dev/oauth/start',
      'https://wynime-broker.example.workers.dev?token=secret',
      'https://wynime-broker.example.workers.dev#callback',
      'https://wynime_broker.example.workers.dev',
    ]) {
      expect(
        BangumiRuntimeConfiguration.fromRawOrigin(value).isConfigured,
        isFalse,
        reason: value,
      );
    }
  });
}
