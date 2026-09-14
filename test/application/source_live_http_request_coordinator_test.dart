import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test('ready result retains exact package identity and request', () {
    final plan = _plan();
    final result = _coordinator().prepare(plan);

    expect(result.status, SourceLiveHttpRequestStatus.ready);
    expect(result.request, same(plan.request));
    expect(result.packageId, 'example.anime');
    expect(result.packageVersion, Version.parse('1.0.0'));
    expect(result.programId, 'search');
    expect(result.reasonCode, isNull);
  });

  test('consent and re-consent are checked before disabled state', () {
    final result = _coordinator().prepare(
      _plan(
        installedPackage: _installed(
          status: SourcePackageStatus.disabled,
          requiresReconsent: true,
        ),
      ),
    );

    expect(result.status, SourceLiveHttpRequestStatus.consentRequired);
    expect(result.reasonCode, 'consent_required');
    expect(result.request, isNull);
  });

  test('disabled and incompatible packages are rejected without a request', () {
    final disabled = _coordinator().prepare(
      _plan(installedPackage: _installed(status: SourcePackageStatus.disabled)),
    );
    expect(disabled.status, SourceLiveHttpRequestStatus.disabled);
    expect(disabled.reasonCode, 'package_disabled');

    final incompatiblePackage = _package(
      constraint: VersionConstraint.parse('^2.0.0'),
    );
    final incompatible = _coordinator().prepare(
      _plan(installedPackage: _installed(package: incompatiblePackage)),
    );
    expect(incompatible.status, SourceLiveHttpRequestStatus.incompatible);
    expect(incompatible.reasonCode, 'incompatible_wynime_version');
  });

  test('unknown program is rejected before policy handoff', () {
    final result = _coordinator().prepare(_plan(programId: 'episodes'));

    expect(result.status, SourceLiveHttpRequestStatus.programNotFound);
    expect(result.reasonCode, 'program_not_found');
    expect(result.request, isNull);
  });

  test('different effective request policy is rejected', () {
    final packagePolicy = testSourcePolicy();
    final requestPolicy = testSourcePolicy(
      domains: [
        SourceDomainRule(host: 'example.com', includeSubdomains: true),
        SourceDomainRule(host: 'other.example', includeSubdomains: true),
      ],
    );
    final result = _coordinator().prepare(
      _plan(
        installedPackage: _installed(package: _package(policy: packagePolicy)),
        request: _request(policy: requestPolicy),
      ),
    );

    expect(result.status, SourceLiveHttpRequestStatus.invalidRequest);
    expect(result.reasonCode, 'request_policy_mismatch');
    expect(result.toString(), isNot(contains('other.example')));
  });

  test(
    'invalid request URI is diagnosed if a request crosses the boundary',
    () {
      final packagePolicy = testSourcePolicy();
      final requestPolicy = testSourcePolicy(
        domains: [
          SourceDomainRule(host: 'other.example', includeSubdomains: true),
        ],
      );
      final request = _request(
        policy: requestPolicy,
        uri: 'https://other.example/search',
      );
      final result = _coordinator().prepare(
        _plan(
          installedPackage: _installed(
            package: _package(policy: packagePolicy),
          ),
          request: request,
        ),
      );

      expect(result.status, SourceLiveHttpRequestStatus.invalidRequest);
      expect(result.reasonCode, 'request_policy_mismatch');
      expect(result.request, isNull);
    },
  );

  test('result constructor keeps rejected diagnostics bounded', () {
    final result = SourceLiveHttpRequestResult(
      packageId: 'example.anime',
      packageVersion: Version.parse('1.0.0'),
      programId: 'search',
      status: SourceLiveHttpRequestStatus.failed,
      reasonCode: 'package_preflight_failed',
    );
    expect(result.toString(), contains('packageId'));
    expect(result.toString(), isNot(contains('https://')));
  });
}

SourceLiveHttpRequestCoordinator _coordinator() =>
    SourceLiveHttpRequestCoordinator(wynimeVersion: Version.parse('1.0.0'));

SourceLiveHttpRequestPlan _plan({
  String programId = 'search',
  InstalledSourcePackage? installedPackage,
  SourceHttpRequest? request,
}) {
  final policy = testSourcePolicy();
  return SourceLiveHttpRequestPlan(
    installedPackage: installedPackage ?? _installed(policy: policy),
    programId: programId,
    request: request ?? _request(policy: policy),
  );
}

SourceHttpRequest _request({
  required SourceSecurityPolicy policy,
  String uri = 'https://example.com/search?q=secret',
}) => SourceHttpRequest(
  uri: Uri.parse(uri),
  securityPolicy: policy,
  headers: const {'accept': 'text/html'},
);

InstalledSourcePackage _installed({
  SourcePackageManifest? package,
  SourceSecurityPolicy? policy,
  SourcePackageStatus status = SourcePackageStatus.enabled,
  bool requiresConsent = false,
  bool requiresReconsent = false,
}) => InstalledSourcePackage(
  package: package ?? _package(policy: policy),
  status: status,
  requiresConsent: requiresConsent,
  requiresReconsent: requiresReconsent,
);

SourcePackageManifest _package({
  SourceSecurityPolicy? policy,
  VersionConstraint? constraint,
}) => SourcePackageManifest(
  schemaVersion: 1,
  packageId: 'example.anime',
  displayName: 'Example Anime',
  version: Version.parse('1.0.0'),
  wynimeVersionConstraint: constraint ?? VersionConstraint.parse('^1.0.0'),
  securityPolicy: policy ?? testSourcePolicy(),
  programs: [
    SourceRuleProgram(
      programId: 'search',
      documentKind: SourceDocumentKind.html,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.css,
        expression: '.item',
      ),
      fields: [SourceFieldRule(name: 'title', valueKind: SourceValueKind.raw)],
      resultLimit: 1,
    ),
  ],
);
