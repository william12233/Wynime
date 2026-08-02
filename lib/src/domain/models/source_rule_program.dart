import 'dart:collection';

import 'source_security_policy.dart';

enum SourceDocumentKind { html, json }

enum SourceSelectorKind { css, jsonPath }

enum SourceValueKind { text, attribute, raw }

final class SourceSelector {
  SourceSelector({required this.kind, required String expression})
    : expression = expression.trim() {
    if (this.expression.isEmpty || this.expression.length > 512) {
      throw ArgumentError.value(
        expression,
        'expression',
        'Must contain between 1 and 512 characters.',
      );
    }
  }

  final SourceSelectorKind kind;
  final String expression;
}

final class SourceRegexCapture {
  SourceRegexCapture({
    required String pattern,
    this.group = 1,
    this.caseSensitive = true,
  }) : pattern = pattern.trim() {
    if (this.pattern.isEmpty) {
      throw ArgumentError.value(pattern, 'pattern', 'Must not be empty.');
    }
    if (group < 0) {
      throw ArgumentError.value(group, 'group', 'Must not be negative.');
    }
  }

  final String pattern;
  final int group;
  final bool caseSensitive;
}

final class SourceFieldRule {
  SourceFieldRule({
    required String name,
    this.selector,
    required this.valueKind,
    this.attributeName,
    this.required = false,
    this.regexCapture,
  }) : name = name.trim() {
    if (this.name.isEmpty ||
        !RegExp(r'^[A-Za-z][A-Za-z0-9_]{0,63}$').hasMatch(this.name)) {
      throw ArgumentError.value(
        name,
        'name',
        'Must be an identifier with at most 64 characters.',
      );
    }
    if (valueKind == SourceValueKind.attribute) {
      final attribute = attributeName?.trim() ?? '';
      if (attribute.isEmpty ||
          !RegExp(r'^[A-Za-z_:][-A-Za-z0-9_:.]*$').hasMatch(attribute)) {
        throw ArgumentError.value(
          attributeName,
          'attributeName',
          'A valid attribute name is required.',
        );
      }
    } else if (attributeName != null) {
      throw ArgumentError(
        'attributeName is valid only for attribute value rules.',
      );
    }
  }

  final String name;
  final SourceSelector? selector;
  final SourceValueKind valueKind;
  final String? attributeName;
  final bool required;
  final SourceRegexCapture? regexCapture;
}

final class SourceRuleProgram {
  SourceRuleProgram({
    required String programId,
    required this.documentKind,
    required this.rootSelector,
    required Iterable<SourceFieldRule> fields,
    required this.resultLimit,
  }) : programId = programId.trim(),
       fields = UnmodifiableListView(List<SourceFieldRule>.unmodifiable(fields)) {
    if (this.programId.isEmpty ||
        !RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(this.programId)) {
      throw ArgumentError.value(
        programId,
        'programId',
        'Must be a lower-case identifier.',
      );
    }
    if (this.fields.isEmpty) {
      throw ArgumentError.value(fields, 'fields', 'Must not be empty.');
    }
    if (resultLimit <= 0 || resultLimit > 1000) {
      throw ArgumentError.value(
        resultLimit,
        'resultLimit',
        'Must be between 1 and 1000.',
      );
    }
    final names = <String>{};
    for (final field in this.fields) {
      if (!names.add(field.name)) {
        throw ArgumentError.value(
          field.name,
          'fields',
          'Field names must be unique.',
        );
      }
    }
    _validateSelectorKinds();
  }

  final String programId;
  final SourceDocumentKind documentKind;
  final SourceSelector rootSelector;
  final UnmodifiableListView<SourceFieldRule> fields;
  final int resultLimit;

  void _validateSelectorKinds() {
    final requiredKind = switch (documentKind) {
      SourceDocumentKind.html => SourceSelectorKind.css,
      SourceDocumentKind.json => SourceSelectorKind.jsonPath,
    };
    if (rootSelector.kind != requiredKind) {
      throw ArgumentError(
        '$documentKind programs require $requiredKind root selectors.',
      );
    }
    for (final field in fields) {
      if (field.selector != null && field.selector!.kind != requiredKind) {
        throw ArgumentError(
          '$documentKind programs require $requiredKind field selectors.',
        );
      }
      if (documentKind == SourceDocumentKind.json &&
          field.valueKind != SourceValueKind.raw) {
        throw ArgumentError('JSON fields must use raw value extraction.');
      }
    }
  }
}

final class SourceFixture {
  SourceFixture({
    required this.initialUri,
    Iterable<Uri> redirectChain = const [],
    required this.body,
  }) : redirectChain = UnmodifiableListView(List<Uri>.unmodifiable(redirectChain));

  final Uri initialUri;
  final UnmodifiableListView<Uri> redirectChain;
  final String body;
}

final class SourceEvaluationRecord {
  SourceEvaluationRecord(Map<String, String> values)
    : values = UnmodifiableMapView(Map<String, String>.unmodifiable(values));

  final UnmodifiableMapView<String, String> values;
}

final class SourceEvaluationDiagnostic {
  SourceEvaluationDiagnostic({
    required this.code,
    required this.message,
    this.recordIndex,
    this.fieldName,
  });

  final String code;
  final String message;
  final int? recordIndex;
  final String? fieldName;
}

final class SourceEvaluationResult {
  SourceEvaluationResult({
    required Iterable<SourceEvaluationRecord> records,
    required Iterable<SourceEvaluationDiagnostic> diagnostics,
    required this.consumedSteps,
    required this.selectorMatches,
  }) : records = UnmodifiableListView(List<SourceEvaluationRecord>.unmodifiable(records)),
       diagnostics = UnmodifiableListView(
         List<SourceEvaluationDiagnostic>.unmodifiable(diagnostics),
       );

  final UnmodifiableListView<SourceEvaluationRecord> records;
  final UnmodifiableListView<SourceEvaluationDiagnostic> diagnostics;
  final int consumedSteps;
  final int selectorMatches;
}

final class SourceRuleSecurityException implements Exception {
  SourceRuleSecurityException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'SourceRuleSecurityException($code): $message';
}

final class SourceRuleEvaluationException implements Exception {
  SourceRuleEvaluationException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'SourceRuleEvaluationException($code): $message';
}
