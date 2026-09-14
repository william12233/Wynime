import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_builder_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_builder.dart';
import 'package:wynime/src/infrastructure/source_rules/safe_css_selector_policy.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  final builder = DeclarativeSourcePackageBuilder();

  SourceBuilderRequest htmlRequest({
    SourceSecurityPolicy? previousSecurityPolicy,
    SourceResourceBudget? proposedBudget,
    bool allowObservedDomains = false,
    bool allowInsecureHttp = false,
    Uri? uri,
  }) {
    final observedUri = uri ?? Uri.parse('https://example.com/search');
    return SourceBuilderRequest(
      packageId: 'generated.example',
      displayName: 'Generated Example',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      documentKind: SourceDocumentKind.html,
      observations: [
        SourceBuilderObservation(
          fixture: SourceFixture(
            initialUri: observedUri,
            body: '''
<main class="cards">
  <article class="anime-card">
    <h2 class="title">Alpha</h2>
    <a class="episode-link" href="/alpha">Episode 1</a>
  </article>
  <article class="anime-card">
    <h2 class="title">Beta</h2>
    <a class="episode-link" href="/beta">Episode 2</a>
  </article>
</main>
''',
          ),
          expectedRecords: [
            {'title': 'Alpha', 'href': '/alpha'},
            {'title': 'Beta', 'href': '/beta'},
          ],
        ),
      ],
      fields: [
        SourceBuilderFieldHint(name: 'title', valueKind: SourceValueKind.text),
        SourceBuilderFieldHint(
          name: 'href',
          valueKind: SourceValueKind.attribute,
          attributeName: 'href',
        ),
      ],
      previousSecurityPolicy: previousSecurityPolicy,
      proposedBudget: proposedBudget,
      allowObservedDomains: allowObservedDomains,
      allowInsecureHttp: allowInsecureHttp,
    );
  }

  test(
    'HTML builder emits verified bounded rules and never auto-activates',
    () {
      final proposal = builder.build(htmlRequest());

      expect(proposal.packageManifest, isNotNull);
      expect(proposal.state, SourceBuilderProposalState.reviewRequired);
      expect(proposal.requiresConsent, isTrue);
      expect(proposal.requiresReconsent, isFalse);
      expect(proposal.canActivate, isFalse);
      expect(proposal.hasErrors, isFalse);

      final package = proposal.packageManifest!;
      final program = package.programs.single;
      expect(program.rootSelector.expression, '.anime-card');
      expect(
        program.fields.map((field) => field.selector?.expression),
        containsAll(<String>['h2.title', 'a.episode-link']),
      );
      SafeCssSelectorPolicy.validate(program.rootSelector.expression);
      for (final field in program.fields) {
        final selector = field.selector?.expression;
        if (selector != null) {
          SafeCssSelectorPolicy.validate(selector);
        }
      }

      expect(
        () => const SourcePackageActivationService().activate(
          proposal: proposal,
          proposalId: 'wrong-proposal',
          userApproved: true,
          reconsentGranted: false,
        ),
        throwsA(
          isA<SourcePackageActivationException>().having(
            (error) => error.code,
            'code',
            'proposal_mismatch',
          ),
        ),
      );
      expect(
        () => const SourcePackageActivationService().activate(
          proposal: proposal,
          proposalId: proposal.proposalId,
          userApproved: false,
          reconsentGranted: false,
        ),
        throwsA(
          isA<SourcePackageActivationException>().having(
            (error) => error.code,
            'code',
            'user_approval_required',
          ),
        ),
      );
      expect(
        const SourcePackageActivationService().activate(
          proposal: proposal,
          proposalId: proposal.proposalId,
          userApproved: true,
          reconsentGranted: false,
        ),
        same(package),
      );
    },
  );

  test('HTML inference is replayed against every supplied observation', () {
    final request = SourceBuilderRequest(
      packageId: 'generated.multi-observation',
      displayName: 'Generated Multi Observation',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      documentKind: SourceDocumentKind.html,
      observations: [
        SourceBuilderObservation(
          fixture: SourceFixture(
            initialUri: Uri.parse('https://example.com/search?page=1'),
            body: '''
<section>
  <article class="anime-card"><h2 class="title">Gamma</h2></article>
  <article class="anime-card"><h2 class="title">Delta</h2></article>
</section>
''',
          ),
          expectedRecords: [
            {'title': 'Gamma'},
            {'title': 'Delta'},
          ],
        ),
        SourceBuilderObservation(
          fixture: SourceFixture(
            initialUri: Uri.parse('https://example.com/search?page=2'),
            body: '''
<section>
  <article class="anime-card"><h2 class="title">Epsilon</h2></article>
  <article class="anime-card"><h2 class="title">Zeta</h2></article>
</section>
''',
          ),
          expectedRecords: [
            {'title': 'Epsilon'},
            {'title': 'Zeta'},
          ],
        ),
      ],
      fields: [
        SourceBuilderFieldHint(name: 'title', valueKind: SourceValueKind.text),
      ],
    );

    final proposal = builder.build(request);

    expect(proposal.packageManifest, isNotNull);
    expect(proposal.hasErrors, isFalse);
    expect(
      proposal.packageManifest!.programs.single.rootSelector.expression,
      '.anime-card',
    );
  });

  test('JSON builder emits only the schema v1 raw JSONPath dialect', () {
    final request = SourceBuilderRequest(
      packageId: 'generated.json',
      displayName: 'Generated JSON',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      documentKind: SourceDocumentKind.json,
      observations: [
        SourceBuilderObservation(
          fixture: SourceFixture(
            initialUri: Uri.parse('https://example.com/api'),
            body: '''
{"items":[{"title":"Alpha","id":"a1"},{"title":"Beta","id":"b2"}]}
''',
          ),
          expectedRecords: [
            {'title': 'Alpha', 'id': 'a1'},
            {'title': 'Beta', 'id': 'b2'},
          ],
        ),
      ],
      fields: [
        SourceBuilderFieldHint(name: 'title', valueKind: SourceValueKind.raw),
        SourceBuilderFieldHint(name: 'id', valueKind: SourceValueKind.raw),
      ],
    );

    final proposal = builder.build(request);
    expect(proposal.packageManifest, isNotNull);
    final program = proposal.packageManifest!.programs.single;
    expect(program.rootSelector.expression, r'$.items[*]');
    expect(
      program.fields.every(
        (field) =>
            field.valueKind == SourceValueKind.raw &&
            field.selector!.kind == SourceSelectorKind.jsonPath,
      ),
      isTrue,
    );
    expect(proposal.hasErrors, isFalse);
  });

  test('new observed domain on an existing package requires re-consent', () {
    final previous = testSourcePolicy(
      domains: [SourceDomainRule(host: 'example.com')],
    );
    final proposal = builder.build(
      htmlRequest(
        previousSecurityPolicy: previous,
        allowObservedDomains: true,
        uri: Uri.parse('https://new.example.net/search'),
      ),
    );

    expect(proposal.packageManifest, isNotNull);
    expect(proposal.requiresReconsent, isTrue);
    expect(
      () => const SourcePackageActivationService().activate(
        proposal: proposal,
        proposalId: proposal.proposalId,
        userApproved: true,
        reconsentGranted: false,
      ),
      throwsA(
        isA<SourcePackageActivationException>().having(
          (error) => error.code,
          'code',
          'reconsent_required',
        ),
      ),
    );
  });

  test('a broader resource budget requires re-consent', () {
    final previous = testSourcePolicy();
    final proposal = builder.build(
      htmlRequest(
        previousSecurityPolicy: previous,
        uri: Uri.parse('https://example.com/search'),
        proposedBudget: testSourceBudget(maxDocumentBytes: 128 * 1024),
      ),
    );

    expect(proposal.packageManifest, isNotNull);
    expect(proposal.requiresReconsent, isTrue);
    expect(
      () => const SourcePackageActivationService().activate(
        proposal: proposal,
        proposalId: proposal.proposalId,
        userApproved: true,
        reconsentGranted: false,
      ),
      throwsA(
        isA<SourcePackageActivationException>().having(
          (error) => error.code,
          'code',
          'reconsent_required',
        ),
      ),
    );
  });

  test(
    'existing allowlist rejects an observed domain without explicit expansion',
    () {
      final previous = testSourcePolicy(
        domains: [SourceDomainRule(host: 'example.com')],
      );
      final proposal = builder.build(
        htmlRequest(
          previousSecurityPolicy: previous,
          uri: Uri.parse('https://new.example.net/search'),
        ),
      );

      expect(proposal.packageManifest, isNull);
      expect(
        proposal.diagnostics.map((diagnostic) => diagnostic.code),
        contains('observed_domain_not_allowed'),
      );
    },
  );

  test(
    'HTTP observations need an explicit opt-in and remain consent gated',
    () {
      final rejected = builder.build(
        htmlRequest(uri: Uri.parse('http://example.com/search')),
      );
      expect(rejected.packageManifest, isNull);
      expect(
        rejected.diagnostics.map((diagnostic) => diagnostic.code),
        contains('insecure_http_requires_explicit_opt_in'),
      );

      final accepted = builder.build(
        htmlRequest(
          uri: Uri.parse('http://example.com/search'),
          allowInsecureHttp: true,
        ),
      );
      expect(accepted.packageManifest, isNotNull);
      expect(
        accepted.packageManifest!.securityPolicy.permissions,
        contains(SourcePermission.insecureHttp),
      );
      expect(accepted.requiresConsent, isTrue);
    },
  );

  test('missing examples reject generation instead of guessing selectors', () {
    final request = SourceBuilderRequest(
      packageId: 'missing.examples',
      displayName: 'Missing Examples',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      documentKind: SourceDocumentKind.html,
      observations: [
        SourceBuilderObservation(
          fixture: SourceFixture(
            initialUri: Uri.parse('https://example.com'),
            body: '<article class="item">Unknown</article>',
          ),
          expectedRecords: [{}],
        ),
      ],
      fields: [
        SourceBuilderFieldHint(name: 'title', valueKind: SourceValueKind.text),
      ],
    );

    final proposal = builder.build(request);
    expect(proposal.packageManifest, isNull);
    expect(
      proposal.diagnostics.map((diagnostic) => diagnostic.code),
      contains('html_program_not_inferred'),
    );
  });

  test('overlong HTML root selectors fail closed with a rejected proposal', () {
    final longClass = List<String>.filled(257, 'a').join();
    final request = SourceBuilderRequest(
      packageId: 'overlong.root',
      displayName: 'Overlong Root',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      documentKind: SourceDocumentKind.html,
      proposedBudget: testSourceBudget(maxSelectorMatches: 2),
      observations: [
        SourceBuilderObservation(
          fixture: SourceFixture(
            initialUri: Uri.parse('https://example.com'),
            body:
                '''
<article class="$longClass">Alpha</article>
<article class="$longClass">Beta</article>
<article class="noise">Noise 1</article>
<article class="noise">Noise 2</article>
<article class="noise">Noise 3</article>
''',
          ),
          expectedRecords: [
            {'title': 'Alpha'},
            {'title': 'Beta'},
          ],
        ),
      ],
      fields: [
        SourceBuilderFieldHint(name: 'title', valueKind: SourceValueKind.text),
      ],
    );

    final proposal = builder.build(request);

    expect(proposal.packageManifest, isNull);
    expect(proposal.hasErrors, isTrue);
    expect(
      proposal.diagnostics.map((diagnostic) => diagnostic.code),
      contains('html_program_not_inferred'),
    );
  });

  test('malformed HTML fails closed instead of using parser recovery', () {
    final request = SourceBuilderRequest(
      packageId: 'malformed.html',
      displayName: 'Malformed HTML',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      documentKind: SourceDocumentKind.html,
      observations: [
        SourceBuilderObservation(
          fixture: SourceFixture(
            initialUri: Uri.parse('https://example.com'),
            body: '<article class="anime-card"><h2 class="title">Alpha',
          ),
          expectedRecords: [
            {'title': 'Alpha'},
          ],
        ),
      ],
      fields: [
        SourceBuilderFieldHint(name: 'title', valueKind: SourceValueKind.text),
      ],
    );

    final proposal = builder.build(request);

    expect(proposal.packageManifest, isNull);
    expect(proposal.hasErrors, isTrue);
    expect(
      proposal.diagnostics.map((diagnostic) => diagnostic.code),
      contains('html_document_invalid'),
    );
  });

  test('bounded input and unsupported JSON field kinds fail closed', () {
    final oversizedRequest = SourceBuilderRequest(
      packageId: 'oversized.observation',
      displayName: 'Oversized Observation',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      documentKind: SourceDocumentKind.html,
      proposedBudget: testSourceBudget(maxDocumentBytes: 8),
      observations: [
        SourceBuilderObservation(
          fixture: SourceFixture(
            initialUri: Uri.parse('https://example.com'),
            body: '<div class="item">too long</div>',
          ),
          expectedRecords: [
            {'title': 'too long'},
          ],
        ),
      ],
      fields: [
        SourceBuilderFieldHint(name: 'title', valueKind: SourceValueKind.text),
      ],
    );
    final oversized = builder.build(oversizedRequest);
    expect(oversized.packageManifest, isNull);
    expect(
      oversized.diagnostics.map((diagnostic) => diagnostic.code),
      contains('document_budget_exceeded'),
    );

    final unsupportedJsonRequest = SourceBuilderRequest(
      packageId: 'unsupported.json',
      displayName: 'Unsupported JSON',
      version: Version.parse('1.0.0'),
      wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
      documentKind: SourceDocumentKind.json,
      observations: [
        SourceBuilderObservation(
          fixture: SourceFixture(
            initialUri: Uri.parse('https://example.com'),
            body: '{"items":[{"title":"Alpha"}]}',
          ),
          expectedRecords: [
            {'title': 'Alpha'},
          ],
        ),
      ],
      fields: [
        SourceBuilderFieldHint(name: 'title', valueKind: SourceValueKind.text),
      ],
    );
    final unsupportedJson = builder.build(unsupportedJsonRequest);
    expect(unsupportedJson.packageManifest, isNull);
    expect(
      unsupportedJson.diagnostics.map((diagnostic) => diagnostic.code),
      contains('json_field_kind_unsupported'),
    );
  });

  test('bounded constructors reject oversized and unbounded iterables', () {
    final observation = SourceBuilderObservation(
      fixture: SourceFixture(
        initialUri: Uri.parse('https://example.com'),
        body: '<div>value</div>',
      ),
      expectedRecords: [
        {'title': 'value'},
      ],
    );

    Iterable<Map<String, String>> unboundedRecords() sync* {
      while (true) {
        yield {'title': 'value'};
      }
    }

    Iterable<SourceBuilderObservation> unboundedObservations() sync* {
      while (true) {
        yield observation;
      }
    }

    expect(
      () => SourceBuilderObservation(
        fixture: observation.fixture,
        expectedRecords: unboundedRecords(),
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceBuilderRequest(
        packageId: 'unbounded.observations',
        displayName: 'Unbounded Observations',
        version: Version.parse('1.0.0'),
        wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
        documentKind: SourceDocumentKind.html,
        observations: unboundedObservations(),
        fields: [
          SourceBuilderFieldHint(
            name: 'title',
            valueKind: SourceValueKind.text,
          ),
        ],
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceBuilderRequest(
        packageId: 'oversized.fields',
        displayName: 'Oversized Fields',
        version: Version.parse('1.0.0'),
        wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
        documentKind: SourceDocumentKind.html,
        observations: [observation],
        fields: Iterable.generate(
          65,
          (index) => SourceBuilderFieldHint(
            name: 'field$index',
            valueKind: SourceValueKind.text,
          ),
        ),
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceBuilderObservation(
        fixture: observation.fixture,
        expectedRecords: [
          Map<String, String>.fromEntries(
            Iterable.generate(65, (index) => MapEntry('field$index', 'value')),
          ),
        ],
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceBuilderFieldHint(
        name: 'href',
        valueKind: SourceValueKind.attribute,
        attributeName: 'a' * 65,
      ),
      throwsArgumentError,
    );
    expect(
      () => SourceBuilderObservation(
        fixture: observation.fixture,
        expectedRecords: [
          {'title': 'value', ' title ': 'duplicate'},
        ],
      ),
      throwsArgumentError,
    );
  });

  test('unsafe observation URIs fail closed without leaking URI contents', () {
    final secretUri = Uri.parse(
      'file:///private/token=do-not-log?cookie=do-not-log',
    );
    final proposal = builder.build(htmlRequest(uri: secretUri));

    expect(proposal.packageManifest, isNull);
    expect(
      proposal.diagnostics.map((diagnostic) => diagnostic.code),
      contains('observed_uri_invalid'),
    );
    expect(proposal.proposalId, isNot(contains('do-not-log')));
    expect(
      proposal.diagnostics.every(
        (diagnostic) =>
            !diagnostic.message.contains('do-not-log') &&
            !diagnostic.message.contains('token'),
      ),
      isTrue,
    );
  });
}
