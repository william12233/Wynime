import 'dart:convert';

import 'package:pub_semver/pub_semver.dart';

import '../../domain/models/source_package_manifest.dart';
import '../../domain/models/source_rule_program.dart';
import '../../domain/models/source_security_policy.dart';

final class SourcePackageDecoder {
  const SourcePackageDecoder();

  static const maxPackageBytes = 256 * 1024;

  SourcePackageManifest decode(String source) {
    final sourceBytes = utf8.encode(source).length;
    if (sourceBytes > maxPackageBytes) {
      throw SourcePackageFormatException(
        r'$',
        'Source package exceeds the $maxPackageBytes-byte limit.',
      );
    }

    final Object? decoded;
    try {
      decoded = jsonDecode(source);
    } on FormatException catch (error) {
      throw SourcePackageFormatException(
        r'$',
        'Invalid JSON: ${error.message}',
      );
    }

    final root = _asMap(decoded, r'$');
    _expectKeys(root, r'$', {
      'schemaVersion',
      'packageId',
      'displayName',
      'version',
      'wynimeVersion',
      'security',
      'programs',
      'signature',
    });

    final version = _parseVersion(
      _requiredString(root, 'version', r'$'),
      r'$.version',
    );
    final constraint = _parseConstraint(
      _requiredString(root, 'wynimeVersion', r'$'),
      r'$.wynimeVersion',
    );
    final security = _decodeSecurity(_required(root, 'security', r'$'));
    final programs = _asList(_required(root, 'programs', r'$'), r'$.programs')
        .indexed
        .map((entry) => _decodeProgram(entry.$2, r'$.programs[${entry.$1}]'))
        .toList(growable: false);

    final signatureValue = root['signature'];
    final signature = signatureValue == null
        ? null
        : _decodeSignature(signatureValue, r'$.signature');

    try {
      return SourcePackageManifest(
        schemaVersion: _requiredInt(root, 'schemaVersion', r'$'),
        packageId: _requiredString(root, 'packageId', r'$'),
        displayName: _requiredString(root, 'displayName', r'$'),
        version: version,
        wynimeVersionConstraint: constraint,
        securityPolicy: security,
        programs: programs,
        signatureMetadata: signature,
      );
    } on ArgumentError catch (error) {
      throw SourcePackageFormatException(
        r'$',
        error.message?.toString() ?? '$error',
      );
    }
  }

  SourceSecurityPolicy _decodeSecurity(Object? value) {
    const path = r'$.security';
    final map = _asMap(value, path);
    _expectKeys(map, path, {'domains', 'permissions', 'budget'});

    final permissions =
        _asList(
          _required(map, 'permissions', path),
          '$path.permissions',
        ).indexed.map((entry) {
          final permissionName = _asString(
            entry.$2,
            '$path.permissions[${entry.$1}]',
          );
          try {
            return SourcePermission.values.byName(permissionName);
          } on ArgumentError {
            throw SourcePackageFormatException(
              '$path.permissions[${entry.$1}]',
              'Unsupported permission: $permissionName',
            );
          }
        }).toSet();

    final domains = _asList(_required(map, 'domains', path), '$path.domains')
        .indexed
        .map((entry) {
          final domainPath = '$path.domains[${entry.$1}]';
          final domain = _asMap(entry.$2, domainPath);
          _expectKeys(domain, domainPath, {
            'host',
            'includeSubdomains',
            'schemes',
          });
          final schemes =
              _asList(
                    _required(domain, 'schemes', domainPath),
                    '$domainPath.schemes',
                  ).indexed
                  .map(
                    (scheme) => _asString(
                      scheme.$2,
                      '$domainPath.schemes[${scheme.$1}]',
                    ),
                  )
                  .toSet();
          try {
            return SourceDomainRule(
              host: _requiredString(domain, 'host', domainPath),
              includeSubdomains: _requiredBool(
                domain,
                'includeSubdomains',
                domainPath,
              ),
              schemes: schemes,
            );
          } on ArgumentError catch (error) {
            throw SourcePackageFormatException(
              domainPath,
              error.message?.toString() ?? '$error',
            );
          }
        })
        .toList(growable: false);

    final budgetPath = '$path.budget';
    final budgetMap = _asMap(_required(map, 'budget', path), budgetPath);
    _expectKeys(budgetMap, budgetPath, {
      'maxDocumentBytes',
      'maxRecords',
      'maxSelectorMatches',
      'maxEvaluationSteps',
      'maxRegexPatternChars',
      'maxRegexInputChars',
      'maxRedirects',
    });

    try {
      return SourceSecurityPolicy(
        allowedDomains: domains,
        permissions: permissions,
        budget: SourceResourceBudget(
          maxDocumentBytes: _requiredInt(
            budgetMap,
            'maxDocumentBytes',
            budgetPath,
          ),
          maxRecords: _requiredInt(budgetMap, 'maxRecords', budgetPath),
          maxSelectorMatches: _requiredInt(
            budgetMap,
            'maxSelectorMatches',
            budgetPath,
          ),
          maxEvaluationSteps: _requiredInt(
            budgetMap,
            'maxEvaluationSteps',
            budgetPath,
          ),
          maxRegexPatternChars: _requiredInt(
            budgetMap,
            'maxRegexPatternChars',
            budgetPath,
          ),
          maxRegexInputChars: _requiredInt(
            budgetMap,
            'maxRegexInputChars',
            budgetPath,
          ),
          maxRedirects: _requiredInt(budgetMap, 'maxRedirects', budgetPath),
        ),
      );
    } on ArgumentError catch (error) {
      throw SourcePackageFormatException(
        path,
        error.message?.toString() ?? '$error',
      );
    }
  }

  SourceRuleProgram _decodeProgram(Object? value, String path) {
    final map = _asMap(value, path);
    _expectKeys(map, path, {
      'id',
      'documentKind',
      'root',
      'resultLimit',
      'fields',
    });
    final documentKindName = _requiredString(map, 'documentKind', path);
    final SourceDocumentKind documentKind;
    try {
      documentKind = SourceDocumentKind.values.byName(documentKindName);
    } on ArgumentError {
      throw SourcePackageFormatException(
        '$path.documentKind',
        'Unsupported document kind: $documentKindName',
      );
    }
    final fields = _asList(_required(map, 'fields', path), '$path.fields')
        .indexed
        .map((entry) => _decodeField(entry.$2, '$path.fields[${entry.$1}]'));
    try {
      return SourceRuleProgram(
        programId: _requiredString(map, 'id', path),
        documentKind: documentKind,
        rootSelector: _decodeSelector(
          _required(map, 'root', path),
          '$path.root',
        ),
        fields: fields,
        resultLimit: _requiredInt(map, 'resultLimit', path),
      );
    } on ArgumentError catch (error) {
      throw SourcePackageFormatException(
        path,
        error.message?.toString() ?? '$error',
      );
    }
  }

  SourceFieldRule _decodeField(Object? value, String path) {
    final map = _asMap(value, path);
    _expectKeys(map, path, {
      'name',
      'selector',
      'value',
      'attribute',
      'required',
      'regex',
    });
    final valueName = _requiredString(map, 'value', path);
    final SourceValueKind valueKind;
    try {
      valueKind = SourceValueKind.values.byName(valueName);
    } on ArgumentError {
      throw SourcePackageFormatException(
        '$path.value',
        'Unsupported value extraction: $valueName',
      );
    }
    final selectorValue = map['selector'];
    final regexValue = map['regex'];
    try {
      return SourceFieldRule(
        name: _requiredString(map, 'name', path),
        selector: selectorValue == null
            ? null
            : _decodeSelector(selectorValue, '$path.selector'),
        valueKind: valueKind,
        attributeName: map['attribute'] == null
            ? null
            : _asString(map['attribute'], '$path.attribute'),
        required: _requiredBool(map, 'required', path),
        regexCapture: regexValue == null
            ? null
            : _decodeRegex(regexValue, '$path.regex'),
      );
    } on ArgumentError catch (error) {
      throw SourcePackageFormatException(
        path,
        error.message?.toString() ?? '$error',
      );
    }
  }

  SourceSelector _decodeSelector(Object? value, String path) {
    final map = _asMap(value, path);
    _expectKeys(map, path, {'type', 'expression'});
    final type = _requiredString(map, 'type', path);
    if (type == 'xpath') {
      throw SourcePackageFormatException(
        '$path.type',
        'XPath is intentionally unsupported in source schema version 1.',
      );
    }
    final SourceSelectorKind kind;
    try {
      kind = SourceSelectorKind.values.byName(type);
    } on ArgumentError {
      throw SourcePackageFormatException(
        '$path.type',
        'Unsupported selector type: $type',
      );
    }
    try {
      return SourceSelector(
        kind: kind,
        expression: _requiredString(map, 'expression', path),
      );
    } on ArgumentError catch (error) {
      throw SourcePackageFormatException(
        path,
        error.message?.toString() ?? '$error',
      );
    }
  }

  SourceRegexCapture _decodeRegex(Object? value, String path) {
    final map = _asMap(value, path);
    _expectKeys(map, path, {'pattern', 'group', 'caseSensitive'});
    try {
      return SourceRegexCapture(
        pattern: _requiredString(map, 'pattern', path),
        group: _requiredInt(map, 'group', path),
        caseSensitive: _requiredBool(map, 'caseSensitive', path),
      );
    } on ArgumentError catch (error) {
      throw SourcePackageFormatException(
        path,
        error.message?.toString() ?? '$error',
      );
    }
  }

  SourcePackageSignatureMetadata _decodeSignature(Object? value, String path) {
    final map = _asMap(value, path);
    _expectKeys(map, path, {
      'declaredSignerId',
      'keyId',
      'algorithm',
      'signatureBase64',
    });
    final algorithm = _requiredString(map, 'algorithm', path);
    if (algorithm != 'ed25519') {
      throw SourcePackageFormatException(
        '$path.algorithm',
        'Only ed25519 signature metadata is accepted in schema version 1.',
      );
    }
    final signature = _requiredString(map, 'signatureBase64', path);
    try {
      if (base64Decode(signature).length != 64) {
        throw const FormatException(
          'Ed25519 signatures must contain 64 bytes.',
        );
      }
    } on FormatException catch (error) {
      throw SourcePackageFormatException(
        '$path.signatureBase64',
        'Invalid base64 signature: ${error.message}',
      );
    }
    try {
      return SourcePackageSignatureMetadata(
        declaredSignerId: _requiredString(map, 'declaredSignerId', path),
        keyId: _requiredString(map, 'keyId', path),
        algorithm: algorithm,
        signatureBase64: signature,
      );
    } on ArgumentError catch (error) {
      throw SourcePackageFormatException(
        path,
        error.message?.toString() ?? '$error',
      );
    }
  }

  Version _parseVersion(String value, String path) {
    try {
      return Version.parse(value);
    } on FormatException catch (error) {
      throw SourcePackageFormatException(path, error.message);
    }
  }

  VersionConstraint _parseConstraint(String value, String path) {
    try {
      return VersionConstraint.parse(value);
    } on FormatException catch (error) {
      throw SourcePackageFormatException(path, error.message);
    }
  }

  static Object? _required(Map<String, Object?> map, String key, String path) {
    if (!map.containsKey(key) || map[key] == null) {
      throw SourcePackageFormatException(
        '$path.$key',
        'Required value is missing.',
      );
    }
    return map[key];
  }

  static String _requiredString(
    Map<String, Object?> map,
    String key,
    String path,
  ) => _asString(_required(map, key, path), '$path.$key');

  static int _requiredInt(Map<String, Object?> map, String key, String path) {
    final value = _required(map, key, path);
    if (value is! int) {
      throw SourcePackageFormatException('$path.$key', 'Expected an integer.');
    }
    return value;
  }

  static bool _requiredBool(Map<String, Object?> map, String key, String path) {
    final value = _required(map, key, path);
    if (value is! bool) {
      throw SourcePackageFormatException('$path.$key', 'Expected a boolean.');
    }
    return value;
  }

  static String _asString(Object? value, String path) {
    if (value is! String) {
      throw SourcePackageFormatException(path, 'Expected a string.');
    }
    return value;
  }

  static Map<String, Object?> _asMap(Object? value, String path) {
    if (value is! Map) {
      throw SourcePackageFormatException(path, 'Expected an object.');
    }
    final result = <String, Object?>{};
    for (final entry in value.entries) {
      if (entry.key is! String) {
        throw SourcePackageFormatException(
          path,
          'Object keys must be strings.',
        );
      }
      result[entry.key as String] = entry.value;
    }
    return result;
  }

  static List<Object?> _asList(Object? value, String path) {
    if (value is! List) {
      throw SourcePackageFormatException(path, 'Expected an array.');
    }
    return value.cast<Object?>();
  }

  static void _expectKeys(
    Map<String, Object?> map,
    String path,
    Set<String> allowed,
  ) {
    final unknown = map.keys.where((key) => !allowed.contains(key)).toList();
    if (unknown.isNotEmpty) {
      throw SourcePackageFormatException(
        path,
        'Unsupported keys: ${unknown.join(', ')}',
      );
    }
  }
}
