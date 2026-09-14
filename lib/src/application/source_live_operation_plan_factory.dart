import 'package:pub_semver/pub_semver.dart';

import '../domain/models/source_episode_normalization_models.dart';
import '../domain/models/source_http_models.dart';
import '../domain/models/source_identity.dart';
import '../domain/models/source_package_live_operations.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_playable_normalization_models.dart';
import '../domain/models/source_rule_program.dart';
import '../domain/models/source_search_normalization_models.dart';
import 'source_live_episode_coordinator.dart';
import 'source_live_http_request_coordinator.dart';
import 'source_live_playable_source_coordinator.dart';
import 'source_live_search_coordinator.dart';

/// The bounded outcomes of turning package metadata into an existing live
/// operation plan.
enum SourceLiveOperationPlanFactoryStatus {
  ready,
  consentRequired,
  disabled,
  incompatible,
  operationNotFound,
  programNotFound,
  invalidInput,
  invalidRequest,
  failed,
}

/// A typed, immutable plan-factory result. Rejected results never retain the
/// expanded URI or any request headers; only a safe reason token remains.
final class SourceLiveOperationPlanResult<T> {
  SourceLiveOperationPlanResult({
    required this.packageId,
    required this.packageVersion,
    required this.operation,
    required this.programId,
    required this.status,
    this.plan,
    this.reasonCode,
  }) {
    final isReady = status == SourceLiveOperationPlanFactoryStatus.ready;
    if (isReady) {
      if (plan == null || reasonCode != null) {
        throw ArgumentError(
          'A ready plan result requires a plan and no reason code.',
        );
      }
    } else if (plan != null || reasonCode == null || !_safeToken(reasonCode!)) {
      throw ArgumentError(
        'A rejected plan result requires one safe reason and no plan.',
      );
    }
  }

  final String packageId;
  final Version packageVersion;
  final SourcePackageLiveOperationKind operation;
  final String programId;
  final SourceLiveOperationPlanFactoryStatus status;
  final T? plan;
  final String? reasonCode;

  Map<String, Object?> toRedactedDiagnostic() => {
    'packageId': packageId,
    'packageVersion': packageVersion.toString(),
    'operation': operation.name,
    'programId': programId,
    'status': status.name,
    'hasPlan': plan != null,
    'reasonCode': reasonCode,
  };

  @override
  String toString() => toRedactedDiagnostic().toString();

  static bool _safeToken(String value) =>
      value.isNotEmpty &&
      value.length <= 64 &&
      RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(value);
}

/// Materializes the existing live search, episode and playable-source plan
/// types from package-declared metadata.
///
/// This is a synchronous composition boundary. It performs no transport,
/// package lookup, source selection, normalization or playback. All request
/// construction is admitted through [SourceLiveHttpRequestCoordinator], so
/// lifecycle, compatibility and package-policy checks happen before a caller
/// can pass a plan to a live coordinator.
final class SourceLiveOperationPlanFactory {
  SourceLiveOperationPlanFactory({required Version wynimeVersion})
    : _requestCoordinator = SourceLiveHttpRequestCoordinator(
        wynimeVersion: wynimeVersion,
      );

  final SourceLiveHttpRequestCoordinator _requestCoordinator;

  SourceLiveOperationPlanResult<SourceLiveSearchPlan> buildSearchPlan({
    required InstalledSourcePackage installedPackage,
    required String query,
  }) {
    final normalizedQuery = query.trim();
    if (!_validText(normalizedQuery, maxLength: 128, allowEmpty: false)) {
      return _failure(
        installedPackage,
        SourcePackageLiveOperationKind.search,
        status: SourceLiveOperationPlanFactoryStatus.invalidInput,
        reasonCode: 'invalid_search_query',
      );
    }

    final admission = _admit(
      installedPackage,
      SourcePackageLiveOperationKind.search,
      {'query': normalizedQuery},
    );
    if (!admission.isReady) {
      return _failureFromAdmission(installedPackage, admission);
    }

    try {
      final mapping = admission.operation!.mapping as SourceSearchFieldMapping;
      return _ready(
        installedPackage,
        admission.operation!,
        SourceLiveSearchPlan(
          requestPlan: admission.requestPlan!,
          mapping: mapping,
        ),
      );
    } on Object {
      return _failure(
        installedPackage,
        SourcePackageLiveOperationKind.search,
        programId: admission.programId,
        status: SourceLiveOperationPlanFactoryStatus.failed,
        reasonCode: 'operation_mapping_invalid',
      );
    }
  }

  SourceLiveOperationPlanResult<SourceLiveEpisodePlan> buildEpisodePlan({
    required InstalledSourcePackage installedPackage,
    required SourceEpisodeIdentity episode,
  }) {
    if (!_validIdentity(episode) ||
        episode.sourceId != installedPackage.package.packageId) {
      return _failure(
        installedPackage,
        SourcePackageLiveOperationKind.episode,
        status: SourceLiveOperationPlanFactoryStatus.invalidInput,
        reasonCode: 'episode_identity_mismatch',
      );
    }

    final admission = _admit(
      installedPackage,
      SourcePackageLiveOperationKind.episode,
      _identityValues(episode),
    );
    if (!admission.isReady) {
      return _failureFromAdmission(installedPackage, admission);
    }

    try {
      final mapping = admission.operation!.mapping as SourceEpisodeFieldMapping;
      return _ready(
        installedPackage,
        admission.operation!,
        SourceLiveEpisodePlan(
          requestPlan: admission.requestPlan!,
          mapping: mapping,
        ),
      );
    } on Object {
      return _failure(
        installedPackage,
        SourcePackageLiveOperationKind.episode,
        programId: admission.programId,
        status: SourceLiveOperationPlanFactoryStatus.failed,
        reasonCode: 'operation_mapping_invalid',
      );
    }
  }

  SourceLiveOperationPlanResult<SourceLivePlayableSourcePlan>
  buildPlayableSourcePlan({
    required InstalledSourcePackage installedPackage,
    required SourceEpisodeIdentity episode,
  }) {
    if (!_validIdentity(episode) ||
        episode.sourceId != installedPackage.package.packageId) {
      return _failure(
        installedPackage,
        SourcePackageLiveOperationKind.playableSource,
        status: SourceLiveOperationPlanFactoryStatus.invalidInput,
        reasonCode: 'episode_identity_mismatch',
      );
    }

    final admission = _admit(
      installedPackage,
      SourcePackageLiveOperationKind.playableSource,
      _identityValues(episode),
    );
    if (!admission.isReady) {
      return _failureFromAdmission(installedPackage, admission);
    }

    try {
      final mapping =
          admission.operation!.mapping as SourcePlayableSourceFieldMapping;
      return _ready(
        installedPackage,
        admission.operation!,
        SourceLivePlayableSourcePlan(
          requestPlan: admission.requestPlan!,
          episode: episode,
          mapping: mapping,
        ),
      );
    } on Object {
      return _failure(
        installedPackage,
        SourcePackageLiveOperationKind.playableSource,
        programId: admission.programId,
        status: SourceLiveOperationPlanFactoryStatus.failed,
        reasonCode: 'operation_mapping_invalid',
      );
    }
  }

  _OperationAdmission _admit(
    InstalledSourcePackage installedPackage,
    SourcePackageLiveOperationKind kind,
    Map<String, String> values,
  ) {
    final package = installedPackage.package;
    final matchingOperations = package.liveOperations
        .where((candidate) => candidate.kind == kind)
        .toList(growable: false);
    if (matchingOperations.length > 1) {
      return _OperationAdmission.rejected(
        kind: kind,
        programId: 'operation_duplicate',
        status: SourceLiveOperationPlanFactoryStatus.failed,
        reasonCode: 'duplicate_operation',
      );
    }
    final operation = matchingOperations.isEmpty
        ? null
        : matchingOperations.single;
    if (operation == null) {
      return _OperationAdmission.rejected(
        kind: kind,
        programId: 'operation_missing',
        status: SourceLiveOperationPlanFactoryStatus.operationNotFound,
        reasonCode: 'operation_not_declared',
      );
    }
    if (installedPackage.requiresConsent ||
        installedPackage.requiresReconsent) {
      return _OperationAdmission.rejected(
        kind: kind,
        programId: operation.programId,
        status: SourceLiveOperationPlanFactoryStatus.consentRequired,
        reasonCode: 'consent_required',
      );
    }
    if (installedPackage.status != SourcePackageStatus.enabled) {
      return _OperationAdmission.rejected(
        kind: kind,
        programId: operation.programId,
        status: SourceLiveOperationPlanFactoryStatus.disabled,
        reasonCode: 'package_disabled',
      );
    }
    if (!package.isCompatibleWith(_requestCoordinator.wynimeVersion)) {
      return _OperationAdmission.rejected(
        kind: kind,
        programId: operation.programId,
        status: SourceLiveOperationPlanFactoryStatus.incompatible,
        reasonCode: 'incompatible_wynime_version',
      );
    }

    final SourceRuleProgram program;
    try {
      program = package.programById(operation.programId);
    } on StateError {
      return _OperationAdmission.rejected(
        kind: kind,
        programId: operation.programId,
        status: SourceLiveOperationPlanFactoryStatus.programNotFound,
        reasonCode: 'program_not_found',
      );
    } on Object {
      return _OperationAdmission.rejected(
        kind: kind,
        programId: operation.programId,
        status: SourceLiveOperationPlanFactoryStatus.failed,
        reasonCode: 'package_preflight_failed',
      );
    }
    if (!_operationMetadataMatchesProgram(operation, program)) {
      return _OperationAdmission.rejected(
        kind: kind,
        programId: operation.programId,
        status: SourceLiveOperationPlanFactoryStatus.failed,
        reasonCode: 'operation_metadata_invalid',
      );
    }

    try {
      final request = SourceHttpRequest(
        uri: operation.requestTemplate.expand(values),
        securityPolicy: package.securityPolicy,
      );
      final plan = SourceLiveHttpRequestPlan(
        installedPackage: installedPackage,
        programId: operation.programId,
        request: request,
      );
      final admitted = _requestCoordinator.prepare(plan);
      if (admitted.status != SourceLiveHttpRequestStatus.ready) {
        return _OperationAdmission.rejected(
          kind: kind,
          programId: operation.programId,
          status: _mapStatus(admitted.status),
          reasonCode: admitted.reasonCode ?? 'live_request_rejected',
        );
      }
      return _OperationAdmission.ready(
        kind: kind,
        programId: operation.programId,
        operation: operation,
        requestPlan: plan,
      );
    } on ArgumentError {
      return _OperationAdmission.rejected(
        kind: kind,
        programId: operation.programId,
        status: SourceLiveOperationPlanFactoryStatus.invalidRequest,
        reasonCode: 'source_http_request_invalid',
      );
    } on Object {
      return _OperationAdmission.rejected(
        kind: kind,
        programId: operation.programId,
        status: SourceLiveOperationPlanFactoryStatus.failed,
        reasonCode: 'operation_request_failed',
      );
    }
  }

  static SourceLiveOperationPlanFactoryStatus _mapStatus(
    SourceLiveHttpRequestStatus status,
  ) {
    return switch (status) {
      SourceLiveHttpRequestStatus.ready =>
        SourceLiveOperationPlanFactoryStatus.ready,
      SourceLiveHttpRequestStatus.consentRequired =>
        SourceLiveOperationPlanFactoryStatus.consentRequired,
      SourceLiveHttpRequestStatus.disabled =>
        SourceLiveOperationPlanFactoryStatus.disabled,
      SourceLiveHttpRequestStatus.incompatible =>
        SourceLiveOperationPlanFactoryStatus.incompatible,
      SourceLiveHttpRequestStatus.programNotFound =>
        SourceLiveOperationPlanFactoryStatus.programNotFound,
      SourceLiveHttpRequestStatus.invalidRequest =>
        SourceLiveOperationPlanFactoryStatus.invalidRequest,
      SourceLiveHttpRequestStatus.failed =>
        SourceLiveOperationPlanFactoryStatus.failed,
    };
  }

  SourceLiveOperationPlanResult<T> _failureFromAdmission<T>(
    InstalledSourcePackage installedPackage,
    _OperationAdmission admission,
  ) {
    return _failure(
      installedPackage,
      admission.kind,
      programId: admission.programId,
      status: admission.status,
      reasonCode: admission.reasonCode!,
    );
  }

  SourceLiveOperationPlanResult<T> _ready<T>(
    InstalledSourcePackage installedPackage,
    SourcePackageLiveOperation operation,
    T plan,
  ) {
    final package = installedPackage.package;
    return SourceLiveOperationPlanResult(
      packageId: package.packageId,
      packageVersion: package.version,
      operation: operation.kind,
      programId: operation.programId,
      status: SourceLiveOperationPlanFactoryStatus.ready,
      plan: plan,
    );
  }

  SourceLiveOperationPlanResult<T> _failure<T>(
    InstalledSourcePackage installedPackage,
    SourcePackageLiveOperationKind kind, {
    String programId = 'operation_unavailable',
    required SourceLiveOperationPlanFactoryStatus status,
    required String reasonCode,
  }) {
    final package = installedPackage.package;
    return SourceLiveOperationPlanResult(
      packageId: package.packageId,
      packageVersion: package.version,
      operation: kind,
      programId: programId,
      status: status,
      reasonCode: reasonCode,
    );
  }

  static Map<String, String> _identityValues(SourceEpisodeIdentity episode) => {
    'sourceId': episode.sourceId,
    'lineId': episode.lineId,
    'subjectId': episode.subjectId,
    'episodeId': episode.episodeId,
  };

  static bool _operationMetadataMatchesProgram(
    SourcePackageLiveOperation operation,
    SourceRuleProgram program,
  ) {
    final fieldNames = program.fields.map((field) => field.name).toSet();
    if (!fieldNames.containsAll(operation.mappingFieldNames)) return false;
    final allowedPlaceholders = switch (operation.kind) {
      SourcePackageLiveOperationKind.search => const {'query'},
      SourcePackageLiveOperationKind.episode => const {
        'sourceId',
        'lineId',
        'subjectId',
        'episodeId',
      },
      SourcePackageLiveOperationKind.playableSource => const {
        'sourceId',
        'lineId',
        'subjectId',
        'episodeId',
      },
    };
    return allowedPlaceholders.containsAll(
      operation.requestTemplate.placeholders,
    );
  }

  static bool _validIdentity(SourceEpisodeIdentity episode) {
    return [
      episode.sourceId,
      episode.lineId,
      episode.subjectId,
      episode.episodeId,
    ].every((value) => _validText(value, maxLength: 128, allowEmpty: false));
  }

  static bool _validText(
    String value, {
    required int maxLength,
    required bool allowEmpty,
  }) {
    final normalized = value.trim();
    return normalized == value &&
        (allowEmpty || normalized.isNotEmpty) &&
        normalized.length <= maxLength &&
        !_hasControlCharacter(normalized);
  }

  static bool _hasControlCharacter(String value) => value.codeUnits.any(
    (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
  );
}

final class _OperationAdmission {
  const _OperationAdmission._({
    required this.kind,
    required this.programId,
    required this.status,
    required this.reasonCode,
    this.operation,
    this.requestPlan,
  });

  const _OperationAdmission.ready({
    required SourcePackageLiveOperationKind kind,
    required String programId,
    required SourcePackageLiveOperation operation,
    required SourceLiveHttpRequestPlan requestPlan,
  }) : this._(
         kind: kind,
         programId: programId,
         status: SourceLiveOperationPlanFactoryStatus.ready,
         reasonCode: null,
         operation: operation,
         requestPlan: requestPlan,
       );

  const _OperationAdmission.rejected({
    required SourcePackageLiveOperationKind kind,
    required String programId,
    required SourceLiveOperationPlanFactoryStatus status,
    required String reasonCode,
  }) : this._(
         kind: kind,
         programId: programId,
         status: status,
         reasonCode: reasonCode,
       );

  final SourcePackageLiveOperationKind kind;
  final String programId;
  final SourceLiveOperationPlanFactoryStatus status;
  final String? reasonCode;
  final SourcePackageLiveOperation? operation;
  final SourceLiveHttpRequestPlan? requestPlan;

  bool get isReady => status == SourceLiveOperationPlanFactoryStatus.ready;
}
