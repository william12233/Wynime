import '../models/source_package_manifest.dart';
import '../models/source_identity.dart';
import '../models/source_playable_normalization_models.dart';
import '../models/source_runtime_models.dart';

abstract interface class SourcePlayableSourceNormalizer {
  SourcePlayableSourceNormalizationResult normalizePlayableSources({
    required SourcePackageManifest package,
    required SourceRuntimeResult runtimeResult,
    required SourceEpisodeIdentity episode,
    required SourcePlayableSourceFieldMapping mapping,
  });
}
