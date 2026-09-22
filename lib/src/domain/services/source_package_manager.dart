import 'package:pub_semver/pub_semver.dart';

import '../models/source_package_manager_models.dart';
import '../models/source_package_manifest.dart';

abstract interface class SourcePackageManager {
  List<InstalledSourcePackage> get installedPackages;

  List<InstalledSourcePackage> get enabledPackages;

  SourcePackageValidationResult validate(
    SourcePackageManifest package, {
    SourcePackageManifest? previous,
  });

  InstalledSourcePackage install(SourcePackageManifest package);

  InstalledSourcePackage update(SourcePackageManifest package);

  InstalledSourcePackage enable({
    required String packageId,
    required Version version,
    required bool userApproved,
    required bool reconsentGranted,
  });

  InstalledSourcePackage disable({
    required String packageId,
    required Version version,
  });

  InstalledSourcePackage remove({required String packageId});
}
