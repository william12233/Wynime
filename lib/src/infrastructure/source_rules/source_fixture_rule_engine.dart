import 'dart:convert';

import 'package:html/dom.dart';
import 'package:html/parser.dart' as html_parser;

import '../../domain/models/source_package_manifest.dart';
import '../../domain/models/source_rule_program.dart';
import '../../domain/models/source_security_policy.dart';
import '../../domain/services/source_rule_evaluator.dart';

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
    final meter = _BudgetMeter(policy.budget);
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
    _BudgetMeter meter,
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
    _BudgetMeter meter,
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
    _BudgetMeter meter,
  ) {
    meter.consumeStep();
    try {
      final List<Element> matches = (root.querySelectorAll(expression) as List<Element>);
      meter.addSelectorMatches(matches.length);
      return matches;
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
    _BudgetMeter meter,
  ) {
    final Object? document;
    try {
      meter.consumeStep();
      document = jsonDecode(fixture.body);
    } on FormatException catch (error) {
      throw SourceRuleEvaluationException(
        'json_parse_failed',
        error.message,
      );
    }

    final roots = _JsonPathSubset.evaluate(
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
            : _JsonPathSubset.evaluate(
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
    _BudgetMeter meter,
  ) {
    if (input == null || input.isEmpty) {
      return null;
    }
    if (capture == null) {
      return input;
    }
    meter.consumeStep();
    _SafeRegexPolicy.validate(capture, input, meter.budget);
    final RegExp expression;
    try {
      expression = RegExp(
        capture.pattern,
        caseSensitive: capture.caseSensitive,
      );
    } on FormatException catch (error) {
      throw SourceRuleEvaluationException(
        'invalid_regex',
        error.message,
      );
    }
    final match = expression.firstMatch(input);
    if (match == null) {
      return null;
    }
    try {
      return match.group(capture.group)?.trim();
    } on RangeError {
      throw SourceRuleEvaluationException(
        'invalid_regex_group',
        'Requested capture group ${capture.group} does not exist.',
      );
    }
  }

  static String _normalizeWhitespace(String value) {
    return value.replaceAll(RegExp(r'\s+'), ' ').trim();
  }

  static String? _jsonValueToString(Object? value) {
    return switch (value) {
      null => null,
      String string => string.trim(),
      num number => number.toString(),
      bool boolean => boolean.toString(),
      List() || Map() => jsonEncode(value),
      _ => value.toString(),
    };
  }
}

final class _BudgetMeter {
  _BudgetMeter(this.budget);

  final SourceResourceBudget budget;
  int consumedSteps = 0;
  int selectorMatches = 0;

  void consumeStep([int count = 1]) {
    consumedSteps += count;
    if (consumedSteps > budget.maxEvaluationSteps) {
      throw SourceRuleSecurityException(
        'evaluation_budget_exceeded',
        'Evaluation step budget was exceeded.',
      );
    }
  }

  void addSelectorMatches(int count) {
    selectorMatches += count;
    if (selectorMatches > budget.maxSelectorMatches) {
      throw SourceRuleSecurityException(
        'selector_match_budget_exceeded',
        'Selector match budget was exceeded.',
      );
    }
  }
}

final class _JsonPathSubset {
  static List<Object?> evaluate(
    Object? root,
    String expression,
    _BudgetMeter meter,
  ) {
    final segments = _parse(expression);
    var current = <Object?>[root];
    for (final segment in segments) {
      meter.consumeStep(current.length + 1);
      final next = <Object?>[];
      for (final value in current) {
        switch (segment) {
          case _JsonPropertySegment(:final name):
            if (value is Map && value.containsKey(name)) {
              next.add(value[name]);
            }
          case _JsonIndexSegment(:final index):
            if (value is List && index < value.length) {
              next.add(value[index]);
            }
          case _JsonWildcardSegment():
            if (value is List) {
              next.addAll(value);
            }
        }
      }
      current = next;
    }
    return current;
  }

  static List<_JsonPathSegment> _parse(String expression) {
    if (!expression.startsWith(r'$')) {
      throw SourceRuleEvaluationException(
        'invalid_json_path',
        r'JSONPath must start with $.',
      );
    }
    final segments = <_JsonPathSegment>[];
    var index = 1;
    while (index < expression.length) {
      final char = expression[index];
      if (char == '.') {
        index++;
        final start = index;
        while (index < expression.length &&
            RegExp(r'[A-Za-z0-9_-]').hasMatch(expression[index])) {
          index++;
        }
        if (start == index ||
            !RegExp(r'[A-Za-z_]').hasMatch(expression[start])) {
          throw SourceRuleEvaluationException(
            'invalid_json_path',
            'Invalid property segment in $expression.',
          );
        }
        segments.add(_JsonPropertySegment(expression.substring(start, index)));
        continue;
      }
      if (char == '[') {
        final end = expression.indexOf(']', index + 1);
        if (end < 0) {
          throw SourceRuleEvaluationException(
            'invalid_json_path',
            'Unclosed index segment in $expression.',
          );
        }
        final token = expression.substring(index + 1, end);
        if (token == '*') {
          segments.add(const _JsonWildcardSegment());
        } else {
          final parsed = int.tryParse(token);
          if (parsed == null || parsed < 0) {
            throw SourceRuleEvaluationException(
              'invalid_json_path',
              'Only non-negative indexes and [*] are supported.',
            );
          }
          segments.add(_JsonIndexSegment(parsed));
        }
        index = end + 1;
        continue;
      }
      throw SourceRuleEvaluationException(
        'invalid_json_path',
        'Unsupported JSONPath syntax at offset $index.',
      );
    }
    return segments;
  }
}

sealed class _JsonPathSegment {
  const _JsonPathSegment();
}

final class _JsonPropertySegment extends _JsonPathSegment {
  const _JsonPropertySegment(this.name);

  final String name;
}

final class _JsonIndexSegment extends _JsonPathSegment {
  const _JsonIndexSegment(this.index);

  final int index;
}

final class _JsonWildcardSegment extends _JsonPathSegment {
  const _JsonWildcardSegment();
}

final class _SafeRegexPolicy {
  static void validate(
    SourceRegexCapture capture,
    String input,
    SourceResourceBudget budget,
  ) {
    final pattern = capture.pattern;
    if (pattern.length > budget.maxRegexPatternChars) {
      throw SourceRuleSecurityException(
        'regex_pattern_budget_exceeded',
        'Regular-expression pattern is too long.',
      );
    }
    if (input.length > budget.maxRegexInputChars) {
      throw SourceRuleSecurityException(
        'regex_input_budget_exceeded',
        'Regular-expression input is too long.',
      );
    }
    if (pattern.contains('(?')) {
      throw SourceRuleSecurityException(
        'regex_lookaround_disallowed',
        'Lookaround, named groups and inline modes are not allowed.',
      );
    }
    if (RegExp(r'\\[1-9]').hasMatch(pattern) || pattern.contains(r'\k<')) {
      throw SourceRuleSecurityException(
        'regex_backreference_disallowed',
        'Backreferences are not allowed.',
      );
    }
    final nestedQuantifier = RegExp(
      r'\((?:\\.|[^()])*(?:[*+?]|\{\d+(?:,\d*)?\})'
      r'(?:\\.|[^()])*\)(?:[*+?]|\{\d+(?:,\d*)?\})',
    );
    if (nestedQuantifier.hasMatch(pattern)) {
      throw SourceRuleSecurityException(
        'regex_nested_quantifier_disallowed',
        'Nested quantifiers are not allowed.',
      );
    }
    final quantifiedAlternation = RegExp(
      r'\((?:\\.|[^()])*\|(?:\\.|[^()])*\)'
      r'(?:[*+?]|\{\d+(?:,\d*)?\})',
    );
    if (quantifiedAlternation.hasMatch(pattern)) {
      throw SourceRuleSecurityException(
        'regex_quantified_alternation_disallowed',
        'Quantified alternations are not allowed.',
      );
    }
    final repeatedWildcard = RegExp(r'(?:\.\*|\.\+).*(?:\.\*|\.\+)');
    if (repeatedWildcard.hasMatch(pattern)) {
      throw SourceRuleSecurityException(
        'regex_repeated_wildcard_disallowed',
        'Repeated wildcard quantifiers are not allowed.',
      );
    }
  }
}
