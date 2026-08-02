import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/infrastructure/playback/proxy_upstream_client.dart';

void main() {
  test('rejects non-public DNS before opening a socket', () async {
    final resolver = _StaticAddressResolver([InternetAddress.loopbackIPv4]);
    final client = DartIoProxyUpstreamClient(addressResolver: resolver);
    addTearDown(client.close);

    await expectLater(
      client.send(_request()),
      throwsA(
        isA<ProxyUpstreamSecurityException>()
            .having(
              (error) => error.code,
              'code',
              'upstream_address_not_public',
            )
            .having(
              (error) => error.toString(),
              'diagnostic',
              isNot(contains('media.example')),
            ),
      ),
    );
    expect(resolver.hosts, ['media.example']);
  });

  test(
    'well-known NAT64 addresses embedding private IPv4 fail closed',
    () async {
      final resolver = _StaticAddressResolver([
        InternetAddress('64:ff9b::c0a8:010a'),
      ]);
      final client = DartIoProxyUpstreamClient(addressResolver: resolver);
      addTearDown(client.close);

      await expectLater(
        client.send(_request()),
        throwsA(
          isA<ProxyUpstreamSecurityException>().having(
            (error) => error.code,
            'code',
            'upstream_address_not_public',
          ),
        ),
      );
    },
  );

  test('IPv4-mapped private IPv6 DNS answers also fail closed', () async {
    final resolver = _StaticAddressResolver([
      InternetAddress('::ffff:192.168.1.10'),
    ]);
    final client = DartIoProxyUpstreamClient(addressResolver: resolver);
    addTearDown(client.close);

    await expectLater(
      client.send(_request()),
      throwsA(
        isA<ProxyUpstreamSecurityException>().having(
          (error) => error.code,
          'code',
          'upstream_address_not_public',
        ),
      ),
    );
  });
}

ProxyUpstreamRequest _request() => ProxyUpstreamRequest(
  uri: Uri.parse('https://media.example/master.m3u8'),
  method: 'GET',
  headers: const {},
  timeout: const Duration(seconds: 2),
);

final class _StaticAddressResolver implements UpstreamAddressResolver {
  _StaticAddressResolver(this.addresses);

  final List<InternetAddress> addresses;
  final List<String> hosts = [];

  @override
  Future<List<InternetAddress>> lookup(String host) async {
    hosts.add(host);
    return addresses;
  }
}
