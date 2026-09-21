import '../models/source_package_manifest.dart';
import '../models/source_runtime_models.dart';
import '../models/source_subject_details_models.dart';
import '../models/source_identity.dart';
import '../models/source_package_live_operations.dart';

abstract interface class SourceSubjectDetailsNormalizer {
  SourceSubjectDetailsResult normalizeSubjectDetails({
    required SourcePackageManifest package,
    required SourceRuntimeResult metadataRuntimeResult,
    required SourceRuntimeResult episodeRuntimeResult,
    required SourceSubjectIdentity subject,
    required SourceSubjectDetailsFieldMapping mapping,
  });
}
