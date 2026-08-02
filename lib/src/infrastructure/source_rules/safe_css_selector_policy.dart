import '../../domain/models/source_rule_program.dart';

final class SafeCssSelectorPolicy {
  const SafeCssSelectorPolicy._();

  static void validate(String expression) {
    final selector = expression.trim();
    if (selector.isEmpty || selector.length > 256) {
      throw SourceRuleSecurityException(
        'css_selector_length_invalid',
        'CSS selectors must contain between 1 and 256 characters.',
      );
    }
    if (RegExp(r'[:,\[\]+~*]').hasMatch(selector)) {
      throw SourceRuleSecurityException(
        'css_selector_syntax_disallowed',
        'Pseudo, group, attribute, sibling and universal selectors are not allowed.',
      );
    }

    final tokens = selector
        .replaceAll('>', ' > ')
        .split(RegExp(r'\s+'))
        .where((token) => token.isNotEmpty)
        .toList(growable: false);
    if (tokens.isEmpty || tokens.first == '>' || tokens.last == '>') {
      throw SourceRuleEvaluationException(
        'invalid_css_selector',
        'CSS selector has an invalid combinator position.',
      );
    }

    var previousWasChild = false;
    for (final token in tokens) {
      if (token == '>') {
        if (previousWasChild) {
          throw SourceRuleEvaluationException(
            'invalid_css_selector',
            'Repeated child combinators are not allowed.',
          );
        }
        previousWasChild = true;
        continue;
      }
      if (!_isSimpleSelector(token)) {
        throw SourceRuleSecurityException(
          'css_selector_syntax_disallowed',
          'Only tag, id and class simple selectors are allowed.',
        );
      }
      previousWasChild = false;
    }
  }

  static bool _isSimpleSelector(String token) {
    final simple = RegExp(
      r'^(?:[A-Za-z][A-Za-z0-9_-]*)?'
      r'(?:#[A-Za-z_][A-Za-z0-9_-]*)?'
      r'(?:\.[A-Za-z_][A-Za-z0-9_-]*)*$',
    );
    return token.isNotEmpty && simple.hasMatch(token);
  }
}
