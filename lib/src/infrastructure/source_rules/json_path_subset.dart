import '../../domain/models/source_rule_program.dart';
import 'evaluation_budget_meter.dart';

final class JsonPathSubset {
  const JsonPathSubset._();

  static List<Object?> evaluate(
    Object? root,
    String expression,
    EvaluationBudgetMeter meter,
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
