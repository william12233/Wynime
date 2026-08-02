import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/infrastructure/source_rules/source_fixture_rule_engine.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  const engine = SourceFixtureRuleEngine();

  SourceRuleProgram htmlProgram({
    SourceRegexCapture? capture,
    int resultLimit = 10,
  }) {
    return SourceRuleProgram(
      programId: 'security',
      documentKind: SourceDocumentKind.html,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.css,
        expression: '.item',
      ),
      resultLimit: resultLimit,
      fields: [
        SourceFieldRule(
          name: 'value',
          valueKind: SourceValueKind.text,
          required: true,
          regexCapture: capture,
        ),
      ],
    );
  }

  test('disallowed domains and deceptive suffixes are rejected', () {
    final program = htmlProgram();
    final package = testSourcePackage(program: program);

    for (final uri in [
      Uri.parse('https://attacker.invalid'),
      Uri.parse('https://evil-example.com'),
    ]) {
      expect(
        () => engine.evaluate(
          package: package,
          program: program,
          fixture: SourceFixture(
            initialUri: uri,
            body: '<div class="item">value</div>',
          ),
        ),
        throwsA(
          isA<SourceRuleSecurityException>().having(
            (error) => error.code,
            'code',
            'uri_not_allowed',
          ),
        ),
      );
    }
  });

  test('redirect, document and selector budgets stop evaluation', () {
    final program = htmlProgram();

    final redirectPackage = testSourcePackage(
      program: program,
      policy: testSourcePolicy(budget: testSourceBudget(maxRedirects: 1)),
    );
    expect(
      () => engine.evaluate(
        package: redirectPackage,
        program: program,
        fixture: SourceFixture(
          initialUri: Uri.parse('https://example.com'),
          redirectChain: [
            Uri.parse('https://a.example.com'),
            Uri.parse('https://b.example.com'),
          ],
          body: '<div class="item">value</div>',
        ),
      ),
      throwsA(isA<SourceRuleSecurityException>()),
    );

    final documentPackage = testSourcePackage(
      program: program,
      policy: testSourcePolicy(budget: testSourceBudget(maxDocumentBytes: 8)),
    );
    expect(
      () => engine.evaluate(
        package: documentPackage,
        program: program,
        fixture: SourceFixture(
          initialUri: Uri.parse('https://example.com'),
          body: '<div class="item">too long</div>',
        ),
      ),
      throwsA(isA<SourceRuleSecurityException>()),
    );

    final matchPackage = testSourcePackage(
      program: program,
      policy: testSourcePolicy(
        budget: testSourceBudget(maxSelectorMatches: 1),
      ),
    );
    expect(
      () => engine.evaluate(
        package: matchPackage,
        program: program,
        fixture: SourceFixture(
          initialUri: Uri.parse('https://example.com'),
          body: '<div class="item">A</div><div class="item">B</div>',
        ),
      ),
      throwsA(
        isA<SourceRuleSecurityException>().having(
          (error) => error.code,
          'code',
          'selector_match_budget_exceeded',
        ),
      ),
    );
  });

  test('unsafe regular-expression constructs are rejected', () {
    final unsafePatterns = <String>[
      r'(a+)+$',
      r'(a|aa)+$',
      r'(.*).*(x)',
      r'(a)\1',
      r'(?=a)a',
      r'a+',
      r'(\d+)?',
    ];

    for (final pattern in unsafePatterns) {
      final program = htmlProgram(
        capture: SourceRegexCapture(pattern: pattern, group: 0),
      );
      final package = testSourcePackage(program: program);
      expect(
        () => engine.evaluate(
          package: package,
          program: program,
          fixture: SourceFixture(
            initialUri: Uri.parse('https://example.com'),
            body: '<div class="item">aaaaaaaaaaaaaaaax</div>',
          ),
        ),
        throwsA(isA<SourceRuleSecurityException>()),
        reason: pattern,
      );
    }
  });

  test('unsupported JSONPath syntax fails explicitly', () {
    final program = SourceRuleProgram(
      programId: 'json',
      documentKind: SourceDocumentKind.json,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.jsonPath,
        expression: r'$..items',
      ),
      resultLimit: 10,
      fields: [
        SourceFieldRule(
          name: 'value',
          valueKind: SourceValueKind.raw,
          required: true,
        ),
      ],
    );
    final package = testSourcePackage(program: program);

    expect(
      () => engine.evaluate(
        package: package,
        program: program,
        fixture: SourceFixture(
          initialUri: Uri.parse('https://example.com'),
          body: '{"items": []}',
        ),
      ),
      throwsA(
        isA<SourceRuleEvaluationException>().having(
          (error) => error.code,
          'code',
          'invalid_json_path',
        ),
      ),
    );
  });
}
