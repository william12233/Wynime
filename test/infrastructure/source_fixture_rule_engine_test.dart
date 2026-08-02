import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/infrastructure/source_rules/source_fixture_rule_engine.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  const engine = SourceFixtureRuleEngine();

  test('CSS fixture evaluator extracts bounded records and regex captures', () {
    final program = SourceRuleProgram(
      programId: 'search',
      documentKind: SourceDocumentKind.html,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.css,
        expression: '.anime-card',
      ),
      resultLimit: 10,
      fields: [
        SourceFieldRule(
          name: 'title',
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.title',
          ),
          valueKind: SourceValueKind.text,
          required: true,
        ),
        SourceFieldRule(
          name: 'path',
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.title',
          ),
          valueKind: SourceValueKind.attribute,
          attributeName: 'href',
          required: true,
        ),
        SourceFieldRule(
          name: 'episode',
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.episode',
          ),
          valueKind: SourceValueKind.text,
          required: true,
          regexCapture: SourceRegexCapture(
            pattern: r'^Episode (\d+)$',
          ),
        ),
      ],
    );
    final package = testSourcePackage(program: program);
    final body = File(
      'test/fixtures/source_rules/anime_list.html',
    ).readAsStringSync();

    final result = engine.evaluate(
      package: package,
      program: program,
      fixture: SourceFixture(
        initialUri: Uri.parse('https://www.example.com/search?q=anime'),
        body: body,
      ),
    );

    expect(result.records, hasLength(2));
    expect(
      result.records.first.values,
      containsPair('title', "Frieren: Beyond Journey's End"),
    );
    expect(result.records.first.values, containsPair('path', '/anime/100'));
    expect(result.records.first.values, containsPair('episode', '12'));
    expect(result.diagnostics, isEmpty);
  });

  test('restricted JSONPath evaluates supplied JSON fixtures', () {
    final program = SourceRuleProgram(
      programId: 'catalog',
      documentKind: SourceDocumentKind.json,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.jsonPath,
        expression: r'$.items[*]',
      ),
      resultLimit: 10,
      fields: [
        SourceFieldRule(
          name: 'title',
          selector: SourceSelector(
            kind: SourceSelectorKind.jsonPath,
            expression: r'$.title',
          ),
          valueKind: SourceValueKind.raw,
          required: true,
        ),
        SourceFieldRule(
          name: 'episode',
          selector: SourceSelector(
            kind: SourceSelectorKind.jsonPath,
            expression: r'$.episode',
          ),
          valueKind: SourceValueKind.raw,
          required: true,
        ),
      ],
    );
    final package = testSourcePackage(program: program);
    final body = File(
      'test/fixtures/source_rules/anime_list.json',
    ).readAsStringSync();

    final result = engine.evaluate(
      package: package,
      program: program,
      fixture: SourceFixture(
        initialUri: Uri.parse('https://api.example.com/catalog'),
        body: body,
      ),
    );

    expect(result.records, hasLength(2));
    expect(result.records.last.values, containsPair('episode', '24'));
  });

  test('missing required fields reject only the affected record', () {
    final program = SourceRuleProgram(
      programId: 'search',
      documentKind: SourceDocumentKind.html,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.css,
        expression: '.item',
      ),
      resultLimit: 10,
      fields: [
        SourceFieldRule(
          name: 'title',
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: '.title',
          ),
          valueKind: SourceValueKind.text,
          required: true,
        ),
      ],
    );
    final package = testSourcePackage(program: program);

    final result = engine.evaluate(
      package: package,
      program: program,
      fixture: SourceFixture(
        initialUri: Uri.parse('https://example.com'),
        body: '<div class="item"><span class="title">A</span></div>'
            '<div class="item"></div>',
      ),
    );

    expect(result.records, hasLength(1));
    expect(result.diagnostics.single.code, 'required_field_missing');
    expect(result.diagnostics.single.recordIndex, 1);
  });
}
