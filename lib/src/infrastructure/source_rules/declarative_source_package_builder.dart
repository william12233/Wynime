import 'dart:collection';
import 'dart:convert';
import 'dart:math' as math;

import 'package:crypto/crypto.dart';
import 'package:html/dom.dart';
import 'package:html/parser.dart' as html_parser;

import '../../domain/models/source_builder_models.dart';
import '../../domain/models/source_package_manifest.dart';
import '../../domain/models/source_rule_program.dart';
import '../../domain/models/source_security_policy.dart';
import 'evaluation_budget_meter.dart';
import 'json_path_subset.dart';
import 'safe_css_selector_policy.dart';
import 'source_fixture_rule_engine.dart';

abstract interface class SourcePackageBuilder {
  SourceBuilderProposal build(SourceBuilderRequest request);
}

final class SourceBuilderProposal {
  SourceBuilderProposal._generated({
    required String proposalId,
    required this.packageManifest,
    required Iterable<SourceBuilderDiagnostic> diagnostics,
    required this.requiresConsent,
    required this.requiresReconsent,
  }) : proposalId = proposalId.trim(),
       diagnostics = UnmodifiableListView(
         List<SourceBuilderDiagnostic>.unmodifiable(diagnostics),
       ),
       state = packageManifest == null
           ? SourceBuilderProposalState.rejected
           : SourceBuilderProposalState.reviewRequired {
    if (this.proposalId.isEmpty || this.proposalId.length > 128) {
      throw ArgumentError(
        'Proposal ID must be bounded and non-empty.',
        'proposalId',
      );
    }
  }

  final String proposalId;
  final SourcePackageManifest? packageManifest;
  final UnmodifiableListView<SourceBuilderDiagnostic> diagnostics;
  final bool requiresConsent;
  final bool requiresReconsent;
  final SourceBuilderProposalState state;

  bool get hasErrors => diagnostics.any(
    (diagnostic) =>
        diagnostic.severity == SourceBuilderDiagnosticSeverity.error,
  );

  // A generated package is never activated implicitly. The caller must pass
  // this proposal through SourcePackageActivationService after review.
  bool get canActivate => false;
}

final class SourcePackageActivationException implements Exception {
  SourcePackageActivationException(this.code, this.message);

  final String code;
  final String message;

  @override
  String toString() => 'SourcePackageActivationException($code): $message';
}

final class SourcePackageActivationService {
  const SourcePackageActivationService();

  SourcePackageManifest activate({
    required SourceBuilderProposal proposal,
    required String proposalId,
    required bool userApproved,
    required bool reconsentGranted,
  }) {
    if (proposal.proposalId != proposalId) {
      throw SourcePackageActivationException(
        'proposal_mismatch',
        'The approval does not match the generated proposal.',
      );
    }
    final package = proposal.packageManifest;
    if (package == null || proposal.hasErrors) {
      throw SourcePackageActivationException(
        'proposal_invalid',
        'Only an error-free generated proposal can be activated.',
      );
    }
    if (!userApproved) {
      throw SourcePackageActivationException(
        'user_approval_required',
        'Source package activation requires explicit user approval.',
      );
    }
    if (proposal.requiresReconsent && !reconsentGranted) {
      throw SourcePackageActivationException(
        'reconsent_required',
        'The security policy changed and requires fresh consent.',
      );
    }
    return package;
  }
}

final class DeclarativeSourcePackageBuilder implements SourcePackageBuilder {
  DeclarativeSourcePackageBuilder()
    : _evaluator = const SourceFixtureRuleEngine();

  static final _defaultBudget = SourceResourceBudget(
    maxDocumentBytes: 256 * 1024,
    maxRecords: 50,
    maxSelectorMatches: 500,
    maxEvaluationSteps: 5000,
    maxRegexPatternChars: 64,
    maxRegexInputChars: 1024,
    maxRedirects: 3,
  );

  static const _maxBuilderElements = 4096;
  static const _maxBuilderCandidates = 1024;
  static const _maxJsonDepth = 8;

  final SourceFixtureRuleEngine _evaluator;

  @override
  SourceBuilderProposal build(SourceBuilderRequest request) {
    final diagnostics = <SourceBuilderDiagnostic>[];
    final policyResult = _buildPolicy(request, diagnostics);
    if (policyResult == null) {
      return _proposal(
        request: request,
        package: null,
        diagnostics: diagnostics,
        requiresReconsent: false,
      );
    }
    final policy = policyResult.policy;

    final program = switch (request.documentKind) {
      SourceDocumentKind.html => _buildHtmlProgram(
        request,
        policy,
        diagnostics,
      ),
      SourceDocumentKind.json => _buildJsonProgram(
        request,
        policy,
        diagnostics,
      ),
    };
    if (program == null) {
      return _proposal(
        request: request,
        package: null,
        diagnostics: diagnostics,
        requiresReconsent: policyResult.requiresReconsent,
      );
    }

    final SourcePackageManifest package;
    try {
      package = SourcePackageManifest(
        schemaVersion: 1,
        packageId: request.packageId,
        displayName: request.displayName,
        version: request.version,
        wynimeVersionConstraint: request.wynimeVersionConstraint,
        securityPolicy: policy,
        programs: [program],
      );
    } on ArgumentError {
      diagnostics.add(
        _error(
          'generated_package_invalid',
          'The generated declarative package failed schema validation.',
        ),
      );
      return _proposal(
        request: request,
        package: null,
        diagnostics: diagnostics,
        requiresReconsent: policyResult.requiresReconsent,
      );
    }

    _verifyGeneratedPackage(request, package, diagnostics);
    if (diagnostics.any(
      (diagnostic) =>
          diagnostic.severity == SourceBuilderDiagnosticSeverity.error,
    )) {
      return _proposal(
        request: request,
        package: null,
        diagnostics: diagnostics,
        requiresReconsent: policyResult.requiresReconsent,
      );
    }

    diagnostics.add(
      SourceBuilderDiagnostic(
        code: 'proposal_generated',
        message:
            'Generated declarative rules require manual review and explicit consent.',
        severity: SourceBuilderDiagnosticSeverity.info,
      ),
    );
    return _proposal(
      request: request,
      package: package,
      diagnostics: diagnostics,
      requiresReconsent: policyResult.requiresReconsent,
    );
  }

  SourceBuilderProposal _proposal({
    required SourceBuilderRequest request,
    required SourcePackageManifest? package,
    required List<SourceBuilderDiagnostic> diagnostics,
    required bool requiresReconsent,
  }) {
    final fingerprint = package == null
        ? jsonEncode({
            'packageId': request.packageId,
            'version': request.version.toString(),
            'diagnostics': diagnostics.map((item) => item.code).toList(),
          })
        : jsonEncode(_packageFingerprintPayload(package));
    final digest = sha256.convert(utf8.encode(fingerprint)).toString();
    return SourceBuilderProposal._generated(
      proposalId: 'source-proposal-${digest.substring(0, 32)}',
      packageManifest: package,
      diagnostics: diagnostics,
      requiresConsent: true,
      requiresReconsent: requiresReconsent,
    );
  }

  _PolicyBuildResult? _buildPolicy(
    SourceBuilderRequest request,
    List<SourceBuilderDiagnostic> diagnostics,
  ) {
    final previous = request.previousSecurityPolicy;
    final budget = request.proposedBudget ?? previous?.budget ?? _defaultBudget;
    final permissions =
        previous?.permissions.toSet() ??
        <SourcePermission>{SourcePermission.network};
    final domains = previous?.allowedDomains.toList() ?? <SourceDomainRule>[];

    if (!permissions.contains(SourcePermission.network)) {
      diagnostics.add(
        _error(
          'network_permission_required',
          'The generated source package requires the network permission.',
        ),
      );
    }

    final fieldNames = request.fields.map((field) => field.name).toSet();
    for (
      var observationIndex = 0;
      observationIndex < request.observations.length;
      observationIndex++
    ) {
      final observation = request.observations[observationIndex];
      final bodyBytes = utf8.encode(observation.fixture.body).length;
      if (bodyBytes > budget.maxDocumentBytes) {
        diagnostics.add(
          _error(
            'document_budget_exceeded',
            'An observation exceeds the proposed document budget.',
            observationIndex: observationIndex,
          ),
        );
      }
      if (observation.fixture.redirectChain.length > budget.maxRedirects) {
        diagnostics.add(
          _error(
            'redirect_budget_exceeded',
            'An observation exceeds the proposed redirect budget.',
            observationIndex: observationIndex,
          ),
        );
      }
      if (observation.expectedRecords.length > request.resultLimit ||
          observation.expectedRecords.length > budget.maxRecords) {
        diagnostics.add(
          _error(
            'record_budget_exceeded',
            'An observation exceeds the proposed record limit.',
            observationIndex: observationIndex,
          ),
        );
      }
      for (final record in observation.expectedRecords) {
        for (final name in record.keys) {
          if (!fieldNames.contains(name)) {
            diagnostics.add(
              _error(
                'unknown_expected_field',
                'An observation contains a field without a matching hint.',
                observationIndex: observationIndex,
                fieldName: name,
              ),
            );
          }
        }
      }
    }

    if (request.documentKind == SourceDocumentKind.json) {
      for (final field in request.fields) {
        if (field.valueKind != SourceValueKind.raw) {
          diagnostics.add(
            _error(
              'json_field_kind_unsupported',
              'JSON source rules can only emit raw values in schema version 1.',
              fieldName: field.name,
            ),
          );
        }
      }
    }

    final canAddObservedDomains =
        previous == null || request.allowObservedDomains;
    final domainKeys = domains.map(_domainKey).toSet();
    final observedUris = <(int, Uri)>[];
    for (
      var observationIndex = 0;
      observationIndex < request.observations.length;
      observationIndex++
    ) {
      final observation = request.observations[observationIndex];
      observedUris.add((observationIndex, observation.fixture.initialUri));
      observedUris.addAll(
        observation.fixture.redirectChain.map((uri) => (observationIndex, uri)),
      );
    }

    for (final (observationIndex, uri) in observedUris) {
      if (!_isSafeObservedUri(uri)) {
        diagnostics.add(
          _error(
            'observed_uri_invalid',
            'An observation URI uses an unsupported scheme, host or port.',
            observationIndex: observationIndex,
          ),
        );
        continue;
      }

      final scheme = uri.scheme.toLowerCase();
      if (scheme == 'http' &&
          !permissions.contains(SourcePermission.insecureHttp)) {
        if (!request.allowInsecureHttp) {
          diagnostics.add(
            _error(
              'insecure_http_requires_explicit_opt_in',
              'HTTP observations require an explicit insecure-http opt-in.',
              observationIndex: observationIndex,
            ),
          );
          continue;
        }
        permissions.add(SourcePermission.insecureHttp);
      }

      if (domains.any((rule) => rule.allows(uri, permissions))) {
        continue;
      }
      if (!canAddObservedDomains) {
        diagnostics.add(
          _error(
            'observed_domain_not_allowed',
            'An observation is outside the existing source domain allowlist.',
            observationIndex: observationIndex,
          ),
        );
        continue;
      }
      try {
        final rule = SourceDomainRule(host: uri.host, schemes: {scheme});
        if (domainKeys.add(_domainKey(rule))) {
          domains.add(rule);
        }
      } on ArgumentError {
        diagnostics.add(
          _error(
            'observed_domain_invalid',
            'An observation host cannot be represented by the source schema.',
            observationIndex: observationIndex,
          ),
        );
      }
    }

    if (diagnostics.any(
      (diagnostic) =>
          diagnostic.severity == SourceBuilderDiagnosticSeverity.error,
    )) {
      return null;
    }

    try {
      final policy = SourceSecurityPolicy(
        allowedDomains: domains,
        permissions: permissions,
        budget: budget,
      );
      final requiresReconsent =
          previous != null && policy.requiresReconsentComparedTo(previous);
      return _PolicyBuildResult(
        policy: policy,
        requiresReconsent: requiresReconsent,
      );
    } on ArgumentError {
      diagnostics.add(
        _error(
          'generated_security_policy_invalid',
          'The generated source security policy failed validation.',
        ),
      );
      return null;
    }
  }

  SourceRuleProgram? _buildHtmlProgram(
    SourceBuilderRequest request,
    SourceSecurityPolicy policy,
    List<SourceBuilderDiagnostic> diagnostics,
  ) {
    final parsed = <_HtmlObservation>[];
    for (var index = 0; index < request.observations.length; index++) {
      final observation = request.observations[index];
      try {
        html_parser.HtmlParser(
          observation.fixture.body,
          strict: true,
          generateSpans: false,
        ).parseFragment();
        parsed.add(
          _HtmlObservation(
            observation: observation,
            document: html_parser.parse(
              observation.fixture.body,
              generateSpans: false,
            ),
          ),
        );
      } on Object {
        diagnostics.add(
          _error(
            'html_document_invalid',
            'An HTML observation could not be parsed.',
            observationIndex: index,
          ),
        );
      }
    }
    if (parsed.length != request.observations.length) {
      return null;
    }

    final selectors = <String>{};
    for (final item in parsed) {
      final elements = item.document
          .querySelectorAll('*')
          .take(_maxBuilderElements);
      for (final element in elements) {
        selectors.addAll(_selectorsForElement(element));
      }
    }
    final sortedSelectors = selectors.toList()..sort();
    final candidates = <_ProgramInference>[];
    for (final rootSelector in sortedSelectors) {
      try {
        SafeCssSelectorPolicy.validate(rootSelector);
      } on Object {
        continue;
      }
      final rootsByObservation = <List<Element>>[];
      var valid = true;
      for (final item in parsed) {
        final roots = item.document.querySelectorAll(rootSelector);
        if (roots.isEmpty ||
            roots.length < item.observation.expectedRecords.length ||
            roots.length > policy.budget.maxSelectorMatches) {
          valid = false;
          break;
        }
        rootsByObservation.add(roots);
      }
      if (!valid) {
        continue;
      }

      final fields = <SourceFieldRule>[];
      var score = _rootScore(rootSelector, rootsByObservation, parsed);
      for (final hint in request.fields) {
        final inference = _inferHtmlField(hint, rootsByObservation, parsed);
        if (inference == null) {
          valid = false;
          break;
        }
        fields.add(
          SourceFieldRule(
            name: hint.name,
            selector: inference.selector == null
                ? null
                : SourceSelector(
                    kind: SourceSelectorKind.css,
                    expression: inference.selector!,
                  ),
            valueKind: hint.valueKind,
            attributeName: hint.attributeName,
            required: hint.isRequired,
          ),
        );
        score += inference.score;
      }
      if (valid) {
        candidates.add(
          _ProgramInference(
            rootSelector: rootSelector,
            fields: fields,
            score: score,
          ),
        );
      }
    }

    if (candidates.isEmpty) {
      diagnostics.add(
        _error(
          'html_program_not_inferred',
          'No bounded HTML selector set matched every field example.',
        ),
      );
      return null;
    }
    candidates.sort((left, right) {
      final score = right.score.compareTo(left.score);
      return score == 0
          ? left.rootSelector.compareTo(right.rootSelector)
          : score;
    });
    final selected = candidates.first;
    try {
      SafeCssSelectorPolicy.validate(selected.rootSelector);
      return SourceRuleProgram(
        programId: 'search',
        documentKind: SourceDocumentKind.html,
        rootSelector: SourceSelector(
          kind: SourceSelectorKind.css,
          expression: selected.rootSelector,
        ),
        fields: selected.fields,
        resultLimit: request.resultLimit,
      );
    } on ArgumentError {
      diagnostics.add(
        _error(
          'html_program_invalid',
          'The generated HTML program failed schema validation.',
        ),
      );
      return null;
    }
  }

  _FieldInference? _inferHtmlField(
    SourceBuilderFieldHint hint,
    List<List<Element>> rootsByObservation,
    List<_HtmlObservation> observations,
  ) {
    final examples = <String>[];
    for (final observation in observations) {
      for (final record in observation.observation.expectedRecords) {
        final value = record[hint.name];
        if (value != null) {
          examples.add(_normalizeExpectedHtml(hint, value));
        }
      }
    }
    if (examples.isEmpty) {
      return null;
    }

    final selectorCandidates = <String?>{null};
    for (final roots in rootsByObservation) {
      for (final root in roots.take(_maxBuilderElements)) {
        for (final element
            in root.querySelectorAll('*').take(_maxBuilderElements)) {
          selectorCandidates.addAll(_selectorsForElement(element));
          if (selectorCandidates.length >= _maxBuilderCandidates) {
            break;
          }
        }
        if (selectorCandidates.length >= _maxBuilderCandidates) {
          break;
        }
      }
      if (selectorCandidates.length >= _maxBuilderCandidates) {
        break;
      }
    }

    final scored = <_FieldInference>[];
    for (final selector in selectorCandidates) {
      if (selector != null) {
        try {
          SafeCssSelectorPolicy.validate(selector);
        } on Object {
          continue;
        }
      }
      var observedValues = 0;
      var ambiguous = false;
      var exactMatches = 0;
      for (
        var observationIndex = 0;
        observationIndex < observations.length;
        observationIndex++
      ) {
        final roots = rootsByObservation[observationIndex];
        final expectedRecords =
            observations[observationIndex].observation.expectedRecords;
        for (var rootIndex = 0; rootIndex < roots.length; rootIndex++) {
          final root = roots[rootIndex];
          final targets = selector == null
              ? <Element>[root]
              : root.querySelectorAll(selector);
          if (targets.length > 1) {
            ambiguous = true;
            break;
          }
          if (targets.isEmpty) {
            continue;
          }
          final value = _extractHtmlValue(targets.single, hint);
          if (value == null || value.isEmpty) {
            continue;
          }
          observedValues++;
          if (rootIndex < expectedRecords.length) {
            final expected = expectedRecords[rootIndex][hint.name];
            if (expected != null &&
                value == _normalizeExpectedHtml(hint, expected)) {
              exactMatches++;
            }
          }
        }
        if (ambiguous) {
          break;
        }
      }
      if (ambiguous || exactMatches != examples.length) {
        continue;
      }
      final semanticScore =
          selector != null &&
              selector.toLowerCase().contains(hint.name.toLowerCase())
          ? 50
          : 0;
      final specificityScore = selector == null
          ? 0
          : selector.startsWith('.') || selector.startsWith('#')
          ? 10
          : selector.contains('.')
          ? 30
          : 0;
      final selectorLength = selector?.length ?? 0;
      scored.add(
        _FieldInference(
          selector: selector,
          score:
              exactMatches * 1000 +
              observedValues * 10 +
              semanticScore +
              specificityScore -
              selectorLength,
        ),
      );
    }
    if (scored.isEmpty) {
      return null;
    }
    scored.sort((left, right) {
      final score = right.score.compareTo(left.score);
      if (score != 0) {
        return score;
      }
      return (left.selector ?? '').compareTo(right.selector ?? '');
    });
    return scored.first;
  }

  SourceRuleProgram? _buildJsonProgram(
    SourceBuilderRequest request,
    SourceSecurityPolicy policy,
    List<SourceBuilderDiagnostic> diagnostics,
  ) {
    final documents = <Object?>[];
    for (var index = 0; index < request.observations.length; index++) {
      try {
        documents.add(jsonDecode(request.observations[index].fixture.body));
      } on FormatException {
        diagnostics.add(
          _error(
            'json_document_invalid',
            'A JSON observation could not be parsed.',
            observationIndex: index,
          ),
        );
      }
    }
    if (documents.length != request.observations.length) {
      return null;
    }

    final rootSelectors = <String>{};
    for (final document in documents) {
      _collectJsonArrayPaths(document, r'$', rootSelectors);
    }
    final candidates = <_ProgramInference>[];
    for (final rootSelector in rootSelectors.toList()..sort()) {
      final rootsByObservation = <List<Object?>>[];
      var valid = true;
      for (final document in documents) {
        final roots = _evaluateJsonPath(document, rootSelector, policy);
        if (roots == null ||
            roots.isEmpty ||
            roots.length > policy.budget.maxSelectorMatches) {
          valid = false;
          break;
        }
        rootsByObservation.add(roots);
      }
      if (!valid) {
        continue;
      }
      for (var index = 0; index < rootsByObservation.length; index++) {
        if (rootsByObservation[index].length <
            request.observations[index].expectedRecords.length) {
          valid = false;
          break;
        }
      }
      if (!valid) {
        continue;
      }

      final fields = <SourceFieldRule>[];
      var score = _rootJsonScore(rootSelector, rootsByObservation, request);
      for (final hint in request.fields) {
        final inference = _inferJsonField(
          hint,
          rootsByObservation,
          request.observations,
        );
        if (inference == null) {
          valid = false;
          break;
        }
        fields.add(
          SourceFieldRule(
            name: hint.name,
            selector: SourceSelector(
              kind: SourceSelectorKind.jsonPath,
              expression: inference.selector!,
            ),
            valueKind: SourceValueKind.raw,
            required: hint.isRequired,
          ),
        );
        score += inference.score;
      }
      if (valid) {
        candidates.add(
          _ProgramInference(
            rootSelector: rootSelector,
            fields: fields,
            score: score,
          ),
        );
      }
    }

    if (candidates.isEmpty) {
      diagnostics.add(
        _error(
          'json_program_not_inferred',
          'No bounded JSONPath set matched every field example.',
        ),
      );
      return null;
    }
    candidates.sort((left, right) {
      final score = right.score.compareTo(left.score);
      return score == 0
          ? left.rootSelector.compareTo(right.rootSelector)
          : score;
    });
    final selected = candidates.first;
    try {
      return SourceRuleProgram(
        programId: 'search',
        documentKind: SourceDocumentKind.json,
        rootSelector: SourceSelector(
          kind: SourceSelectorKind.jsonPath,
          expression: selected.rootSelector,
        ),
        fields: selected.fields,
        resultLimit: request.resultLimit,
      );
    } on ArgumentError {
      diagnostics.add(
        _error(
          'json_program_invalid',
          'The generated JSON program failed schema validation.',
        ),
      );
      return null;
    }
  }

  _FieldInference? _inferJsonField(
    SourceBuilderFieldHint hint,
    List<List<Object?>> rootsByObservation,
    List<SourceBuilderObservation> observations,
  ) {
    final examples = <String>[];
    for (final observation in observations) {
      for (final record in observation.expectedRecords) {
        final value = record[hint.name];
        if (value != null) {
          examples.add(value.trim());
        }
      }
    }
    if (examples.isEmpty) {
      return null;
    }

    final pathCandidates = <String>{};
    for (final roots in rootsByObservation) {
      for (final root in roots.take(_maxBuilderElements)) {
        _collectJsonFieldPaths(root, r'$', pathCandidates);
        if (pathCandidates.length >= _maxBuilderCandidates) {
          break;
        }
      }
      if (pathCandidates.length >= _maxBuilderCandidates) {
        break;
      }
    }

    final scored = <_FieldInference>[];
    for (final path in pathCandidates.toList()..sort()) {
      var exactMatches = 0;
      var observedValues = 0;
      var ambiguous = false;
      for (
        var observationIndex = 0;
        observationIndex < observations.length;
        observationIndex++
      ) {
        final roots = rootsByObservation[observationIndex];
        final expectedRecords = observations[observationIndex].expectedRecords;
        for (var rootIndex = 0; rootIndex < roots.length; rootIndex++) {
          final selected = _evaluateJsonPath(roots[rootIndex], path, null);
          if (selected == null || selected.length > 1) {
            ambiguous = true;
            break;
          }
          if (selected.isEmpty) {
            continue;
          }
          final value = _jsonValueToString(selected.single);
          if (value == null || value.isEmpty) {
            continue;
          }
          observedValues++;
          if (rootIndex < expectedRecords.length) {
            final expected = expectedRecords[rootIndex][hint.name];
            if (expected != null && value == expected.trim()) {
              exactMatches++;
            }
          }
        }
        if (ambiguous) {
          break;
        }
      }
      if (ambiguous || exactMatches != examples.length) {
        continue;
      }
      final semanticScore = path.toLowerCase().contains(hint.name.toLowerCase())
          ? 50
          : 0;
      scored.add(
        _FieldInference(
          selector: path,
          score:
              exactMatches * 1000 +
              observedValues * 10 +
              semanticScore -
              path.length,
        ),
      );
    }
    if (scored.isEmpty) {
      return null;
    }
    scored.sort((left, right) {
      final score = right.score.compareTo(left.score);
      return score == 0 ? left.selector!.compareTo(right.selector!) : score;
    });
    return scored.first;
  }

  void _verifyGeneratedPackage(
    SourceBuilderRequest request,
    SourcePackageManifest package,
    List<SourceBuilderDiagnostic> diagnostics,
  ) {
    final program = package.programs.single;
    for (
      var observationIndex = 0;
      observationIndex < request.observations.length;
      observationIndex++
    ) {
      final observation = request.observations[observationIndex];
      final SourceEvaluationResult result;
      try {
        result = _evaluator.evaluate(
          package: package,
          program: program,
          fixture: observation.fixture,
        );
      } on SourceRuleSecurityException {
        diagnostics.add(
          _error(
            'generated_rule_security_failed',
            'The generated rule failed its security verification.',
            observationIndex: observationIndex,
          ),
        );
        continue;
      } on SourceRuleEvaluationException {
        diagnostics.add(
          _error(
            'generated_rule_evaluation_failed',
            'The generated rule failed its deterministic verification.',
            observationIndex: observationIndex,
          ),
        );
        continue;
      } on Object {
        diagnostics.add(
          _error(
            'generated_rule_verification_failed',
            'The generated rule could not be verified.',
            observationIndex: observationIndex,
          ),
        );
        continue;
      }

      if (result.records.length < observation.expectedRecords.length) {
        diagnostics.add(
          _error(
            'generated_record_count_mismatch',
            'The generated rule returned fewer records than the observation.',
            observationIndex: observationIndex,
          ),
        );
        continue;
      }
      for (
        var recordIndex = 0;
        recordIndex < observation.expectedRecords.length;
        recordIndex++
      ) {
        final expected = observation.expectedRecords[recordIndex];
        final actual = result.records[recordIndex].values;
        for (final field in request.fields) {
          final expectedValue = expected[field.name];
          if (expectedValue == null) {
            continue;
          }
          final actualValue = actual[field.name];
          final normalizedExpected =
              request.documentKind == SourceDocumentKind.html &&
                  field.valueKind == SourceValueKind.text
              ? _normalizeWhitespace(expectedValue)
              : expectedValue;
          if (actualValue != normalizedExpected) {
            diagnostics.add(
              _error(
                'generated_value_mismatch',
                'The generated rule did not reproduce a field example.',
                observationIndex: observationIndex,
                fieldName: field.name,
              ),
            );
          }
        }
      }
      if (result.diagnostics.isNotEmpty) {
        diagnostics.add(
          _error(
            'generated_diagnostics_present',
            'The generated rule produced diagnostics during verification.',
            observationIndex: observationIndex,
          ),
        );
      }
    }
  }

  static int _rootScore(
    String selector,
    List<List<Element>> rootsByObservation,
    List<_HtmlObservation> observations,
  ) {
    var score = selector.contains('.') || selector.startsWith('#') ? 50 : 0;
    for (var index = 0; index < rootsByObservation.length; index++) {
      final difference =
          rootsByObservation[index].length -
          observations[index].observation.expectedRecords.length;
      score += 1000 - math.min(900, difference.abs() * 20);
    }
    return score - selector.length;
  }

  static int _rootJsonScore(
    String selector,
    List<List<Object?>> rootsByObservation,
    SourceBuilderRequest request,
  ) {
    var score = selector.contains('[*]') ? 50 : 0;
    for (var index = 0; index < rootsByObservation.length; index++) {
      final difference =
          rootsByObservation[index].length -
          request.observations[index].expectedRecords.length;
      score += 1000 - math.min(900, difference.abs() * 20);
    }
    return score - selector.length;
  }

  static Set<String> _selectorsForElement(Element element) {
    final selectors = <String>{};
    final tag = element.localName;
    if (tag == null || !_isSafeTag(tag)) {
      return selectors;
    }
    selectors.add(tag);
    final id = element.attributes['id'];
    if (id != null && _isSafeCssIdentifier(id)) {
      selectors.add('#$id');
    }
    for (final className in element.classes) {
      if (!_isSafeCssIdentifier(className)) {
        continue;
      }
      selectors.add('.$className');
      selectors.add('$tag.$className');
    }
    return selectors;
  }

  static String? _extractHtmlValue(
    Element element,
    SourceBuilderFieldHint hint,
  ) {
    final value = switch (hint.valueKind) {
      SourceValueKind.text => _normalizeWhitespace(element.text),
      SourceValueKind.attribute =>
        element.attributes[hint.attributeName!]?.trim(),
      SourceValueKind.raw => element.outerHtml.trim(),
      SourceValueKind.literal => null,
    };
    if (value == null || value.isEmpty) {
      return null;
    }
    return value;
  }

  static String _normalizeExpectedHtml(
    SourceBuilderFieldHint hint,
    String value,
  ) => hint.valueKind == SourceValueKind.text
      ? _normalizeWhitespace(value)
      : value.trim();

  static String _normalizeWhitespace(String value) {
    return value.replaceAll(RegExp(r'\s+'), ' ').trim();
  }

  static bool _isSafeObservedUri(Uri uri) {
    if (!uri.hasScheme ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.fragment.isNotEmpty) {
      return false;
    }
    final scheme = uri.scheme.toLowerCase();
    if (scheme != 'https' && scheme != 'http') {
      return false;
    }
    if (uri.hasPort &&
        ((scheme == 'https' && uri.port != 443) ||
            (scheme == 'http' && uri.port != 80))) {
      return false;
    }
    try {
      SourceDomainRule(host: uri.host);
      return true;
    } on ArgumentError {
      return false;
    }
  }

  static String _domainKey(SourceDomainRule rule) {
    final schemes = rule.schemes.toList()..sort();
    return '${rule.host}|${rule.includeSubdomains}|${schemes.join(',')}';
  }

  static bool _isSafeTag(String value) {
    return RegExp(r'^[A-Za-z][A-Za-z0-9_-]*$').hasMatch(value);
  }

  static bool _isSafeCssIdentifier(String value) {
    return RegExp(r'^[A-Za-z_][A-Za-z0-9_-]*$').hasMatch(value);
  }

  static void _collectJsonArrayPaths(
    Object? value,
    String path,
    Set<String> output,
  ) {
    if (path.length > 256 || path.split('.').length > _maxJsonDepth) {
      return;
    }
    if (value is List) {
      if (value.isNotEmpty && value.every((item) => item is Map)) {
        output.add(path == r'$' ? r'$[*]' : '$path[*]');
      }
      final itemPath = path == r'$' ? r'$[*]' : '$path[*]';
      for (final item in value.take(8)) {
        if (item is Map || item is List) {
          _collectJsonArrayPaths(item, itemPath, output);
        }
      }
      return;
    }
    if (value is! Map) {
      return;
    }
    final keys = value.keys.whereType<String>().toList()..sort();
    for (final key in keys) {
      if (!_isSafeJsonKey(key)) {
        continue;
      }
      _collectJsonArrayPaths(value[key], '$path.$key', output);
    }
  }

  static void _collectJsonFieldPaths(
    Object? value,
    String path,
    Set<String> output,
  ) {
    if (path.length > 256 || path.split('.').length > _maxJsonDepth) {
      return;
    }
    if (value is! Map) {
      return;
    }
    final keys = value.keys.whereType<String>().toList()..sort();
    for (final key in keys) {
      if (!_isSafeJsonKey(key)) {
        continue;
      }
      final childPath = '$path.$key';
      output.add(childPath);
      _collectJsonFieldPaths(value[key], childPath, output);
      if (output.length >= _maxBuilderCandidates) {
        return;
      }
    }
  }

  static bool _isSafeJsonKey(String key) {
    return RegExp(r'^[A-Za-z_][A-Za-z0-9_-]*$').hasMatch(key);
  }

  static List<Object?>? _evaluateJsonPath(
    Object? root,
    String expression,
    SourceSecurityPolicy? policy,
  ) {
    try {
      final budget = policy?.budget ?? _defaultBudget;
      final meter = EvaluationBudgetMeter(budget);
      return JsonPathSubset.evaluate(root, expression, meter);
    } on SourceRuleEvaluationException {
      return null;
    } on SourceRuleSecurityException {
      return null;
    }
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

  static Map<String, Object?> _packageFingerprintPayload(
    SourcePackageManifest package,
  ) {
    return {
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
            .toList(),
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
      'programs': package.programs
          .map(
            (program) => {
              'id': program.programId,
              'documentKind': program.documentKind.name,
              'root': {
                'type': program.rootSelector.kind.name,
                'expression': program.rootSelector.expression,
              },
              'resultLimit': program.resultLimit,
              'fields': program.fields
                  .map(
                    (field) => {
                      'name': field.name,
                      'selector': field.selector?.expression,
                      'value': field.valueKind.name,
                      'attribute': field.attributeName,
                      'required': field.required,
                    },
                  )
                  .toList(),
            },
          )
          .toList(),
    };
  }

  static SourceBuilderDiagnostic _error(
    String code,
    String message, {
    int? observationIndex,
    String? fieldName,
  }) {
    return SourceBuilderDiagnostic(
      code: code,
      message: message,
      severity: SourceBuilderDiagnosticSeverity.error,
      observationIndex: observationIndex,
      fieldName: fieldName,
    );
  }
}

final class _PolicyBuildResult {
  const _PolicyBuildResult({
    required this.policy,
    required this.requiresReconsent,
  });

  final SourceSecurityPolicy policy;
  final bool requiresReconsent;
}

final class _HtmlObservation {
  const _HtmlObservation({required this.observation, required this.document});

  final SourceBuilderObservation observation;
  final Document document;
}

final class _ProgramInference {
  const _ProgramInference({
    required this.rootSelector,
    required this.fields,
    required this.score,
  });

  final String rootSelector;
  final List<SourceFieldRule> fields;
  final int score;
}

final class _FieldInference {
  const _FieldInference({required this.selector, required this.score});

  final String? selector;
  final int score;
}
