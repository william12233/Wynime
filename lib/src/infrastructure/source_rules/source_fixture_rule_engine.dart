import 'dart:convert';

import 'package:html/dom.dart';
import 'package:html/parser.dart' as html_parser;

import '../../domain/models/source_package_manifest.dart';
import '../../domain/models/source_rule_program.dart';
import '../../domain/models/source_security_policy.dart';
import '../../domain/services/source_rule_evaluator.dart';
import 'evaluation_budget_meter.dart';
import 'json_path_subset.dart';
import 'safe_css_selector_policy.dart';
import 'safe_regex_policy.dart';

final class SourceFixtureRuleEngine implements SourceRuleEvaluator {
  const SourceFixtureRuleEngine();

  @override
  SourceEvaluationResult evaluate({
    required SourcePackageManifest package,
    required SourceRuleProgram program,
    required SourceFixture fixture,
  }) {
    final policy = package.securityPolicy;
    _validateFixtureSecurity(policy, program, fixture);
    final meter = EvaluationBudgetMeter(policy.budget);
    return switch (program.documentKind) {
      SourceDocumentKind.html => _evaluateHtml(program, fixture, meter),
      SourceDocumentKind.json => _evaluateJson(program, fixture, meter),
    };
  }

  void _validateFixtureSecurity(
    SourceSecurityPolicy policy,
    SourceRuleProgram program,
    SourceFixture fixture,
  ) {
    if (!policy.allowsUri(fixture.initialUri)) {
      throw SourceRuleSecurityException(
        'uri_not_allowed',
        'Initial URI is outside the declared network permission and allowlist.',
      );
    }
    if (fixture.redirectChain.length > policy.budget.maxRedirects) {
      throw SourceRuleSecurityException(
        'redirect_budget_exceeded',
        'Redirect count exceeds the declared budget.',
      );
    }
    for (final redirect in fixture.redirectChain) {
      if (!policy.allowsUri(redirect)) {
        throw SourceRuleSecurityException(
          'redirect_uri_not_allowed',
          'A redirect target is outside the declared allowlist.',
        );
      }
    }
    final bodyBytes = utf8.encode(fixture.body).length;
    if (bodyBytes > policy.budget.maxDocumentBytes) {
      throw SourceRuleSecurityException(
        'document_budget_exceeded',
        'Fixture document contains $bodyBytes bytes.',
      );
    }
    if (program.resultLimit > policy.budget.maxRecords) {
      throw SourceRuleSecurityException(
        'record_budget_exceeded',
        'Program resultLimit exceeds the package budget.',
      );
    }
  }

  SourceEvaluationResult _evaluateHtml(
    SourceRuleProgram program,
    SourceFixture fixture,
    EvaluationBudgetMeter meter,
  ) {
    final Document document;
    try {
      meter.consumeStep();
      document = html_parser.parse(fixture.body, generateSpans: false);
    } on Object catch (error) {
      throw SourceRuleEvaluationException(
        'html_parse_failed',
        'HTML parser failed: $error',
      );
    }

    final roots = _queryAll(document, program.rootSelector.expression, meter);
    final records = <SourceEvaluationRecord>[];
    final diagnostics = <SourceEvaluationDiagnostic>[];
    final count = roots.length < program.resultLimit
        ? roots.length
        : program.resultLimit;

    for (var index = 0; index < count; index++) {
      meter.consumeStep();
      final values = <String, String>{};
      var rejected = false;
      for (final field in program.fields) {
        meter.consumeStep();
        final value = _extractHtmlField(roots[index], field, meter);
        if (value == null) {
          if (field.required) {
            diagnostics.add(
              SourceEvaluationDiagnostic(
                code: 'required_field_missing',
                message: 'Required field ${field.name} was not extracted.',
                recordIndex: index,
                fieldName: field.name,
              ),
            );
            rejected = true;
            break;
          }
          continue;
        }
        values[field.name] = value;
      }
      if (!rejected) {
        records.add(SourceEvaluationRecord(values));
      }
    }

    return SourceEvaluationResult(
      records: records,
      diagnostics: diagnostics,
      consumedSteps: meter.consumedSteps,
      selectorMatches: meter.selectorMatches,
    );
  }

  String? _extractHtmlField(
    Element root,
    SourceFieldRule field,
    EvaluationBudgetMeter meter,
  ) {
    final Element? target;
    if (field.selector == null) {
      target = root;
    } else {
      final matches = _queryAll(root, field.selector!.expression, meter);
      target = matches.isEmpty ? null : matches.first;
    }
    if (target == null) {
      return null;
    }

    final String? extracted = switch (field.valueKind) {
      SourceValueKind.text => _normalizeWhitespace(target.text),
      SourceValueKind.attribute => target.attributes[field.attributeName!]?.trim(),
      SourceValueKind.raw => target.outerHtml.trim(),
    };
    return _applyRegex(extracted, field.regexCapture, meter);
  }

  List<Element> _queryAll(
    dynamic root,
    String expression,
    EvaluationBudgetMeter meter,
  ) {
    meter.consumeStep();
    SafeCssSelectorPolicy.validate(expression);
    try {
      final rawMatches = root.querySelectorAll(expression) as Iterable;
      final matches = rawMatches.cast<Element>().toList(growable: false);
      meter.addSelectorMatches(matches.length);
      return matches;
    } on SourceRuleSecurityException {
      rethrow;
    } on Object catch (error) {
      throw SourceRuleEvaluationException(
        'invalid_css_selector',
        'CSS selector failed: $error',
      );
    }
  }

  SourceEvaluationResult _evaluateJson(
    SourceRuleProgram program,
    SourceFixture fixture,
    EvaluationBudgetMeter meter,
  ) {
    final Object? document;
    try {
      meter.consumeStep();
      document = jsonDecode(fixture.body);
    } on FormatException catch (error) {
      throw SourceRuleEvaluationException('json_parse_failed', error.message);
    }

    final roots = JsonPathSubset.evaluate(
      document,
      program.rootSelector.expression,
      meter,
    );
    meter.addSelectorMatches(roots.length);
    final records = <SourceEvaluationRecord>[];
    final diagnostics = <SourceEvaluationDiagnostic>[];
    final count = roots.length < program.resultLimit
        ? roots.length
        : program.resultLimit;

    for (var index = 0; index < count; index++) {
      meter.consumeStep();
      final values = <String, String>{};
      var rejected = false;
      for (final field in program.fields) {
        meter.consumeStep();
        final selected = field.selector == null
            ? <Object?>[roots[index]]
            : JsonPathSubset.evaluate(
                roots[index],
                field.selector!.expression,
                meter,
              );
        meter.addSelectorMatches(selected.length);
        if (selected.length > 1) {
          throw SourceRuleEvaluationException(
            'multiple_field_values',
            'Field ${field.name} selected multiple JSON values.',
          );
        }
        final value = selected.isEmpty
            ? null
            : _jsonValueToString(selected.single);
        final captured = _applyRegex(value, field.regexCapture, meter);
        if (captured == null) {
          if (field.required) {
            diagnostics.add(
              SourceEvaluationDiagnostic(
                code: 'required_field_missing',
                message: 'Required field ${field.name} was not extracted.',
                recordIndex: index,
                fieldName: field.name,
              ),
            );
            rejected = true;
            break;
          }
          continue;
        }
        values[field.name] = captured;
      }
      if (!rejected) {
        records.add(SourceEvaluationRecord(values));
      }
    }

    return SourceEvaluationResult(
      records: records,
      diagnostics: diagnostics,
      consumedSteps: meter.consumedSteps,
      selectorMatches: meter.selectorMatches,
    );
  }

  String? _applyRegex(
    String? input,
    SourceRegexCapture? capture,
    EvaluationBudgetMeter meter,
  ) {
    if (input == null || input.isEmpty) {
      return null;
    }
    if (capture == null) {
      return input;
    }
    meter.consumeStep();
    return SafeRegexPolicy.capture(capture, input, meter.budget);
  }

  static String _normalizeWhitespace(String value) {
    return value.replaceAll(RegExp(r'\s+'), ' ').trim();
  }

  static String? _jsonValueToString(Object? value) {
    if (value == null) {
      return null;
    }
    if (value is String) {
      return value.trim();
    }
    if (value is num || value is bool) {
      return value.toString();
    }
    if (value is List || value is Map) {
      return jsonEncode(value);
    }
    return value.toString();
  }
}
