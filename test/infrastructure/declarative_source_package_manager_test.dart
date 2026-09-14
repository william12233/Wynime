import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_builder_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_builder.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  late DeclarativeSourcePackageManager manager;

  setUp(() {
    manager = DeclarativeSourcePackageManager(
      wynimeVersion: Version.parse('1.5.0'),
    );
  });

  test('install is disabled until explicit approval, then can be toggled', () {
    final package = testPackage();

    final installed = manager.install(package);

    expect(installed.status, SourcePackageStatus.disabled);
    expect(installed.requiresConsent, isTrue);
    expect(installed.requiresReconsent, isFalse);
    expect(manager.enabledPackages, isEmpty);

    expectManagerCode(
      () => manager.enable(
        packageId: package.packageId,
        version: package.version,
        userApproved: false,
        reconsentGranted: false,
      ),
      'user_approval_required',
    );

    final enabled = manager.enable(
      packageId: package.packageId,
      version: package.version,
      userApproved: true,
      reconsentGranted: false,
    );
    expect(enabled.status, SourcePackageStatus.enabled);
    expect(enabled.requiresConsent, isFalse);
    expect(manager.enabledPackages.single.package.packageId, 'example.anime');

    final disabled = manager.disable(
      packageId: package.packageId,
      version: package.version,
    );
    expect(disabled.status, SourcePackageStatus.disabled);
    expect(manager.enabledPackages, isEmpty);

    // Existing consent remains valid after a user toggles the package off.
    expect(
      manager
          .enable(
            packageId: package.packageId,
            version: package.version,
            userApproved: false,
            reconsentGranted: false,
          )
          .status,
      SourcePackageStatus.enabled,
    );
  });

  test('incompatible and duplicate installs fail without changing state', () {
    final incompatible = testPackage(
      packageId: 'future.anime',
      version: '2.0.0',
      wynimeVersion: '^2.0.0',
    );

    expectManagerCode(
      () => manager.install(incompatible),
      'incompatible_wynime_version',
    );
    expect(manager.installedPackages, isEmpty);

    final package = testPackage();
    manager.install(package);

    expectManagerCode(() => manager.install(package), 'already_installed');
    expect(manager.installedPackages, hasLength(1));
    expect(manager.installedPackages.single.package.version, package.version);
  });

  test(
    'update requires a newer compatible version and replaces atomically',
    () {
      final original = testPackage();
      manager.install(original);
      manager.enable(
        packageId: original.packageId,
        version: original.version,
        userApproved: true,
        reconsentGranted: false,
      );

      expectManagerCode(
        () => manager.update(testPackage(version: '1.0.0')),
        'version_not_newer',
      );
      expect(manager.enabledPackages.single.package.version, original.version);

      expectManagerCode(
        () => manager.update(
          testPackage(version: '2.0.0', wynimeVersion: '^2.0.0'),
        ),
        'incompatible_wynime_version',
      );
      expect(manager.enabledPackages.single.package.version, original.version);

      final updated = manager.update(testPackage(version: '1.1.0'));

      expect(updated.status, SourcePackageStatus.disabled);
      expect(updated.requiresConsent, isTrue);
      expect(manager.enabledPackages, isEmpty);
      expect(
        manager.installedPackages.single.package.version,
        Version.parse('1.1.0'),
      );

      // A stale UI action cannot mutate the replacement package.
      expectManagerCode(
        () => manager.enable(
          packageId: original.packageId,
          version: original.version,
          userApproved: true,
          reconsentGranted: false,
        ),
        'package_version_mismatch',
      );
    },
  );

  test('broader update policy remains disabled until re-consent', () {
    final original = testPackage();
    manager.install(original);

    final broader = testPackage(
      version: '1.1.0',
      policy: testPolicy(
        domains: [
          SourceDomainRule(host: 'example.com'),
          SourceDomainRule(host: 'cdn.example.net'),
        ],
      ),
    );
    final updated = manager.update(broader);

    expect(updated.requiresReconsent, isTrue);
    expectManagerCode(
      () => manager.enable(
        packageId: broader.packageId,
        version: broader.version,
        userApproved: true,
        reconsentGranted: false,
      ),
      'reconsent_required',
    );
    expect(manager.enabledPackages, isEmpty);

    final enabled = manager.enable(
      packageId: broader.packageId,
      version: broader.version,
      userApproved: true,
      reconsentGranted: true,
    );
    expect(enabled.status, SourcePackageStatus.enabled);
    expect(enabled.requiresReconsent, isFalse);
  });

  test(
    'encoded package loading uses the strict decoder and preserves state',
    () {
      expect(
        () => manager.installEncoded('{"schemaVersion":'),
        throwsA(isA<SourcePackageFormatException>()),
      );
      expect(manager.installedPackages, isEmpty);
    },
  );

  test(
    'approved builder proposal requires exact identity and enables atomically',
    () {
      final proposal = validProposal();

      expectManagerCode(
        () => manager.installApprovedProposal(
          proposal: proposal,
          proposalId: 'wrong-proposal',
          userApproved: true,
          reconsentGranted: false,
        ),
        'proposal_mismatch',
      );
      expect(manager.installedPackages, isEmpty);

      expectManagerCode(
        () => manager.installApprovedProposal(
          proposal: proposal,
          proposalId: proposal.proposalId,
          userApproved: false,
          reconsentGranted: false,
        ),
        'user_approval_required',
      );
      expect(manager.installedPackages, isEmpty);

      final activated = manager.installApprovedProposal(
        proposal: proposal,
        proposalId: proposal.proposalId,
        userApproved: true,
        reconsentGranted: false,
      );
      expect(activated.status, SourcePackageStatus.enabled);
      expect(activated.requiresConsent, isFalse);
      expect(
        manager.enabledPackages.single.package.packageId,
        'generated.example',
      );
    },
  );

  test(
    'approved proposal replacement cannot bypass current-policy re-consent',
    () {
      final original = testPackage(
        packageId: 'generated.example',
        policy: testPolicy(
          domains: [SourceDomainRule(host: 'old.example.com')],
        ),
      );
      manager.install(original);
      manager.enable(
        packageId: original.packageId,
        version: original.version,
        userApproved: true,
        reconsentGranted: false,
      );
      final proposal = validProposal(version: '1.1.0');

      expectManagerCode(
        () => manager.installApprovedProposal(
          proposal: proposal,
          proposalId: proposal.proposalId,
          userApproved: true,
          reconsentGranted: false,
        ),
        'reconsent_required',
      );
      expect(manager.enabledPackages.single.package.version, original.version);

      final replaced = manager.installApprovedProposal(
        proposal: proposal,
        proposalId: proposal.proposalId,
        userApproved: true,
        reconsentGranted: true,
      );
      expect(replaced.status, SourcePackageStatus.enabled);
      expect(replaced.package.version, Version.parse('1.1.0'));
      expect(replaced.requiresReconsent, isFalse);
    },
  );

  test('package listings are sorted and returned as immutable snapshots', () {
    manager.install(testPackage(packageId: 'zeta.anime'));
    manager.install(testPackage(packageId: 'alpha.anime'));

    expect(manager.installedPackages.map((entry) => entry.package.packageId), [
      'alpha.anime',
      'zeta.anime',
    ]);
    expect(
      () => manager.installedPackages.add(manager.installedPackages.first),
      throwsUnsupportedError,
    );
  });
}

SourcePackageManifest testPackage({
  String packageId = 'example.anime',
  String version = '1.0.0',
  String wynimeVersion = '^1.0.0',
  SourceSecurityPolicy? policy,
}) {
  return SourcePackageManifest(
    schemaVersion: 1,
    packageId: packageId,
    displayName: 'Example Anime',
    version: Version.parse(version),
    wynimeVersionConstraint: VersionConstraint.parse(wynimeVersion),
    securityPolicy: policy ?? testPolicy(),
    programs: [
      SourceRuleProgram(
        programId: 'search',
        documentKind: SourceDocumentKind.html,
        rootSelector: SourceSelector(
          kind: SourceSelectorKind.css,
          expression: '.item',
        ),
        fields: [
          SourceFieldRule(
            name: 'title',
            valueKind: SourceValueKind.text,
            required: true,
          ),
        ],
        resultLimit: 10,
      ),
    ],
  );
}

SourceSecurityPolicy testPolicy({Iterable<SourceDomainRule>? domains}) {
  return SourceSecurityPolicy(
    allowedDomains: domains ?? [SourceDomainRule(host: 'example.com')],
    permissions: {SourcePermission.network},
    budget: testSourceBudget(),
  );
}

SourceBuilderProposal validProposal({String version = '1.0.0'}) {
  final builder = DeclarativeSourcePackageBuilder();
  return builder.build(
    SourceBuilderRequest(
      packageId: 'generated.example',
      displayName: 'Generated Example',
      version: Version.parse(version),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      documentKind: SourceDocumentKind.html,
      observations: [
        SourceBuilderObservation(
          fixture: SourceFixture(
            initialUri: Uri.parse('https://example.com/search'),
            body: '<article class="item"><h2>Alpha</h2></article>',
          ),
          expectedRecords: [
            {'title': 'Alpha'},
          ],
        ),
      ],
      fields: [
        SourceBuilderFieldHint(name: 'title', valueKind: SourceValueKind.text),
      ],
    ),
  );
}

void expectManagerCode(void Function() action, String code) {
  expect(
    action,
    throwsA(
      isA<SourcePackageManagerException>().having(
        (error) => error.code,
        'code',
        code,
      ),
    ),
  );
}
