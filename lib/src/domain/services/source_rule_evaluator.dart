import '../models/source_package_manifest.dart';
import '../models/source_rule_program.dart';

abstract interface class SourceRuleEvaluator {
  SourceEvaluationResult evaluate({
    required SourcePackageManifest package,
    required SourceRuleProgram program,
    required SourceFixture fixture,
  });
}
