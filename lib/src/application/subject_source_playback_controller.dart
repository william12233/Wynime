import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:pub_semver/pub_semver.dart';

import '../domain/models/bangumi_models.dart';
import '../domain/models/source_identity.dart';
import '../domain/models/source_models.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_package_provenance.dart';
import '../domain/models/source_playback_mapping.dart';
import '../domain/repositories/source_playback_mapping_repository.dart';
import '../infrastructure/source_rules/source_package_revision_calculator.dart';
import '../platform/playback/playback_surface_host.dart';
import 'playback/playback_coordinator.dart';
import 'source_episode_correlator.dart';
import 'source_installed_live_episode_pipeline.dart';
import 'source_installed_live_playback_pipeline.dart';
import 'source_installed_live_search_pipeline.dart';
import 'source_installed_live_subject_pipeline.dart';
import 'source_live_capture_playback_entry_point.dart';
import 'source_package_startup_controller.dart';
import 'source_subject_matcher.dart';
import '../domain/services/web_source_browser.dart';

enum SubjectSourcePlaybackPhase {
  idle,
  loading,
  ready,
  selectionRequired,
  episodeSelectionRequired,
  notFound,
  sourceUnavailable,
  sourceActionRequired,
  failed,
}

final class SubjectSourcePlaybackState {
  const SubjectSourcePlaybackState({
    required this.phase,
    this.package,
    this.provenance,
    this.sourceSubject,
    this.subjectDetails,
    this.subjectCandidates = const [],
    this.sourceCandidates = const [],
    this.episodeCandidates = const [],
    this.selectedEpisode,
    this.errorCode,
  });

  final SubjectSourcePlaybackPhase phase;
  final InstalledSourcePackage? package;
  final SourcePackageProvenance? provenance;
  final SourceSubjectIdentity? sourceSubject;
  final SourceSubjectDetails? subjectDetails;
  final List<SourceSearchResult> subjectCandidates;
  final List<SourceSearchResult> sourceCandidates;
  final List<SourceEpisode> episodeCandidates;
  final SourceEpisodeIdentity? selectedEpisode;
  final String? errorCode;
}

enum SourceEpisodeResolutionStatus { ready, selectionRequired, notFound }

final class SourceEpisodeResolution {
  const SourceEpisodeResolution({
    required this.status,
    this.identity,
    this.candidates = const [],
    this.reason,
  });

  final SourceEpisodeResolutionStatus status;
  final SourceEpisodeIdentity? identity;
  final List<SourceEpisode> candidates;
  final String? reason;
}

/// Owns subject-to-source and episode-to-source identity state for one
/// Bangumi detail page. It never resolves a media URL and delegates all live
/// network/playback work to the existing typed pipelines.
final class SubjectSourcePlaybackController extends ChangeNotifier {
  SubjectSourcePlaybackController({
    required this.subject,
    required this.sourcePackages,
    required this.searchPipeline,
    required this.subjectPipeline,
    required this.episodePipeline,
    required this.playbackPipeline,
    required this.mappingRepository,
    this.revisionCalculator = const SourcePackageRevisionCalculator(),
    this.episodeCorrelator = const SourceEpisodeCorrelator(),
    this.playbackCoordinator,
    this.surfaceHost,
    this.closePlayback,
    this.capturePlaybackEntryPoint,
    this.captureBrowserPort,
    this.wynimeVersion,
  }) : _state = const SubjectSourcePlaybackState(
         phase: SubjectSourcePlaybackPhase.idle,
       ) {
    sourcePackages.addListener(_onSourcePackagesChanged);
    _packageSnapshot = _snapshotPackages(sourcePackages.enabledPackages);
  }

  final BangumiSubject subject;
  final SourcePackageStartupController sourcePackages;
  final SourceInstalledLiveSearchPipeline searchPipeline;
  final SourceInstalledLiveSubjectPipeline subjectPipeline;
  final SourceInstalledLiveEpisodePipeline episodePipeline;
  final SourceInstalledLivePlaybackPipeline playbackPipeline;
  final SourcePlaybackMappingRepository mappingRepository;
  final SourcePackageRevisionCalculator revisionCalculator;
  final SourceEpisodeCorrelator episodeCorrelator;
  final PlaybackCoordinator? playbackCoordinator;
  final PlaybackSurfaceHost? surfaceHost;
  final Future<void> Function()? closePlayback;
  final SourceLiveCapturePlaybackEntryPoint? capturePlaybackEntryPoint;
  final WebSourceBrowserPort? captureBrowserPort;
  final Version? wynimeVersion;

  SubjectSourcePlaybackState _state;
  String _packageSnapshot = '';
  int _generation = 0;
  bool _closed = false;

  SubjectSourcePlaybackState get state => _state;

  Future<void> initialize() async {
    final generation = ++_generation;
    _setState(
      const SubjectSourcePlaybackState(
        phase: SubjectSourcePlaybackPhase.loading,
      ),
    );
    final packages = sourcePackages.enabledPackages;
    if (packages.isEmpty) {
      _setState(
        SubjectSourcePlaybackState(
          phase: SubjectSourcePlaybackPhase.sourceUnavailable,
          errorCode: sourcePackages.installedPackages.isEmpty
              ? 'no_installed_sources'
              : 'no_enabled_sources',
        ),
      );
      return;
    }
    try {
      final persistedCandidates = await _persistedSubjectCandidates(packages);
      if (!_isCurrent(generation)) return;
      if (persistedCandidates.length == 1) {
        await selectSubject(
          persistedCandidates.single,
          expectedGeneration: _generation,
          availableCandidates: persistedCandidates,
        );
        return;
      }
      if (persistedCandidates.length > 1) {
        _setState(
          SubjectSourcePlaybackState(
            phase: SubjectSourcePlaybackPhase.selectionRequired,
            subjectCandidates: persistedCandidates,
            sourceCandidates: persistedCandidates,
          ),
        );
        return;
      }

      final result = await searchPipeline.search(
        installedPackages: packages,
        query: subject.nameCn.isNotEmpty ? subject.nameCn : subject.name,
      );
      if (!_isCurrent(generation)) return;
      if (result.coordinatorResult == null ||
          result.coordinatorResult!.results.isEmpty) {
        _setState(
          SubjectSourcePlaybackState(
            phase:
                result.status ==
                    SourceInstalledLiveSearchPipelineStatus.noUsableSources
                ? SubjectSourcePlaybackPhase.sourceActionRequired
                : SubjectSourcePlaybackPhase.notFound,
            errorCode: result.reasonCode,
          ),
        );
        return;
      }
      final match = const SourceSubjectMatcher().match(
        subject: subject,
        results: result.coordinatorResult!.results,
      );
      if (match.status == SourceSubjectMatchStatus.notFound) {
        _setState(
          const SubjectSourcePlaybackState(
            phase: SubjectSourcePlaybackPhase.notFound,
          ),
        );
        return;
      }
      if (match.status == SourceSubjectMatchStatus.selectionRequired) {
        _setState(
          SubjectSourcePlaybackState(
            phase: SubjectSourcePlaybackPhase.selectionRequired,
            subjectCandidates: match.candidates,
            sourceCandidates: match.candidates,
          ),
        );
        return;
      }
      await selectSubject(
        match.candidates.single,
        expectedGeneration: _generation,
        availableCandidates: match.candidates,
      );
    } on Object {
      if (_isCurrent(generation)) {
        _setState(
          const SubjectSourcePlaybackState(
            phase: SubjectSourcePlaybackPhase.failed,
            errorCode: 'source_search_failed',
          ),
        );
      }
    }
  }

  Future<List<SourceSearchResult>> _persistedSubjectCandidates(
    Iterable<InstalledSourcePackage> packages,
  ) async {
    final candidates = <SourceSearchResult>[];
    for (final package in packages) {
      final provenance = revisionCalculator.calculate(package.package);
      final mapping = await mappingRepository.findValidSubjectMapping(
        bangumiSubjectId: subject.id,
        packageId: package.package.packageId,
        expectedProvenance: provenance,
      );
      if (mapping == null) continue;
      candidates.add(
        SourceSearchResult(
          sourceId: mapping.sourceSubject.sourceId,
          subjectId: mapping.sourceSubject.subjectId,
          title: subject.nameCn.isNotEmpty ? subject.nameCn : subject.name,
        ),
      );
    }
    return candidates;
  }

  Future<void> selectSubject(
    SourceSearchResult selected, {
    int? expectedGeneration,
    Iterable<SourceSearchResult>? availableCandidates,
  }) async {
    final generation = expectedGeneration ?? ++_generation;
    final sourceCandidates = List<SourceSearchResult>.unmodifiable(
      availableCandidates ??
          (_state.sourceCandidates.isNotEmpty
              ? _state.sourceCandidates
              : [selected]),
    );
    final package = _packageFor(selected.sourceId);
    if (package == null) {
      _setState(
        const SubjectSourcePlaybackState(
          phase: SubjectSourcePlaybackPhase.sourceActionRequired,
          errorCode: 'source_package_not_enabled',
        ),
      );
      return;
    }
    final sourceSubject = SourceSubjectIdentity(
      sourceId: selected.sourceId,
      subjectId: selected.subjectId,
    );
    final provenance = revisionCalculator.calculate(package.package);
    _setState(
      SubjectSourcePlaybackState(
        phase: SubjectSourcePlaybackPhase.loading,
        package: package,
        provenance: provenance,
        sourceSubject: sourceSubject,
        sourceCandidates: sourceCandidates,
      ),
    );
    try {
      final persisted = await mappingRepository.findValidSubjectMapping(
        bangumiSubjectId: subject.id,
        packageId: package.package.packageId,
        expectedProvenance: provenance,
      );
      if (!_isCurrent(generation)) return;
      if (persisted == null || persisted.sourceSubject != sourceSubject) {
        await mappingRepository.upsertSubjectMapping(
          SourceSubjectMapping(
            bangumiSubjectId: subject.id,
            packageId: package.package.packageId,
            sourceSubject: sourceSubject,
            provenance: provenance,
            mappingKind: expectedGeneration == null
                ? SubjectMappingKind.userConfirmed
                : SubjectMappingKind.automaticExactTitle,
            confirmedAt: DateTime.now().toUtc(),
          ),
        );
      }
      final detailsResult = await subjectPipeline.listSubjects(
        targets: [
          SourceInstalledLiveSubjectTarget(
            installedPackage: package,
            subject: sourceSubject,
          ),
        ],
      );
      if (!_isCurrent(generation)) return;
      final details = detailsResult.subjectResults
          .map((result) => result.details)
          .whereType<SourceSubjectDetails>()
          .firstOrNull;
      if (details == null) {
        _setState(
          SubjectSourcePlaybackState(
            phase:
                detailsResult.status ==
                    SourceInstalledLiveSubjectPipelineStatus.challengeRequired
                ? SubjectSourcePlaybackPhase.sourceActionRequired
                : detailsResult.status ==
                      SourceInstalledLiveSubjectPipelineStatus.notFound
                ? SubjectSourcePlaybackPhase.notFound
                : SubjectSourcePlaybackPhase.failed,
            package: package,
            provenance: provenance,
            sourceSubject: sourceSubject,
            sourceCandidates: sourceCandidates,
            errorCode: detailsResult.reasonCode,
          ),
        );
        return;
      }
      _setState(
        SubjectSourcePlaybackState(
          phase: SubjectSourcePlaybackPhase.ready,
          package: package,
          provenance: provenance,
          sourceSubject: sourceSubject,
          subjectDetails: details,
          sourceCandidates: sourceCandidates,
        ),
      );
    } on Object {
      if (_isCurrent(generation)) {
        _setState(
          SubjectSourcePlaybackState(
            phase: SubjectSourcePlaybackPhase.failed,
            package: package,
            provenance: provenance,
            sourceSubject: sourceSubject,
            sourceCandidates: sourceCandidates,
            errorCode: 'source_subject_failed',
          ),
        );
      }
    }
  }

  Future<SourceEpisodeResolution> resolveEpisode(
    BangumiEpisode episode, {
    String? preferredLineId,
  }) async {
    final current = _state;
    final package = current.package;
    final provenance = current.provenance;
    final sourceSubject = current.sourceSubject;
    final details = current.subjectDetails;
    if (package == null ||
        provenance == null ||
        sourceSubject == null ||
        details == null) {
      return const SourceEpisodeResolution(
        status: SourceEpisodeResolutionStatus.notFound,
        reason: 'source_subject_not_ready',
      );
    }
    SourceSubjectLine? preferredLine;
    if (preferredLineId != null) {
      if (preferredLineId.trim().isEmpty ||
          preferredLineId != preferredLineId.trim()) {
        return const SourceEpisodeResolution(
          status: SourceEpisodeResolutionStatus.notFound,
          reason: 'line_selection_invalid',
        );
      }
      for (final line in details.lines) {
        if (line.lineId == preferredLineId) {
          preferredLine = line;
          break;
        }
      }
      if (preferredLine == null) {
        return const SourceEpisodeResolution(
          status: SourceEpisodeResolutionStatus.notFound,
          reason: 'line_not_found',
        );
      }
    }
    final persisted = await mappingRepository.findValidEpisodeMapping(
      bangumiSubjectId: subject.id,
      bangumiEpisodeId: episode.id,
      packageId: package.package.packageId,
      expectedSourceSubject: sourceSubject,
      expectedProvenance: provenance,
    );
    if (persisted != null &&
        (preferredLineId == null ||
            persisted.sourceEpisode.lineId == preferredLineId)) {
      _setState(
        SubjectSourcePlaybackState(
          phase: SubjectSourcePlaybackPhase.ready,
          package: package,
          provenance: provenance,
          sourceSubject: sourceSubject,
          subjectDetails: details,
          sourceCandidates: current.sourceCandidates,
          selectedEpisode: persisted.sourceEpisode,
        ),
      );
      return SourceEpisodeResolution(
        status: SourceEpisodeResolutionStatus.ready,
        identity: persisted.sourceEpisode,
      );
    }

    final correlation = episodeCorrelator.correlate(
      episode: episode,
      details: details,
    );
    final correlatedCandidates = correlation.candidates
        .where(
          (candidate) =>
              preferredLineId == null ||
              candidate.identity.lineId == preferredLineId,
        )
        .toList(growable: false);
    final candidates = correlatedCandidates.isNotEmpty || preferredLine == null
        ? correlatedCandidates
        : preferredLine.episodes;
    if (candidates.length == 1) {
      final identity = candidates.single.identity;
      await mappingRepository.upsertEpisodeMapping(
        SourceEpisodeMapping(
          bangumiSubjectId: subject.id,
          bangumiEpisodeId: episode.id,
          packageId: package.package.packageId,
          sourceEpisode: identity,
          provenance: provenance,
          mappingKind: EpisodeMappingKind.automaticExactNumber,
          confirmedAt: DateTime.now().toUtc(),
        ),
      );
      _setState(
        SubjectSourcePlaybackState(
          phase: SubjectSourcePlaybackPhase.ready,
          package: package,
          provenance: provenance,
          sourceSubject: sourceSubject,
          subjectDetails: details,
          sourceCandidates: current.sourceCandidates,
          selectedEpisode: identity,
        ),
      );
      return SourceEpisodeResolution(
        status: SourceEpisodeResolutionStatus.ready,
        identity: identity,
      );
    }

    final notFound = candidates.isEmpty;
    _setState(
      SubjectSourcePlaybackState(
        phase: notFound
            ? SubjectSourcePlaybackPhase.notFound
            : SubjectSourcePlaybackPhase.episodeSelectionRequired,
        package: package,
        provenance: provenance,
        sourceSubject: sourceSubject,
        subjectDetails: details,
        sourceCandidates: current.sourceCandidates,
        episodeCandidates: candidates,
        errorCode: notFound ? 'episode_not_found' : correlation.reason,
      ),
    );
    return SourceEpisodeResolution(
      status: notFound
          ? SourceEpisodeResolutionStatus.notFound
          : SourceEpisodeResolutionStatus.selectionRequired,
      candidates: candidates,
      reason: notFound ? 'episode_not_found' : correlation.reason,
    );
  }

  Future<SourceEpisodeResolution> selectEpisode(
    BangumiEpisode episode,
    SourceEpisode selected,
  ) async {
    final current = _state;
    final package = current.package;
    final provenance = current.provenance;
    final sourceSubject = current.sourceSubject;
    final details = current.subjectDetails;
    if (package == null ||
        provenance == null ||
        sourceSubject == null ||
        details == null ||
        !details.lines.any(
          (line) => line.episodes.any(
            (episode) => episode.identity == selected.identity,
          ),
        )) {
      return const SourceEpisodeResolution(
        status: SourceEpisodeResolutionStatus.notFound,
        reason: 'episode_selection_invalid',
      );
    }
    await mappingRepository.upsertEpisodeMapping(
      SourceEpisodeMapping(
        bangumiSubjectId: subject.id,
        bangumiEpisodeId: episode.id,
        packageId: package.package.packageId,
        sourceEpisode: selected.identity,
        provenance: provenance,
        mappingKind: EpisodeMappingKind.userConfirmed,
        confirmedAt: DateTime.now().toUtc(),
      ),
    );
    _setState(
      SubjectSourcePlaybackState(
        phase: SubjectSourcePlaybackPhase.ready,
        package: package,
        provenance: provenance,
        sourceSubject: sourceSubject,
        subjectDetails: details,
        sourceCandidates: current.sourceCandidates,
        selectedEpisode: selected.identity,
      ),
    );
    return SourceEpisodeResolution(
      status: SourceEpisodeResolutionStatus.ready,
      identity: selected.identity,
    );
  }

  SourceInstalledLivePlaybackPipeline get livePlaybackPipeline =>
      playbackPipeline;

  @override
  void dispose() {
    _closed = true;
    sourcePackages.removeListener(_onSourcePackagesChanged);
    unawaited(closePlayback?.call());
    super.dispose();
  }

  void _onSourcePackagesChanged() {
    final next = _snapshotPackages(sourcePackages.enabledPackages);
    if (next == _packageSnapshot) return;
    _packageSnapshot = next;
    _generation++;
    unawaited(closePlayback?.call());
    _setState(
      SubjectSourcePlaybackState(
        phase: SubjectSourcePlaybackPhase.sourceActionRequired,
        errorCode: 'source_lifecycle_changed',
      ),
    );
  }

  InstalledSourcePackage? _packageFor(String sourceId) {
    for (final package in sourcePackages.enabledPackages) {
      if (package.package.packageId == sourceId) return package;
    }
    return null;
  }

  bool _isCurrent(int generation) => !_closed && generation == _generation;

  void _setState(SubjectSourcePlaybackState state) {
    if (_closed) return;
    _state = state;
    notifyListeners();
  }

  static String _snapshotPackages(
    Iterable<InstalledSourcePackage> packages,
  ) => packages
      .map(
        (package) =>
            '${package.package.packageId}@${package.package.version}:${package.status.name}:${package.requiresConsent}:${package.requiresReconsent}',
      )
      .join('|');
}
