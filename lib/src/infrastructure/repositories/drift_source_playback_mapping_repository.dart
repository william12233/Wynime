import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:pub_semver/pub_semver.dart';

import '../../domain/models/episode_mapping.dart';
import '../../domain/models/source_identity.dart';
import '../../domain/models/source_models.dart';
import '../../domain/models/source_package_provenance.dart';
import '../../domain/models/source_playback_mapping.dart';
import '../../domain/repositories/source_playback_mapping_repository.dart';
import '../database/wynime_database.dart';

/// Durable identity-only source mappings.
///
/// Mapping reads fail closed on package revision changes and remove stale
/// rows. Subject remaps clear dependent episode mappings in the same write
/// transaction, so a new subject identity cannot inherit old episode links.
final class DriftSourcePlaybackMappingRepository
    implements SourcePlaybackMappingRepository {
  DriftSourcePlaybackMappingRepository(
    this._database, {
    DateTime Function()? clock,
  }) : _clock = clock ?? DateTime.now;

  final WynimeDatabase _database;
  final DateTime Function() _clock;

  @override
  Future<SourceSubjectMapping?> findValidSubjectMapping({
    required String bangumiSubjectId,
    required String packageId,
    required SourcePackageProvenance expectedProvenance,
  }) async {
    final rows =
        await (_database.select(_database.sourceSubjectMappings)..where(
              (table) =>
                  table.bangumiSubjectId.equals(bangumiSubjectId) &
                  table.packageId.equals(packageId),
            ))
            .get();
    if (rows.isEmpty) return null;
    final mappings = rows.map(_mapSubject).toList(growable: false);
    final valid = mappings
        .where(
          (mapping) =>
              mapping.provenance == expectedProvenance &&
              mapping.packageId == packageId &&
              mapping.provenance.packageId == packageId &&
              mapping.sourceSubject.sourceId == packageId,
        )
        .toList(growable: false);
    if (valid.length != 1 || valid.length != mappings.length) {
      await clearSubjectMapping(
        bangumiSubjectId: bangumiSubjectId,
        packageId: packageId,
      );
      return null;
    }
    return valid.single;
  }

  @override
  Future<void> upsertSubjectMapping(SourceSubjectMapping mapping) async {
    _validateSubject(mapping);
    final now = _clock().toUtc();
    await _database.runWrite(
      () => _database.transaction(() async {
        final existing =
            await (_database.select(_database.sourceSubjectMappings)..where(
                  (table) =>
                      table.bangumiSubjectId.equals(mapping.bangumiSubjectId) &
                      table.packageId.equals(mapping.packageId),
                ))
                .get();
        final remapped = existing.any(
          (row) =>
              row.sourceId != mapping.sourceSubject.sourceId ||
              row.sourceSubjectId != mapping.sourceSubject.subjectId ||
              row.packageVersion != mapping.provenance.version.toString() ||
              row.packageRevisionSha256 != mapping.provenance.revisionSha256,
        );
        if (remapped) {
          await (_database.delete(_database.sourceEpisodeMappings)..where(
                (table) =>
                    table.bangumiSubjectId.equals(mapping.bangumiSubjectId) &
                    table.packageId.equals(mapping.packageId),
              ))
              .go();
          await (_database.delete(_database.sourceSubjectMappings)..where(
                (table) =>
                    table.bangumiSubjectId.equals(mapping.bangumiSubjectId) &
                    table.packageId.equals(mapping.packageId),
              ))
              .go();
        }
        await _database
            .into(_database.sourceSubjectMappings)
            .insertOnConflictUpdate(_subjectCompanion(mapping, now));
      }),
    );
  }

  @override
  Future<void> clearSubjectMapping({
    required String bangumiSubjectId,
    required String packageId,
  }) async {
    await _database.runWrite(
      () => _database.transaction(() async {
        await (_database.delete(_database.sourceEpisodeMappings)..where(
              (table) =>
                  table.bangumiSubjectId.equals(bangumiSubjectId) &
                  table.packageId.equals(packageId),
            ))
            .go();
        await (_database.delete(_database.sourceSubjectMappings)..where(
              (table) =>
                  table.bangumiSubjectId.equals(bangumiSubjectId) &
                  table.packageId.equals(packageId),
            ))
            .go();
      }),
    );
  }

  @override
  Future<SourceEpisodeMapping?> findValidEpisodeMapping({
    required String bangumiSubjectId,
    required String bangumiEpisodeId,
    required String packageId,
    required SourceSubjectIdentity expectedSourceSubject,
    required SourcePackageProvenance expectedProvenance,
  }) async {
    final row =
        await (_database.select(_database.sourceEpisodeMappings)..where(
              (table) =>
                  table.bangumiEpisodeId.equals(bangumiEpisodeId) &
                  table.packageId.equals(packageId) &
                  table.sourceId.equals(expectedSourceSubject.sourceId),
            ))
            .getSingleOrNull();
    if (row == null) return null;
    final mapping = _mapEpisode(row);
    if (mapping.bangumiSubjectId != bangumiSubjectId ||
        mapping.sourceEpisode.sourceId != packageId ||
        mapping.sourceEpisode.sourceId != expectedSourceSubject.sourceId ||
        mapping.sourceEpisode.subjectId != expectedSourceSubject.subjectId ||
        mapping.provenance != expectedProvenance ||
        mapping.provenance.packageId != packageId) {
      await clearEpisodeMapping(
        bangumiEpisodeId: bangumiEpisodeId,
        packageId: packageId,
      );
      return null;
    }
    return mapping;
  }

  @override
  Future<void> upsertEpisodeMapping(SourceEpisodeMapping mapping) async {
    _validateEpisode(mapping);
    final now = _clock().toUtc();
    await _database.runWrite(
      () => _database
          .into(_database.sourceEpisodeMappings)
          .insertOnConflictUpdate(_episodeCompanion(mapping, now)),
    );
  }

  @override
  Future<void> clearEpisodeMapping({
    required String bangumiEpisodeId,
    required String packageId,
  }) async {
    await _database.runWrite(
      () =>
          (_database.delete(_database.sourceEpisodeMappings)..where(
                (table) =>
                    table.bangumiEpisodeId.equals(bangumiEpisodeId) &
                    table.packageId.equals(packageId),
              ))
              .go(),
    );
  }

  @override
  Future<void> clearEpisodesUnderSubject({
    required String bangumiSubjectId,
    required String packageId,
  }) async {
    await _database.runWrite(
      () =>
          (_database.delete(_database.sourceEpisodeMappings)..where(
                (table) =>
                    table.bangumiSubjectId.equals(bangumiSubjectId) &
                    table.packageId.equals(packageId),
              ))
              .go(),
    );
  }

  @override
  Future<void> clearAllForPackage(String packageId) async {
    await _database.runWrite(
      () => _database.transaction(() async {
        await (_database.delete(
          _database.sourceEpisodeMappings,
        )..where((table) => table.packageId.equals(packageId))).go();
        await (_database.delete(
          _database.sourceSubjectMappings,
        )..where((table) => table.packageId.equals(packageId))).go();
      }),
    );
  }

  SourceSubjectMappingsCompanion _subjectCompanion(
    SourceSubjectMapping mapping,
    DateTime now,
  ) {
    return SourceSubjectMappingsCompanion.insert(
      bangumiSubjectId: mapping.bangumiSubjectId,
      packageId: mapping.packageId,
      sourceId: mapping.sourceSubject.sourceId,
      sourceSubjectId: mapping.sourceSubject.subjectId,
      packageVersion: mapping.provenance.version.toString(),
      packageRevisionSha256: mapping.provenance.revisionSha256,
      mappingKind: mapping.mappingKind.name,
      confirmedAt: mapping.confirmedAt.toUtc(),
      updatedAt: now,
    );
  }

  SourceEpisodeMappingsCompanion _episodeCompanion(
    SourceEpisodeMapping mapping,
    DateTime now,
  ) {
    return SourceEpisodeMappingsCompanion.insert(
      bangumiSubjectId: mapping.bangumiSubjectId,
      bangumiEpisodeId: mapping.bangumiEpisodeId,
      packageId: mapping.packageId,
      sourceId: mapping.sourceEpisode.sourceId,
      sourceSubjectId: mapping.sourceEpisode.subjectId,
      sourceLineId: mapping.sourceEpisode.lineId,
      sourceEpisodeId: mapping.sourceEpisode.episodeId,
      packageVersion: mapping.provenance.version.toString(),
      packageRevisionSha256: mapping.provenance.revisionSha256,
      mappingKind: mapping.mappingKind.name,
      mappingMetadataJson: Value(
        mapping.mapping == null
            ? null
            : jsonEncode(_encodeMapping(mapping.mapping!)),
      ),
      confirmedAt: mapping.confirmedAt.toUtc(),
      updatedAt: now,
    );
  }

  SourceSubjectMapping _mapSubject(SourceSubjectMappingRecord row) {
    try {
      return SourceSubjectMapping(
        bangumiSubjectId: row.bangumiSubjectId,
        packageId: row.packageId,
        sourceSubject: SourceSubjectIdentity(
          sourceId: row.sourceId,
          subjectId: row.sourceSubjectId,
        ),
        provenance: SourcePackageProvenance(
          packageId: row.packageId,
          version: Version.parse(row.packageVersion),
          revisionSha256: row.packageRevisionSha256,
        ),
        mappingKind: SubjectMappingKind.values.byName(row.mappingKind),
        confirmedAt: row.confirmedAt.toUtc(),
      );
    } on Object {
      throw StateError('Persisted source subject mapping is invalid.');
    }
  }

  SourceEpisodeMapping _mapEpisode(SourceEpisodeMappingRecord row) {
    try {
      final sourceEpisode = SourceEpisode(
        identity: SourceEpisodeIdentity(
          sourceId: row.sourceId,
          lineId: row.sourceLineId,
          subjectId: row.sourceSubjectId,
          episodeId: row.sourceEpisodeId,
        ),
        title: _mappingRawLabel(row.mappingMetadataJson) ?? row.sourceEpisodeId,
        episodeNumber: _mappingNumber(row.mappingMetadataJson, 'sourceNumber'),
        episodeKind: _mappingKind(row.mappingMetadataJson),
      );
      return SourceEpisodeMapping(
        bangumiSubjectId: row.bangumiSubjectId,
        bangumiEpisodeId: row.bangumiEpisodeId,
        packageId: row.packageId,
        sourceEpisode: sourceEpisode.identity,
        provenance: SourcePackageProvenance(
          packageId: row.packageId,
          version: Version.parse(row.packageVersion),
          revisionSha256: row.packageRevisionSha256,
        ),
        mappingKind: EpisodeMappingKind.values.byName(row.mappingKind),
        confirmedAt: row.confirmedAt.toUtc(),
        mapping: _decodeMapping(row.mappingMetadataJson, sourceEpisode),
      );
    } on Object {
      throw StateError('Persisted source episode mapping is invalid.');
    }
  }

  void _validateSubject(SourceSubjectMapping mapping) {
    if (mapping.packageId != mapping.provenance.packageId ||
        mapping.sourceSubject.sourceId != mapping.packageId ||
        mapping.bangumiSubjectId.trim().isEmpty) {
      throw ArgumentError('Source subject mapping identity is inconsistent.');
    }
  }

  void _validateEpisode(SourceEpisodeMapping mapping) {
    if (mapping.packageId != mapping.provenance.packageId ||
        mapping.sourceEpisode.sourceId != mapping.packageId ||
        mapping.bangumiSubjectId.trim().isEmpty ||
        mapping.bangumiEpisodeId.trim().isEmpty) {
      throw ArgumentError('Source episode mapping identity is inconsistent.');
    }
  }

  Map<String, Object?> _encodeMapping(EpisodeMapping mapping) => {
    'sourceRawLabel': mapping.sourceEpisode.rawLabel,
    'sourceNumber': mapping.sourceNumber,
    'sourceKind': mapping.sourceEpisode.episodeKind.name,
    'bangumiSort': mapping.bangumiSort,
    'numberingMode': mapping.numberingMode.name,
    'seasonRelativeNumber': mapping.seasonRelativeNumber,
    'absoluteNumber': mapping.absoluteNumber,
    'offset': mapping.offset,
    'evidence': [
      for (final item in mapping.evidence)
        {
          'code': item.code,
          'offset': item.offset,
          'alignedEpisodeCount': item.alignedEpisodeCount,
        },
    ],
  };

  EpisodeMapping? _decodeMapping(String? encoded, SourceEpisode sourceEpisode) {
    if (encoded == null) return null;
    final decoded = jsonDecode(encoded);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException(
        'Source episode mapping metadata is invalid.',
      );
    }
    final modeName = decoded['numberingMode'];
    if (modeName is! String) {
      throw const FormatException('Source episode mapping mode is invalid.');
    }
    final mode = EpisodeNumberingMode.values.byName(modeName);
    final sourceNumber = _requiredDouble(decoded, 'sourceNumber');
    final bangumiSort = _requiredDouble(decoded, 'bangumiSort');
    final evidenceValue = decoded['evidence'];
    if (evidenceValue is! List) {
      throw const FormatException(
        'Source episode mapping evidence is invalid.',
      );
    }
    final evidence = <EpisodeMappingEvidence>[];
    for (final item in evidenceValue) {
      if (item is! Map<String, dynamic> || item['code'] is! String) {
        throw const FormatException(
          'Source episode mapping evidence item is invalid.',
        );
      }
      evidence.add(
        EpisodeMappingEvidence(
          code: item['code'] as String,
          offset: _optionalDouble(item['offset']),
          alignedEpisodeCount: item['alignedEpisodeCount'] is int
              ? item['alignedEpisodeCount'] as int
              : null,
        ),
      );
    }
    return EpisodeMapping(
      sourceEpisode: sourceEpisode,
      bangumiSort: bangumiSort,
      sourceNumber: sourceNumber,
      numberingMode: mode,
      seasonRelativeNumber: _optionalDouble(decoded['seasonRelativeNumber']),
      absoluteNumber: _optionalDouble(decoded['absoluteNumber']),
      offset: _optionalDouble(decoded['offset']),
      evidence: evidence,
    );
  }

  String? _mappingRawLabel(String? encoded) {
    if (encoded == null) return null;
    final decoded = jsonDecode(encoded);
    if (decoded is! Map<String, dynamic> ||
        decoded['sourceRawLabel'] is! String) {
      throw const FormatException('Source episode mapping label is invalid.');
    }
    return decoded['sourceRawLabel'] as String;
  }

  double? _mappingNumber(String? encoded, String key) {
    if (encoded == null) return null;
    final decoded = jsonDecode(encoded);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException(
        'Source episode mapping metadata is invalid.',
      );
    }
    return _optionalDouble(decoded[key]);
  }

  SourceEpisodeKind? _mappingKind(String? encoded) {
    if (encoded == null) return null;
    final decoded = jsonDecode(encoded);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException(
        'Source episode mapping metadata is invalid.',
      );
    }
    final value = decoded['sourceKind'];
    if (value == null) return null;
    if (value is! String) {
      throw const FormatException('Source episode mapping kind is invalid.');
    }
    return SourceEpisodeKind.values.byName(value);
  }

  double _requiredDouble(Map<String, dynamic> value, String key) {
    final parsed = _optionalDouble(value[key]);
    if (parsed == null) {
      throw FormatException('Source episode mapping $key is invalid.');
    }
    return parsed;
  }

  double? _optionalDouble(Object? value) {
    if (value is num && value.isFinite) return value.toDouble();
    return null;
  }
}
