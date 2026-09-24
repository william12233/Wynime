import 'source_identity.dart';
import 'source_package_provenance.dart';
import 'episode_mapping.dart';

enum SubjectMappingKind { automaticExactTitle, userConfirmed }

enum EpisodeMappingKind { automaticExactNumber, userConfirmed }

final class SourceSubjectMapping {
  const SourceSubjectMapping({
    required this.bangumiSubjectId,
    required this.packageId,
    required this.sourceSubject,
    required this.provenance,
    required this.mappingKind,
    required this.confirmedAt,
  });

  final String bangumiSubjectId;
  final String packageId;
  final SourceSubjectIdentity sourceSubject;
  final SourcePackageProvenance provenance;
  final SubjectMappingKind mappingKind;
  final DateTime confirmedAt;
}

final class SourceEpisodeMapping {
  const SourceEpisodeMapping({
    required this.bangumiSubjectId,
    required this.bangumiEpisodeId,
    required this.packageId,
    required this.sourceEpisode,
    required this.provenance,
    required this.mappingKind,
    required this.confirmedAt,
    this.mapping,
  });

  final String bangumiSubjectId;
  final String bangumiEpisodeId;
  final String packageId;
  final SourceEpisodeIdentity sourceEpisode;
  final SourcePackageProvenance provenance;
  final EpisodeMappingKind mappingKind;
  final DateTime confirmedAt;
  final EpisodeMapping? mapping;
}
