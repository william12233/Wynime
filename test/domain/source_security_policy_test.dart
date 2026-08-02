import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test('subdomain allowlist uses a dot boundary', () {
    final policy = testSourcePolicy();

    expect(
      policy.allowsUri(Uri.parse('https://api.example.com/search')),
      isTrue,
    );
    expect(policy.allowsUri(Uri.parse('https://example.com/search')), isTrue);
    expect(
      policy.allowsUri(Uri.parse('https://evil-example.com/search')),
      isFalse,
    );
    expect(
      policy.allowsUri(Uri.parse('https://user@example.com/search')),
      isFalse,
    );
  });

  test('schema version 1 rejects non-standard ports', () {
    final policy = testSourcePolicy();

    expect(policy.allowsUri(Uri.parse('https://example.com:443/path')), isTrue);
    expect(
      policy.allowsUri(Uri.parse('https://example.com:8443/path')),
      isFalse,
    );
  });

  test('network permission is mandatory even for an allowed domain', () {
    final policy = testSourcePolicy(permissions: const {});

    expect(policy.allowsUri(Uri.parse('https://example.com')), isFalse);
  });

  test('http rules require the explicit insecureHttp permission', () {
    expect(
      () => testSourcePolicy(
        domains: [
          SourceDomainRule(host: 'example.com', schemes: {'http'}),
        ],
      ),
      throwsArgumentError,
    );

    final policy = testSourcePolicy(
      domains: [
        SourceDomainRule(host: 'example.com', schemes: {'http'}),
      ],
      permissions: {SourcePermission.network, SourcePermission.insecureHttp},
    );
    expect(policy.allowsUri(Uri.parse('http://example.com')), isTrue);
    expect(policy.allowsUri(Uri.parse('http://example.com:80')), isTrue);
    expect(policy.allowsUri(Uri.parse('http://example.com:8080')), isFalse);
  });

  test('authority expansion requires fresh consent', () {
    final original = testSourcePolicy();
    final addedPermission = testSourcePolicy(
      permissions: {SourcePermission.network, SourcePermission.cookies},
    );
    final addedDomain = testSourcePolicy(
      domains: [
        SourceDomainRule(host: 'example.com', includeSubdomains: true),
        SourceDomainRule(host: 'other.example'),
      ],
    );
    final broaderBudget = testSourcePolicy(
      budget: testSourceBudget(maxRecords: 21),
    );
    final narrowerDomain = testSourcePolicy(
      domains: [SourceDomainRule(host: 'api.example.com')],
    );

    expect(addedPermission.requiresReconsentComparedTo(original), isTrue);
    expect(addedDomain.requiresReconsentComparedTo(original), isTrue);
    expect(broaderBudget.requiresReconsentComparedTo(original), isTrue);
    expect(narrowerDomain.requiresReconsentComparedTo(original), isFalse);
  });
}
