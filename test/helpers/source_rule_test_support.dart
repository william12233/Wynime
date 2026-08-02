import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';

SourceResourceBudget testSourceBudget({
  int maxDocumentBytes = 64 * 1024,
  int maxRecords = 20,
  int maxSelectorMatches = 100,
  int maxEvaluationSteps = 1000,
  int maxRegexPatternChars = 128,
  int maxRegexInputChars = 1024,
  int maxRedirects = 3,
}) {
  return SourceResourceBudget(
    maxDocumentBytes: maxDocumentBytes,
    maxRecords: maxRecords,
    maxSelectorMatches: maxSelectorMatches,
    maxEvaluationSteps: maxEvaluationSteps,
    maxRegexPatternChars: maxRegexPatternChars,
    maxRegexInputChars: maxRegexInputChars,
    maxRedirects: maxRedirects,
  );
}

SourceSecurityPolicy testSourcePolicy({
  Iterable<SourceDomainRule>? domains,
  Set<SourcePermission>? permissions,
  SourceResourceBudget? budget,
}) {
  return SourceSecurityPolicy(
    allowedDomains:
        domains ??
        [SourceDomainRule(host: 'example.com', includeSubdomains: true)],
    permissions: permissions ?? {SourcePermission.network},
    budget: budget ?? testSourceBudget(),
  );
}

SourcePackageManifest testSourcePackage({
  required SourceRuleProgram program,
  SourceSecurityPolicy? policy,
  SourcePackageSignatureMetadata? signature,
}) {
  return SourcePackageManifest(
    schemaVersion: 1,
    packageId: 'example.anime',
    displayName: 'Example Anime',
    version: Version.parse('1.0.0'),
    wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
    securityPolicy: policy ?? testSourcePolicy(),
    programs: [program],
    signatureMetadata: signature,
  );
}
