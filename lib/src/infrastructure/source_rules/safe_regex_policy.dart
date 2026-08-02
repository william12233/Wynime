import '../../domain/models/source_rule_program.dart';
import '../../domain/models/source_security_policy.dart';

final class SafeRegexPolicy {
  const SafeRegexPolicy._();

  static String? capture(
    SourceRegexCapture capture,
    String input,
    SourceResourceBudget budget,
  ) {
    _validatePattern(capture.pattern, input, budget);
    final RegExp expression;
    try {
      expression = RegExp(
        capture.pattern,
        caseSensitive: capture.caseSensitive,
      );
    } on FormatException catch (error) {
      throw SourceRuleEvaluationException('invalid_regex', error.message);
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

  static void _validatePattern(
    String pattern,
    String input,
    SourceResourceBudget budget,
  ) {
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
        'regex_extended_group_disallowed',
        'Lookaround, named groups, noncapturing groups and inline modes are not allowed.',
      );
    }
    if (pattern.contains('|')) {
      throw SourceRuleSecurityException(
        'regex_alternation_disallowed',
        'Alternation is not allowed in the bounded regex dialect.',
      );
    }
    if (RegExp(r'\\[1-9]').hasMatch(pattern) || pattern.contains(r'\k<')) {
      throw SourceRuleSecurityException(
        'regex_backreference_disallowed',
        'Backreferences are not allowed.',
      );
    }
    if (pattern.contains('.*') || pattern.contains('.+')) {
      throw SourceRuleSecurityException(
        'regex_wildcard_quantifier_disallowed',
        'Quantified wildcard atoms are not allowed.',
      );
    }
    if (pattern.contains('{') || pattern.contains('}')) {
      throw SourceRuleSecurityException(
        'regex_braced_quantifier_disallowed',
        'Braced quantifiers are not supported in schema version 1.',
      );
    }

    var escaped = false;
    var inClass = false;
    var groupDepth = 0;
    var unboundedQuantifiers = 0;
    for (var index = 0; index < pattern.length; index++) {
      final char = pattern[index];
      if (escaped) {
        escaped = false;
        continue;
      }
      if (char == r'\') {
        escaped = true;
        continue;
      }
      if (char == '[') {
        if (!inClass) {
          inClass = true;
        }
        continue;
      }
      if (char == ']' && inClass) {
        inClass = false;
        continue;
      }
      if (inClass) {
        continue;
      }
      if (char == '(') {
        groupDepth++;
        if (groupDepth > 1) {
          throw SourceRuleSecurityException(
            'regex_nested_group_disallowed',
            'Nested capture groups are not allowed.',
          );
        }
        continue;
      }
      if (char == ')') {
        groupDepth--;
        if (groupDepth < 0) {
          throw SourceRuleEvaluationException(
            'invalid_regex',
            'Unmatched closing parenthesis.',
          );
        }
        continue;
      }
      if (char == '*' || char == '+') {
        unboundedQuantifiers++;
        if (unboundedQuantifiers > 4 || !_hasSafeQuantifiedAtom(pattern, index)) {
          throw SourceRuleSecurityException(
            'regex_unbounded_quantifier_disallowed',
            'Unbounded quantifiers are limited to character classes and escaped character classes.',
          );
        }
      }
      if (char == '?' && index > 0 && pattern[index - 1] == ')') {
        throw SourceRuleSecurityException(
          'regex_group_quantifier_disallowed',
          'Capture groups may not be quantified.',
        );
      }
    }
    if (escaped || inClass || groupDepth != 0) {
      throw SourceRuleEvaluationException(
        'invalid_regex',
        'Regular-expression delimiters are unbalanced.',
      );
    }
  }

  static bool _hasSafeQuantifiedAtom(String pattern, int quantifierIndex) {
    if (quantifierIndex == 0) {
      return false;
    }
    final previous = pattern[quantifierIndex - 1];
    if (previous == ']') {
      return true;
    }
    if (!const {'d', 'D', 's', 'S', 'w', 'W'}.contains(previous) ||
        quantifierIndex < 2) {
      return false;
    }
    var slashCount = 0;
    for (var index = quantifierIndex - 2;
        index >= 0 && pattern[index] == r'\';
        index--) {
      slashCount++;
    }
    return slashCount.isOdd;
  }
}
