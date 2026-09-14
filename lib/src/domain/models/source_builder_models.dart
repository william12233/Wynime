import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

import 'source_rule_program.dart';
import 'source_security_policy.dart';

enum SourceBuilderDiagnosticSeverity { info, warning, error }

enum SourceBuilderProposalState { reviewRequired, rejected }

const _maxExpectedRecords = 1000;
const _maxExpectedRecordFields = 64;
const _maxRequestObservations = 8;
const _maxRequestFields = 64;

List<T> _boundedIterableToList<T>(
  Iterable<T> values, {
  required int maxLength,
  required String name,
  required String message,
}) {
  final result = <T>[];
  final iterator = values.iterator;
  while (iterator.moveNext()) {
    if (result.length == maxLength) {
      throw ArgumentError(message, name);
    }
    result.add(iterator.current);
  }
  return List<T>.unmodifiable(result);
}

final class SourceBuilderFieldHint {
  SourceBuilderFieldHint({
    required String name,
    required this.valueKind,
    String? attributeName,
    this.isRequired = true,
  }) : name = name.trim(),
       attributeName = attributeName?.trim() {
    if (!RegExp(r'^[A-Za-z][A-Za-z0-9_]{0,63}$').hasMatch(this.name)) {
      throw ArgumentError.value(
        name,
        'name',
        'Must be an identifier with at most 64 characters.',
      );
    }
    if (valueKind == SourceValueKind.attribute) {
      final attribute = this.attributeName ?? '';
      if (!RegExp(r'^[A-Za-z_:][-A-Za-z0-9_:.]{0,63}$').hasMatch(attribute)) {
        throw ArgumentError.value(
          attributeName,
          'attributeName',
          'A valid attribute name with at most 64 characters is required for attribute fields.',
        );
      }
    } else if (this.attributeName != null) {
      throw ArgumentError('attributeName is valid only for attribute fields.');
    }
  }

  final String name;
  final SourceValueKind valueKind;
  final String? attributeName;
  final bool isRequired;
}

final class SourceBuilderObservation {
  factory SourceBuilderObservation({
    required SourceFixture fixture,
    required Iterable<Map<String, String>> expectedRecords,
  }) {
    final records = <Map<String, String>>[];
    final iterator = expectedRecords.iterator;
    while (iterator.moveNext()) {
      if (records.length == _maxExpectedRecords) {
        throw ArgumentError(
          'Must contain between 1 and $_maxExpectedRecords records.',
          'expectedRecords',
        );
      }
      final copy = <String, String>{};
      var fieldCount = 0;
      final recordIterator = iterator.current.entries.iterator;
      while (recordIterator.moveNext()) {
        if (fieldCount == _maxExpectedRecordFields) {
          throw ArgumentError(
            'Each expected record may contain at most $_maxExpectedRecordFields fields.',
            'expectedRecords',
          );
        }
        fieldCount++;
        final entry = recordIterator.current;
        final name = entry.key.trim();
        final value = entry.value.trim();
        if (name.isEmpty || name.length > 64) {
          throw ArgumentError.value(
            entry.key,
            'expectedRecords',
            'Expected field names must be bounded identifiers.',
          );
        }
        if (value.isEmpty || value.length > 8192) {
          throw ArgumentError.value(
            entry.value,
            'expectedRecords',
            'Expected field values must contain between 1 and 8192 characters.',
          );
        }
        if (copy.containsKey(name)) {
          throw ArgumentError.value(
            entry.key,
            'expectedRecords',
            'Expected field names must be unique after trimming.',
          );
        }
        copy[name] = value;
      }
      records.add(Map<String, String>.unmodifiable(copy));
    }
    if (records.isEmpty) {
      throw ArgumentError(
        'Must contain between 1 and $_maxExpectedRecords records.',
        'expectedRecords',
      );
    }
    return SourceBuilderObservation._(
      fixture: fixture,
      expectedRecords: records,
    );
  }

  SourceBuilderObservation._({
    required this.fixture,
    required List<Map<String, String>> expectedRecords,
  }) : expectedRecords = UnmodifiableListView(expectedRecords);

  final SourceFixture fixture;
  final UnmodifiableListView<Map<String, String>> expectedRecords;
}

final class SourceBuilderRequest {
  factory SourceBuilderRequest({
    required String packageId,
    required String displayName,
    required Version version,
    required VersionConstraint wynimeVersionConstraint,
    required SourceDocumentKind documentKind,
    required Iterable<SourceBuilderObservation> observations,
    required Iterable<SourceBuilderFieldHint> fields,
    SourceSecurityPolicy? previousSecurityPolicy,
    SourceResourceBudget? proposedBudget,
    bool allowObservedDomains = false,
    bool allowInsecureHttp = false,
    int resultLimit = 20,
  }) {
    final boundedObservations = _boundedIterableToList(
      observations,
      maxLength: _maxRequestObservations,
      name: 'observations',
      message:
          'Must contain between 1 and $_maxRequestObservations bounded observations.',
    );
    final boundedFields = _boundedIterableToList(
      fields,
      maxLength: _maxRequestFields,
      name: 'fields',
      message: 'Must contain between 1 and $_maxRequestFields field hints.',
    );
    return SourceBuilderRequest._(
      packageId: packageId,
      displayName: displayName,
      version: version,
      wynimeVersionConstraint: wynimeVersionConstraint,
      documentKind: documentKind,
      observations: boundedObservations,
      fields: boundedFields,
      previousSecurityPolicy: previousSecurityPolicy,
      proposedBudget: proposedBudget,
      allowObservedDomains: allowObservedDomains,
      allowInsecureHttp: allowInsecureHttp,
      resultLimit: resultLimit,
    );
  }

  SourceBuilderRequest._({
    required String packageId,
    required String displayName,
    required this.version,
    required this.wynimeVersionConstraint,
    required this.documentKind,
    required List<SourceBuilderObservation> observations,
    required List<SourceBuilderFieldHint> fields,
    this.previousSecurityPolicy,
    this.proposedBudget,
    this.allowObservedDomains = false,
    this.allowInsecureHttp = false,
    this.resultLimit = 20,
  }) : packageId = packageId.trim(),
       displayName = displayName.trim(),
       observations = UnmodifiableListView(observations),
       fields = UnmodifiableListView(fields) {
    if (!RegExp(r'^[a-z0-9]+(?:[._-][a-z0-9]+)*$').hasMatch(this.packageId) ||
        this.packageId.length > 128) {
      throw ArgumentError.value(
        packageId,
        'packageId',
        'Must be a lower-case package identifier.',
      );
    }
    if (this.displayName.isEmpty || this.displayName.length > 80) {
      throw ArgumentError.value(
        displayName,
        'displayName',
        'Must contain between 1 and 80 characters.',
      );
    }
    if (this.observations.isEmpty ||
        this.observations.length > _maxRequestObservations) {
      throw ArgumentError.value(
        observations,
        'observations',
        'Must contain between 1 and $_maxRequestObservations bounded observations.',
      );
    }
    if (this.fields.isEmpty || this.fields.length > _maxRequestFields) {
      throw ArgumentError.value(
        fields,
        'fields',
        'Must contain between 1 and $_maxRequestFields field hints.',
      );
    }
    if (resultLimit <= 0 || resultLimit > 1000) {
      throw ArgumentError.value(
        resultLimit,
        'resultLimit',
        'Must be between 1 and 1000.',
      );
    }
    final fieldNames = <String>{};
    for (final field in this.fields) {
      if (!fieldNames.add(field.name)) {
        throw ArgumentError.value(
          field.name,
          'fields',
          'Field names must be unique.',
        );
      }
    }
  }

  final String packageId;
  final String displayName;
  final Version version;
  final VersionConstraint wynimeVersionConstraint;
  final SourceDocumentKind documentKind;
  final UnmodifiableListView<SourceBuilderObservation> observations;
  final UnmodifiableListView<SourceBuilderFieldHint> fields;
  final SourceSecurityPolicy? previousSecurityPolicy;
  final SourceResourceBudget? proposedBudget;
  final bool allowObservedDomains;
  final bool allowInsecureHttp;
  final int resultLimit;
}

final class SourceBuilderDiagnostic {
  SourceBuilderDiagnostic({
    required String code,
    required String message,
    required this.severity,
    this.observationIndex,
    this.fieldName,
  }) : code = code.trim(),
       message = message.trim() {
    if (this.code.isEmpty || this.code.length > 64) {
      throw ArgumentError.value(code, 'code', 'Diagnostic code is invalid.');
    }
    if (this.message.isEmpty || this.message.length > 256) {
      throw ArgumentError.value(
        message,
        'message',
        'Diagnostic message is invalid.',
      );
    }
    if (observationIndex != null && observationIndex! < 0) {
      throw ArgumentError.value(
        observationIndex,
        'observationIndex',
        'Observation index must not be negative.',
      );
    }
  }

  final String code;
  final String message;
  final SourceBuilderDiagnosticSeverity severity;
  final int? observationIndex;
  final String? fieldName;
}
