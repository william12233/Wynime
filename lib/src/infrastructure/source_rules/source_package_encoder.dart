import 'dart:convert';

import '../../domain/models/source_package_manifest.dart';
import '../../domain/models/source_package_live_operations.dart';
import '../../domain/models/source_episode_normalization_models.dart';
import '../../domain/models/source_playable_normalization_models.dart';
import '../../domain/models/source_rule_program.dart';
import '../../domain/models/source_search_normalization_models.dart';
import 'source_package_decoder.dart';

/// Encodes a validated source package without adding source-provided
/// executable content. Schema-v2 live-operation declarations are included in
/// the canonical payload covered by package signatures.
final class SourcePackageEncoder {
  const SourcePackageEncoder();

  String encode(SourcePackageManifest package) {
    final encoded = jsonEncode(toMap(package));
    if (utf8.encode(encoded).length > SourcePackageDecoder.maxPackageBytes) {
      throw ArgumentError.value(
        package,
        'package',
        'The encoded source package exceeds the package byte limit.',
      );
    }
    return encoded;
  }

  /// Encodes the canonical bytes covered by an optional package signature.
  ///
  /// The signature metadata is deliberately omitted so the signed payload
  /// cannot contain its own signature recursively.
  String encodeSignaturePayload(SourcePackageManifest package) {
    final encoded = jsonEncode(toMap(package, includeSignature: false));
    if (utf8.encode(encoded).length > SourcePackageDecoder.maxPackageBytes) {
      throw ArgumentError.value(
        package,
        'package',
        'The canonical source package signature payload exceeds the package byte limit.',
      );
    }
    return encoded;
  }

  Map<String, Object?> toMap(
    SourcePackageManifest package, {
    bool includeSignature = true,
  }) {
    final result = <String, Object?>{
      'schemaVersion': package.schemaVersion,
      'packageId': package.packageId,
      'displayName': package.displayName,
      'version': package.version.toString(),
      'wynimeVersion': package.wynimeVersionConstraint.toString(),
      'security': {
        'domains': package.securityPolicy.allowedDomains
            .map(
              (domain) => {
                'host': domain.host,
                'includeSubdomains': domain.includeSubdomains,
                'schemes': domain.schemes.toList()..sort(),
              },
            )
            .toList(growable: false),
        'permissions':
            package.securityPolicy.permissions
                .map((permission) => permission.name)
                .toList()
              ..sort(),
        'budget': {
          'maxDocumentBytes': package.securityPolicy.budget.maxDocumentBytes,
          'maxRecords': package.securityPolicy.budget.maxRecords,
          'maxSelectorMatches':
              package.securityPolicy.budget.maxSelectorMatches,
          'maxEvaluationSteps':
              package.securityPolicy.budget.maxEvaluationSteps,
          'maxRegexPatternChars':
              package.securityPolicy.budget.maxRegexPatternChars,
          'maxRegexInputChars':
              package.securityPolicy.budget.maxRegexInputChars,
          'maxRedirects': package.securityPolicy.budget.maxRedirects,
        },
      },
      'programs': package.programs.map(_encodeProgram).toList(growable: false),
    };
    if (package.schemaVersion == 2) {
      final operations = package.liveOperations.toList()
        ..sort((left, right) => left.kind.index.compareTo(right.kind.index));
      result['liveOperations'] = operations
          .map(_encodeLiveOperation)
          .toList(growable: false);
    }
    if (includeSignature) {
      result['signature'] = package.signatureMetadata == null
          ? null
          : {
              'declaredSignerId': package.signatureMetadata!.declaredSignerId,
              'keyId': package.signatureMetadata!.keyId,
              'algorithm': package.signatureMetadata!.algorithm,
              'signatureBase64': package.signatureMetadata!.signatureBase64,
            };
    }
    return result;
  }

  Map<String, Object?> _encodeProgram(SourceRuleProgram program) {
    return {
      'id': program.programId,
      'documentKind': program.documentKind.name,
      'root': {
        'type': program.rootSelector.kind.name,
        'expression': program.rootSelector.expression,
      },
      'resultLimit': program.resultLimit,
      'fields': program.fields.map(_encodeField).toList(growable: false),
    };
  }

  Map<String, Object?> _encodeField(SourceFieldRule field) {
    return {
      'name': field.name,
      'selector': field.selector == null
          ? null
          : {
              'type': field.selector!.kind.name,
              'expression': field.selector!.expression,
            },
      'value': field.valueKind.name,
      'attribute': field.attributeName,
      'required': field.required,
      'regex': field.regexCapture == null
          ? null
          : {
              'pattern': field.regexCapture!.pattern,
              'group': field.regexCapture!.group,
              'caseSensitive': field.regexCapture!.caseSensitive,
            },
    };
  }

  Map<String, Object?> _encodeLiveOperation(
    SourcePackageLiveOperation operation,
  ) {
    final mapping = switch (operation.kind) {
      SourcePackageLiveOperationKind.search => _encodeSearchMapping(
        operation.mapping as SourceSearchFieldMapping,
      ),
      SourcePackageLiveOperationKind.episode => _encodeEpisodeMapping(
        operation.mapping as SourceEpisodeFieldMapping,
      ),
      SourcePackageLiveOperationKind.playableSource => _encodePlayableMapping(
        operation.mapping as SourcePlayableSourceFieldMapping,
      ),
    };
    return {
      'kind': operation.kind.name,
      'programId': operation.programId,
      'uriTemplate': operation.requestTemplate.template,
      'mapping': mapping,
    };
  }

  Map<String, Object?> _encodeSearchMapping(SourceSearchFieldMapping mapping) {
    return {
      'subjectIdField': mapping.subjectIdField,
      'titleField': mapping.titleField,
    };
  }

  Map<String, Object?> _encodeEpisodeMapping(
    SourceEpisodeFieldMapping mapping,
  ) {
    return {
      'lineIdField': mapping.lineIdField,
      'subjectIdField': mapping.subjectIdField,
      'episodeIdField': mapping.episodeIdField,
      'titleField': mapping.titleField,
    };
  }

  Map<String, Object?> _encodePlayableMapping(
    SourcePlayableSourceFieldMapping mapping,
  ) {
    return {
      'sourceKeyField': mapping.sourceKeyField,
      'labelField': mapping.labelField,
      'kindField': mapping.kindField,
      'mediaUriField': mapping.mediaUriField,
      'pageUriField': mapping.pageUriField,
    };
  }
}
