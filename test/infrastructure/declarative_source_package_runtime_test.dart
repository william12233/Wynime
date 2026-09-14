import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  late DeclarativeSourcePackageManager manager;
  late DeclarativeSourcePackageRuntime runtime;

  setUp(() {
    manager = DeclarativeSourcePackageManager(
      wynimeVersion: Version.parse('1.5.0'),
    );
    runtime = DeclarativeSourcePackageRuntime(
      wynimeVersion: Version.parse('1.5.0'),
    );
  });

  test('enabled package evaluates a fixture into immutable records', () {
    final package = htmlPackage();
    final installed = _enable(package);

    final result = runtime.executeFixture(
      installedPackage: installed,
      programId: 'search',
      fixture: fixture('<article class="item"><h2>Alpha</h2></article>'),
    );

    expect(result.status, SourceRuntimeStatus.available);
    expect(result.records.single.values['title'], 'Alpha');
    expect(result.consumedSteps, greaterThan(0));
    expect(result.selectorMatches, greaterThan(0));
    expect(
      () => result.records.add(SourceRuntimeRecord({'title': 'Beta'})),
      throwsUnsupportedError,
    );
    expect(
      () => result.records.single.values['title'] = 'changed',
      throwsUnsupportedError,
    );
  });

  test('empty evaluation is not found and keeps safe field diagnostics', () {
    final installed = _enable(htmlPackage());

    final result = runtime.executeFixture(
      installedPackage: installed,
      programId: 'search',
      fixture: fixture('<article class="item"></article>'),
    );

    expect(result.status, SourceRuntimeStatus.notFound);
    expect(result.records, isEmpty);
    expect(result.diagnostics.single.code, 'required_field_missing');
    expect(
      result.diagnostics.single.message,
      'A required source field was not found.',
    );
  });

  test(
    'partial evaluation stays available while exposing bounded diagnostics',
    () {
      final installed = _enable(htmlPackage());

      final result = runtime.executeFixture(
        installedPackage: installed,
        programId: 'search',
        fixture: fixture(
          '<article class="item"><h2>Alpha</h2></article>'
          '<article class="item"></article>',
        ),
      );

      expect(result.status, SourceRuntimeStatus.available);
      expect(result.records.single.values['title'], 'Alpha');
      expect(result.diagnostics.single.code, 'required_field_missing');
      expect(result.diagnostics.single.recordIndex, 1);
    },
  );

  test('trims valid IDs and rejects invalid IDs before evaluation', () {
    final installed = _enable(htmlPackage());
    final trimmed = runtime.executeFixture(
      installedPackage: installed,
      programId: ' search ',
      fixture: fixture('<article class="item"><h2>Alpha</h2></article>'),
    );
    expect(trimmed.status, SourceRuntimeStatus.available);
    expect(trimmed.programId, 'search');

    final invalidIds = <String>[
      '',
      '   ',
      'SEARCH',
      List<String>.filled(65, 'a').join(),
    ];
    for (final invalidId in invalidIds) {
      final result = runtime.executeFixture(
        installedPackage: installed,
        programId: invalidId,
        fixture: fixture('<article class="item"><h2>Alpha</h2></article>'),
      );
      expect(result.status, SourceRuntimeStatus.failed);
      expect(result.programId, 'unknown');
      expect(result.diagnostics.single.code, 'program_not_found');
      expect(result.consumedSteps, 0);
      expect(result.selectorMatches, 0);
    }
  });

  test('disabled and consent-pending packages do not evaluate fixtures', () {
    final package = htmlPackage();
    final pending = manager.install(package);
    final disabled = runtime.executeFixture(
      installedPackage: pending,
      programId: 'search',
      fixture: fixture('<article class="item"><h2>Alpha</h2></article>'),
    );
    expect(disabled.status, SourceRuntimeStatus.disabled);
    expect(disabled.consumedSteps, 0);

    final consentPending = InstalledSourcePackage(
      package: package,
      status: SourcePackageStatus.enabled,
      requiresConsent: true,
      requiresReconsent: false,
    );
    final consent = runtime.executeFixture(
      installedPackage: consentPending,
      programId: 'search',
      fixture: fixture('<article class="item"><h2>Alpha</h2></article>'),
    );
    expect(consent.status, SourceRuntimeStatus.consentRequired);
    expect(consent.consumedSteps, 0);
  });

  test('incompatible package and unknown program fail before evaluation', () {
    final incompatiblePackage = htmlPackage(wynimeVersion: '<1.0.0');
    final incompatible = runtime.executeFixture(
      installedPackage: InstalledSourcePackage(
        package: incompatiblePackage,
        status: SourcePackageStatus.enabled,
        requiresConsent: false,
        requiresReconsent: false,
      ),
      programId: 'search',
      fixture: fixture('<article class="item"><h2>Alpha</h2></article>'),
    );
    expect(incompatible.status, SourceRuntimeStatus.incompatible);
    expect(incompatible.diagnostics.single.code, 'incompatible_wynime_version');

    final unknown = runtime.executeFixture(
      installedPackage: _enable(htmlPackage()),
      programId: 'detail',
      fixture: fixture('<article class="item"><h2>Alpha</h2></article>'),
    );
    expect(unknown.status, SourceRuntimeStatus.failed);
    expect(unknown.diagnostics.single.code, 'program_not_found');
  });

  test(
    'security and parse failures are stable and do not echo fixture data',
    () {
      final installed = _enable(htmlPackage());
      const secret = 'https://private.example.test/secret-token';
      final security = runtime.executeFixture(
        installedPackage: installed,
        programId: 'search',
        fixture: SourceFixture(
          initialUri: Uri.parse(secret),
          body: '<article class="item"><h2>Alpha</h2></article>',
        ),
      );
      expect(security.status, SourceRuntimeStatus.failed);
      expect(security.diagnostics.single.code, 'uri_not_allowed');
      expect(security.diagnostics.single.message, isNot(contains(secret)));

      final jsonPackage = packageFor(
        SourceRuleProgram(
          programId: 'search',
          documentKind: SourceDocumentKind.json,
          rootSelector: SourceSelector(
            kind: SourceSelectorKind.jsonPath,
            expression: r'$.items[*]',
          ),
          fields: [
            SourceFieldRule(
              name: 'title',
              valueKind: SourceValueKind.raw,
              required: true,
              selector: SourceSelector(
                kind: SourceSelectorKind.jsonPath,
                expression: r'$.title',
              ),
            ),
          ],
          resultLimit: 10,
        ),
      );
      final parse = runtime.executeFixture(
        installedPackage: _enable(jsonPackage),
        programId: 'search',
        fixture: SourceFixture(
          initialUri: Uri.parse('https://example.com/search'),
          body: '{"items": [',
        ),
      );
      expect(parse.status, SourceRuntimeStatus.failed);
      expect(parse.diagnostics.single.code, 'json_parse_failed');
      expect(parse.diagnostics.single.message, isNot(contains('{"items"')));
    },
  );
}

InstalledSourcePackage _enable(SourcePackageManifest package) {
  final manager = DeclarativeSourcePackageManager(
    wynimeVersion: Version.parse('1.5.0'),
  );
  final pending = manager.install(package);
  return manager.enable(
    packageId: package.packageId,
    version: pending.package.version,
    userApproved: true,
    reconsentGranted: false,
  );
}

SourceFixture fixture(String body) {
  return SourceFixture(
    initialUri: Uri.parse('https://example.com/search'),
    body: body,
  );
}

SourcePackageManifest htmlPackage({String wynimeVersion = '^1.0.0'}) {
  return packageFor(
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
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: 'h2',
          ),
          required: true,
        ),
      ],
      resultLimit: 10,
    ),
    wynimeVersion: wynimeVersion,
  );
}

SourcePackageManifest packageFor(
  SourceRuleProgram program, {
  String wynimeVersion = '^1.0.0',
}) {
  return SourcePackageManifest(
    schemaVersion: 1,
    packageId: 'example.anime',
    displayName: 'Example Anime',
    version: Version.parse('1.0.0'),
    wynimeVersionConstraint: VersionConstraint.parse(wynimeVersion),
    securityPolicy: testSourcePolicy(),
    programs: [program],
  );
}
