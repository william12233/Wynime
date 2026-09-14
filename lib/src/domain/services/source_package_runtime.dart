import '../models/source_package_manager_models.dart';
import '../models/source_rule_program.dart';
import '../models/source_runtime_models.dart';

abstract interface class SourcePackageRuntime {
  SourceRuntimeResult executeFixture({
    required InstalledSourcePackage installedPackage,
    required String programId,
    required SourceFixture fixture,
  });
}
