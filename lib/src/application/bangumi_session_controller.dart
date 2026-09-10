// The public constructor deliberately keeps public parameter names while the
// implementation fields remain private.
// ignore_for_file: prefer_initializing_formals

import 'package:flutter/foundation.dart';

import 'bangumi_sync_service.dart';
import '../domain/models/bangumi_models.dart';
import '../domain/models/bangumi_sync_models.dart';
import '../domain/repositories/bangumi_local_store.dart';
import '../domain/services/bangumi_ports.dart';

enum BangumiConnectionStatus {
  disconnected,
  authorizing,
  connected,
  reauthRequired,
  failed,
}

enum BangumiAvailability { unavailable, available }

enum BangumiDetailPhase { idle, loading, ready, partial, fatal }

enum BangumiDetailSection { subject, episodes, characters, persons, relations }

final class BangumiSubjectDetailState {
  const BangumiSubjectDetailState({
    required this.subjectId,
    required this.phase,
    this.snapshot,
    this.loadingSections = const <BangumiDetailSection>{},
    this.failedSections = const <BangumiDetailSection>{},
    this.errors = const <BangumiDetailSection, String>{},
    this.remoteStateErrorCode,
  });

  final String subjectId;
  final BangumiDetailPhase phase;
  final BangumiSubjectDetailSnapshot? snapshot;
  final Set<BangumiDetailSection> loadingSections;
  final Set<BangumiDetailSection> failedSections;
  final Map<BangumiDetailSection, String> errors;
  final String? remoteStateErrorCode;

  bool get hasUsableSubject => snapshot?.subject != null;

  BangumiSubjectDetailState copyWith({
    BangumiDetailPhase? phase,
    BangumiSubjectDetailSnapshot? snapshot,
    Set<BangumiDetailSection>? loadingSections,
    Set<BangumiDetailSection>? failedSections,
    Map<BangumiDetailSection, String>? errors,
    String? remoteStateErrorCode,
  }) {
    return BangumiSubjectDetailState(
      subjectId: subjectId,
      phase: phase ?? this.phase,
      snapshot: snapshot ?? this.snapshot,
      loadingSections: loadingSections ?? this.loadingSections,
      failedSections: failedSections ?? this.failedSections,
      errors: errors ?? this.errors,
      remoteStateErrorCode: remoteStateErrorCode ?? this.remoteStateErrorCode,
    );
  }
}

typedef BangumiClientFactory =
    BangumiClient Function(BangumiAuthSession session);

final class BangumiSessionController extends ChangeNotifier {
  BangumiSessionController({
    required BangumiAuthenticationPort authentication,
    required BangumiLocalStore store,
    required BangumiClientFactory clientFactory,
    this.availability = BangumiAvailability.available,
    BangumiCallbackPort? callbackPort,
    Future<bool> Function(Uri uri)? openAuthorizationUri,
  }) : _authentication = authentication,
       _store = store,
       _clientFactory = clientFactory,
       _callbackPort = callbackPort,
       _openAuthorizationUri = openAuthorizationUri;

  final BangumiAuthenticationPort _authentication;
  final BangumiLocalStore _store;
  final BangumiClientFactory _clientFactory;
  final BangumiAvailability availability;
  final BangumiCallbackPort? _callbackPort;
  final Future<bool> Function(Uri uri)? _openAuthorizationUri;

  BangumiConnectionStatus status = BangumiConnectionStatus.disconnected;
  BangumiAccountSnapshot? account;
  List<BangumiScheduleEntry> schedule = const <BangumiScheduleEntry>[];
  List<BangumiCollectionEntry> collections = const <BangumiCollectionEntry>[];
  bool scheduleIsFresh = false;
  BangumiSubject? selectedSubject;
  BangumiEpisodePage? selectedEpisodes;
  BangumiRemoteState? selectedRemoteState;
  int pendingCount = 0;
  int conflictCount = 0;
  int blockedCount = 0;
  List<BangumiConflict> conflicts = const <BangumiConflict>[];
  final Map<String, BangumiSubjectDetailState> detailStates =
      <String, BangumiSubjectDetailState>{};
  String? errorCode;
  DateTime? scheduleUpdatedAt;
  int failedCount = 0;

  BangumiAuthSession? _session;
  BangumiClient? _client;
  Future<void>? _loginFuture;
  Future<void>? _syncFuture;
  Future<bool>? _refreshFuture;
  final Map<String, int> _detailGenerations = <String, int>{};

  bool get isAvailable => availability == BangumiAvailability.available;

  bool get isAuthenticated =>
      isAvailable &&
      _session != null &&
      status == BangumiConnectionStatus.connected;

  BangumiClient get client {
    final value = _client;
    if (value == null) throw const BangumiApiException(code: 'reauth_required');
    return value;
  }

  BangumiSubjectDetailState? subjectDetailState(String subjectId) =>
      detailStates[subjectId];

  Future<void> initialize() async {
    account = await _store.loadActiveAccount();
    collections = await _store.cachedCollections();
    schedule = await _store.cachedSchedule();
    scheduleUpdatedAt =
        account?.scheduleUpdatedAt ?? await _store.cachedScheduleUpdatedAt();
    scheduleIsFresh = false;
    status = !isAvailable
        ? BangumiConnectionStatus.disconnected
        : account == null
        ? BangumiConnectionStatus.disconnected
        : BangumiConnectionStatus.reauthRequired;
    await _refreshQueueCounts();
    notifyListeners();
    final resumedCallback = await _resumePendingSignIn();
    if (!resumedCallback) await _resumePersistedSession();
  }

  Future<bool> _resumePendingSignIn() async {
    final callbackPort = _callbackPort;
    if (callbackPort is! BangumiPendingCallbackPort) return false;

    final recoveryPort = callbackPort as BangumiPendingCallbackPort;
    final callback = await recoveryPort.takePendingCallback();
    if (callback == null) return false;

    status = BangumiConnectionStatus.authorizing;
    errorCode = null;
    notifyListeners();
    await completeSignIn(callback);
    return true;
  }

  Future<void> _resumePersistedSession() async {
    final persistence = _authentication is BangumiPersistentAuthenticationPort
        ? _authentication as BangumiPersistentAuthenticationPort
        : null;
    if (!isAvailable || persistence == null) {
      return;
    }

    BangumiAuthSession? storedSession;
    try {
      storedSession = await persistence.restoreSession();
    } on BangumiApiException catch (error) {
      if (error.code == 'oauth_session_storage_invalid') {
        await _clearStoredSessionBestEffort(persistence);
      }
      status = BangumiConnectionStatus.failed;
      errorCode = error.code;
      notifyListeners();
      return;
    } on BangumiPayloadException catch (error) {
      await _clearStoredSessionBestEffort(persistence);
      status = BangumiConnectionStatus.failed;
      errorCode = error.code;
      notifyListeners();
      return;
    } on Object {
      status = BangumiConnectionStatus.failed;
      errorCode = 'oauth_session_storage_failed';
      notifyListeners();
      return;
    }
    if (storedSession == null) return;

    status = BangumiConnectionStatus.authorizing;
    errorCode = null;
    notifyListeners();
    try {
      final refreshed = await _authentication.refresh(storedSession);
      if (refreshed.accountId != storedSession.accountId) {
        throw const BangumiApiException(code: 'account_mismatch');
      }
      await _activateSession(refreshed);
    } on BangumiApiException catch (error) {
      _session = null;
      _client = null;
      if (_shouldDiscardStoredSession(error.code)) {
        await _clearStoredSessionBestEffort(persistence);
      }
      _handleApiError(error);
      errorCode = error.code;
    } on BangumiPayloadException catch (error) {
      _session = null;
      _client = null;
      status = BangumiConnectionStatus.failed;
      errorCode = error.code;
    } on Object {
      _session = null;
      _client = null;
      status = BangumiConnectionStatus.failed;
      errorCode = 'session_restore_failed';
    }
    notifyListeners();
  }

  Future<void> signIn() async {
    if (!isAvailable) {
      status = BangumiConnectionStatus.disconnected;
      errorCode = 'bangumi_service_not_configured';
      notifyListeners();
      return;
    }
    final existing = _loginFuture;
    if (existing != null) return existing;
    final future = _signIn();
    _loginFuture = future;
    try {
      await future;
    } finally {
      if (identical(_loginFuture, future)) _loginFuture = null;
    }
  }

  Future<void> _signIn() async {
    status = BangumiConnectionStatus.authorizing;
    errorCode = null;
    notifyListeners();
    try {
      final request = await _authentication.begin();
      final open = _openAuthorizationUri;
      if (open == null || !await open(request.authorizationUri)) {
        status = BangumiConnectionStatus.disconnected;
        errorCode = 'authorization_not_opened';
        notifyListeners();
        return;
      }
      final callbackPort = _callbackPort;
      if (callbackPort == null) {
        status = BangumiConnectionStatus.authorizing;
        notifyListeners();
        return;
      }
      final callback = await callbackPort.waitForCallback();
      await completeSignIn(callback);
    } on BangumiApiException catch (error) {
      _handleApiError(error);
      errorCode = error.code;
      notifyListeners();
    } on Object {
      status = BangumiConnectionStatus.failed;
      errorCode = 'authorization_failed';
      notifyListeners();
    }
  }

  Future<void> completeSignIn(BangumiAuthCallback callback) async {
    if (!isAvailable) {
      status = BangumiConnectionStatus.disconnected;
      errorCode = 'bangumi_service_not_configured';
      notifyListeners();
      return;
    }
    try {
      final session = await _authentication.redeem(callback);
      await _activateSession(session);
    } on BangumiApiException catch (error) {
      _session = null;
      _client = null;
      _handleApiError(error);
      errorCode = error.code;
    } on Object {
      _session = null;
      _client = null;
      status = BangumiConnectionStatus.failed;
      errorCode = 'sign_in_failed';
    }
    notifyListeners();
  }

  Future<void> _activateSession(BangumiAuthSession session) async {
    final api = _clientFactory(session);
    final identity = await api.currentUser();
    if (identity.id != session.accountId) {
      throw const BangumiApiException(code: 'account_mismatch');
    }
    _session = session;
    _client = api;
    await _store.saveAccount(identity);
    account = await _store.loadActiveAccount();
    collections = await _store.cachedCollections();
    schedule = await _store.cachedSchedule();
    scheduleUpdatedAt =
        account?.scheduleUpdatedAt ?? await _store.cachedScheduleUpdatedAt();
    scheduleIsFresh = false;
    status = BangumiConnectionStatus.connected;
    errorCode = null;
    await refreshSchedule();
    await refreshCollections();
    await _refreshQueueCounts();
  }

  Future<void> refreshSession() async {
    await _tryRefreshSession();
    notifyListeners();
  }

  Future<void> refreshSchedule() async {
    try {
      final refreshed = await _withAuthenticatedClient((api) => api.calendar());
      await _store.cacheSchedule(refreshed);
      schedule = refreshed;
      scheduleUpdatedAt = await _store.cachedScheduleUpdatedAt();
      scheduleIsFresh = true;
      errorCode = null;
    } on BangumiApiException catch (error) {
      _handleApiError(error);
      errorCode = error.code;
    } on BangumiPayloadException catch (error) {
      errorCode = error.code;
    }
    notifyListeners();
  }

  Future<void> refreshCollections() async {
    try {
      final remoteCollections =
          await _withAuthenticatedClient<List<BangumiCollectionEntry>>((
            api,
          ) async {
            final all = <BangumiCollectionEntry>[];
            var offset = 0;
            for (var pageNumber = 0; pageNumber < 100; pageNumber++) {
              final page = await api.collections(offset: offset);
              all.addAll(page.collections);
              if (!page.hasMore || page.collections.isEmpty) break;
              offset += page.collections.length;
              if (pageNumber == 99) {
                throw const BangumiPayloadException(
                  'pagination_limit_exceeded',
                );
              }
            }
            return List<BangumiCollectionEntry>.unmodifiable(all);
          });
      collections = remoteCollections;
      for (final entry in remoteCollections) {
        await _store.cacheCollection(entry);
      }
      final cached = await _store.cachedCollections();
      final localFirst = await _store.localFirstCollections();
      final cachedBySubject = <String, BangumiCollectionEntry>{
        for (final entry in cached) entry.subjectId: entry,
      };
      for (final entry in localFirst) {
        cachedBySubject[entry.subjectId] = entry;
      }
      // The server list remains authoritative for membership, while the
      // cached row is authoritative for an in-flight local-first status. A
      // local-only queued subject is appended so it remains visible until its
      // first successful remote mutation.
      final remoteSubjectIds = remoteCollections
          .map((entry) => entry.subjectId)
          .toSet();
      collections = List.unmodifiable(<BangumiCollectionEntry>[
        ...remoteCollections.map(
          (entry) => cachedBySubject[entry.subjectId] ?? entry,
        ),
        ...localFirst.where(
          (entry) => !remoteSubjectIds.contains(entry.subjectId),
        ),
      ]);
      errorCode = null;
    } on BangumiApiException catch (error) {
      _handleApiError(error);
      errorCode = error.code;
    } on BangumiPayloadException catch (error) {
      errorCode = error.code;
    }
    notifyListeners();
  }

  Future<void> openSubject(String subjectId) async {
    await loadSubjectDetail(subjectId);
  }

  Future<void> loadSubjectDetail(
    String subjectId, {
    Set<BangumiDetailSection>? onlySections,
  }) async {
    final generation = (_detailGenerations[subjectId] ?? 0) + 1;
    _detailGenerations[subjectId] = generation;
    final existing = detailStates[subjectId];
    BangumiSubjectDetailSnapshot? cached;
    try {
      cached = await _store.cachedSubjectDetail(subjectId);
    } on Object {
      cached = existing?.snapshot;
    }
    if (!_isCurrentDetailGeneration(subjectId, generation)) return;

    final requested = onlySections ?? BangumiDetailSection.values.toSet();
    detailStates[subjectId] = BangumiSubjectDetailState(
      subjectId: subjectId,
      phase: BangumiDetailPhase.loading,
      snapshot: cached ?? existing?.snapshot,
      loadingSections: Set.unmodifiable(requested),
      failedSections: const <BangumiDetailSection>{},
      errors: const <BangumiDetailSection, String>{},
      remoteStateErrorCode: null,
    );
    notifyListeners();

    BangumiSubject? fetchedSubject;
    BangumiEpisodePage? fetchedEpisodes;
    List<BangumiCharacter>? fetchedCharacters;
    List<BangumiPersonCredit>? fetchedPersons;
    List<BangumiSubjectRelation>? fetchedRelations;
    BangumiRemoteState? fetchedRemoteState;
    final errors = <BangumiDetailSection, String>{
      if (onlySections != null) ...?existing?.errors,
    };
    if (onlySections != null) {
      for (final section in onlySections) {
        errors.remove(section);
      }
    }
    String? remoteStateErrorCode = onlySections == null
        ? null
        : existing?.remoteStateErrorCode;

    Future<T?> capture<T>(
      BangumiDetailSection section,
      Future<T> Function(BangumiClient api) operation,
    ) async {
      try {
        return await _withAuthenticatedClient(operation);
      } on BangumiApiException catch (error) {
        if (_isAuthenticationError(error.code)) rethrow;
        errors[section] = error.code;
      } on BangumiPayloadException catch (error) {
        errors[section] = error.code;
      } on Object {
        errors[section] = 'detail_section_failed';
      }
      return null;
    }

    try {
      if (requested.contains(BangumiDetailSection.subject)) {
        fetchedSubject = await capture(
          BangumiDetailSection.subject,
          (api) => api.subject(subjectId),
        );
      }
      if (requested.contains(BangumiDetailSection.episodes)) {
        fetchedEpisodes = await capture(
          BangumiDetailSection.episodes,
          (api) => api.episodes(subjectId),
        );
      }
      if (requested.contains(BangumiDetailSection.characters)) {
        fetchedCharacters = await capture(
          BangumiDetailSection.characters,
          (api) => api.characters(subjectId),
        );
      }
      if (requested.contains(BangumiDetailSection.persons)) {
        fetchedPersons = await capture(
          BangumiDetailSection.persons,
          (api) => api.persons(subjectId),
        );
      }
      if (requested.contains(BangumiDetailSection.relations)) {
        fetchedRelations = await capture(
          BangumiDetailSection.relations,
          (api) => api.relations(subjectId),
        );
      }
      if (onlySections == null) {
        try {
          fetchedRemoteState = await _withAuthenticatedClient(
            (api) => api.remoteState(subjectId),
          );
        } on BangumiApiException catch (error) {
          if (_isAuthenticationError(error.code)) rethrow;
          remoteStateErrorCode = error.code;
        } on BangumiPayloadException catch (error) {
          remoteStateErrorCode = error.code;
        } on Object {
          remoteStateErrorCode = 'detail_remote_state_failed';
        }
      }
    } on BangumiApiException catch (error) {
      _handleApiError(error);
      errorCode = error.code;
      for (final section in requested) {
        errors.putIfAbsent(section, () => error.code);
      }
    } on BangumiPayloadException catch (error) {
      errorCode = error.code;
      for (final section in requested) {
        errors.putIfAbsent(section, () => error.code);
      }
    }

    if (!_isCurrentDetailGeneration(subjectId, generation)) return;
    final previous = existing?.snapshot ?? cached;
    final subject = fetchedSubject ?? previous?.subject;
    if (subject == null) {
      final failed = errors.keys.toSet();
      detailStates[subjectId] = BangumiSubjectDetailState(
        subjectId: subjectId,
        phase: BangumiDetailPhase.fatal,
        snapshot: null,
        loadingSections: const <BangumiDetailSection>{},
        failedSections: Set.unmodifiable(failed),
        errors: Map.unmodifiable(errors),
        remoteStateErrorCode: remoteStateErrorCode,
      );
      notifyListeners();
      return;
    }

    final previousEpisodes =
        previous?.episodes ??
        const BangumiEpisodePage(
          episodes: <BangumiEpisode>[],
          offset: 0,
          limit: 100,
          total: 0,
        );
    BangumiCollectionEntry? statusFromCollection;
    for (final entry in collections) {
      if (entry.subjectId == subjectId) {
        statusFromCollection = entry;
        break;
      }
    }
    final remoteStatus =
        fetchedRemoteState?.status ??
        previous?.collectionStatus ??
        statusFromCollection?.status;
    final watched =
        fetchedRemoteState?.watchedEpisodeIds ??
        previous?.watchedEpisodeIds ??
        const <String>{};
    final snapshot = BangumiSubjectDetailSnapshot(
      subject: subject,
      episodes: fetchedEpisodes ?? previousEpisodes,
      characters:
          fetchedCharacters ??
          previous?.characters ??
          const <BangumiCharacter>[],
      persons:
          fetchedPersons ?? previous?.persons ?? const <BangumiPersonCredit>[],
      relations:
          fetchedRelations ??
          previous?.relations ??
          const <BangumiSubjectRelation>[],
      collectionStatus: remoteStatus,
      epStatus: statusFromCollection?.epStatus ?? previous?.epStatus,
      watchedEpisodeIds: Set.unmodifiable(watched),
      cachedAt: DateTime.now().toUtc(),
    );
    if (!_isCurrentDetailGeneration(subjectId, generation)) return;
    try {
      await _store.cacheSubjectDetail(snapshot);
      if (fetchedRemoteState != null) {
        if (fetchedRemoteState.accountId != _session?.accountId ||
            fetchedRemoteState.subjectId != subjectId) {
          throw const BangumiApiException(code: 'account_mismatch');
        }
        await _store.applyRemoteState(fetchedRemoteState);
      }
    } on BangumiApiException catch (error) {
      if (_isAuthenticationError(error.code)) _handleApiError(error);
      remoteStateErrorCode ??= error.code;
    } on Object {
      remoteStateErrorCode ??= 'detail_cache_failed';
    }

    final failedSections = errors.keys.toSet();
    final hasFailure =
        failedSections.isNotEmpty || remoteStateErrorCode != null;
    detailStates[subjectId] = BangumiSubjectDetailState(
      subjectId: subjectId,
      phase: hasFailure ? BangumiDetailPhase.partial : BangumiDetailPhase.ready,
      snapshot: snapshot,
      loadingSections: const <BangumiDetailSection>{},
      failedSections: Set.unmodifiable(failedSections),
      errors: Map.unmodifiable(errors),
      remoteStateErrorCode: remoteStateErrorCode,
    );
    _selectDetailSnapshot(subjectId, snapshot);
    errorCode = null;
    notifyListeners();
  }

  Future<void> retryDetail(String subjectId) => loadSubjectDetail(subjectId);

  Future<void> retryDetailSection(
    String subjectId,
    BangumiDetailSection section,
  ) => loadSubjectDetail(subjectId, onlySections: {section});

  bool _isCurrentDetailGeneration(String subjectId, int generation) =>
      _detailGenerations[subjectId] == generation;

  void _selectDetailSnapshot(
    String subjectId,
    BangumiSubjectDetailSnapshot snapshot,
  ) {
    selectedSubject = snapshot.subject;
    selectedEpisodes = snapshot.episodes;
    if (snapshot.collectionStatus != null ||
        snapshot.watchedEpisodeIds.isNotEmpty) {
      final accountId = _session?.accountId ?? account?.accountId;
      if (accountId != null) {
        selectedRemoteState = BangumiRemoteState(
          accountId: accountId,
          subjectId: subjectId,
          status: snapshot.collectionStatus,
          watchedEpisodeIds: Set.unmodifiable(snapshot.watchedEpisodeIds),
          remoteRevision: BangumiRemoteState.fingerprint(
            subjectId: subjectId,
            status: snapshot.collectionStatus,
            watchedEpisodeIds: snapshot.watchedEpisodeIds,
          ),
        );
      }
    }
  }

  Future<void> setCollectionStatus(
    String subjectId,
    BangumiCollectionStatus value,
  ) async {
    await _store.saveCollectionStatus(subjectId, value);
    final selected = selectedRemoteState;
    if (selected != null && selected.subjectId == subjectId) {
      selectedRemoteState = BangumiRemoteState(
        accountId: selected.accountId,
        subjectId: selected.subjectId,
        status: value,
        watchedEpisodeIds: selected.watchedEpisodeIds,
        remoteRevision: selected.remoteRevision,
      );
    }
    collections = List.unmodifiable(
      collections.map(
        (entry) => entry.subjectId == subjectId
            ? BangumiCollectionEntry(
                subjectId: entry.subjectId,
                status: value,
                name: entry.name,
                nameCn: entry.nameCn,
                imageUrl: entry.imageUrl,
                totalEpisodes: entry.totalEpisodes,
                epStatus: entry.epStatus,
              )
            : entry,
      ),
    );
    final detail = detailStates[subjectId];
    final detailSnapshot = detail?.snapshot;
    if (detail != null && detailSnapshot != null) {
      final updated = BangumiSubjectDetailSnapshot(
        subject: detailSnapshot.subject,
        episodes: detailSnapshot.episodes,
        characters: detailSnapshot.characters,
        persons: detailSnapshot.persons,
        relations: detailSnapshot.relations,
        collectionStatus: value,
        epStatus: detailSnapshot.epStatus,
        watchedEpisodeIds: detailSnapshot.watchedEpisodeIds,
        cachedAt: detailSnapshot.cachedAt,
      );
      detailStates[subjectId] = BangumiSubjectDetailState(
        subjectId: detail.subjectId,
        phase: detail.phase,
        snapshot: updated,
        loadingSections: detail.loadingSections,
        failedSections: detail.failedSections,
        errors: detail.errors,
        remoteStateErrorCode: detail.remoteStateErrorCode,
      );
      _selectDetailSnapshot(subjectId, updated);
    }
    await _refreshQueueCounts();
    notifyListeners();
  }

  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  ) async {
    await _store.setEpisodeWatched(subjectId, episodeId, watched);
    final selected = selectedRemoteState;
    if (selected != null && selected.subjectId == subjectId) {
      final watchedEpisodeIds = {...selected.watchedEpisodeIds};
      if (watched) {
        watchedEpisodeIds.add(episodeId);
      } else {
        watchedEpisodeIds.remove(episodeId);
      }
      selectedRemoteState = BangumiRemoteState(
        accountId: selected.accountId,
        subjectId: selected.subjectId,
        status: selected.status,
        watchedEpisodeIds: Set.unmodifiable(watchedEpisodeIds),
        remoteRevision: selected.remoteRevision,
      );
    }
    final detail = detailStates[subjectId];
    final detailSnapshot = detail?.snapshot;
    if (detail != null && detailSnapshot != null) {
      final watchedEpisodeIds = {...detailSnapshot.watchedEpisodeIds};
      if (watched) {
        watchedEpisodeIds.add(episodeId);
      } else {
        watchedEpisodeIds.remove(episodeId);
      }
      final updated = BangumiSubjectDetailSnapshot(
        subject: detailSnapshot.subject,
        episodes: detailSnapshot.episodes,
        characters: detailSnapshot.characters,
        persons: detailSnapshot.persons,
        relations: detailSnapshot.relations,
        collectionStatus: detailSnapshot.collectionStatus,
        epStatus: detailSnapshot.epStatus,
        watchedEpisodeIds: Set.unmodifiable(watchedEpisodeIds),
        cachedAt: detailSnapshot.cachedAt,
      );
      detailStates[subjectId] = BangumiSubjectDetailState(
        subjectId: detail.subjectId,
        phase: detail.phase,
        snapshot: updated,
        loadingSections: detail.loadingSections,
        failedSections: detail.failedSections,
        errors: detail.errors,
        remoteStateErrorCode: detail.remoteStateErrorCode,
      );
      _selectDetailSnapshot(subjectId, updated);
    }
    await _refreshQueueCounts();
    notifyListeners();
  }

  Future<void> syncNow({bool forceRetry = true}) async {
    final existing = _syncFuture;
    if (existing != null) return existing;
    final future = _performSyncNow(forceRetry: forceRetry);
    _syncFuture = future;
    try {
      await future;
    } finally {
      if (identical(_syncFuture, future)) _syncFuture = null;
    }
  }

  Future<void> _performSyncNow({required bool forceRetry}) async {
    try {
      await _ensureFreshSession();
      await refreshCollections();
      final result = await _syncWithRefresh(forceRetry: forceRetry);
      final selectedId = selectedSubject?.id;
      if (result.processed > 0 && selectedId != null) {
        await openSubject(selectedId);
      }
      await _reloadCollectionsFromCache();
      await _refreshQueueCounts();
      errorCode = result.errorCode;
    } on BangumiApiException catch (error) {
      _handleApiError(error);
      errorCode = error.code;
    } on BangumiPayloadException catch (error) {
      errorCode = error.code;
    }
    notifyListeners();
  }

  Future<void> refreshQueueCounts() async {
    await _refreshQueueCounts();
    notifyListeners();
  }

  Future<void> _refreshQueueCounts() async {
    pendingCount = await _store.pendingCount();
    blockedCount = await _store.blockedCount();
    failedCount = await _store.failedCount();
    conflicts = await _store.conflicts();
    conflictCount = conflicts.length;
  }

  Future<void> _reloadCollectionsFromCache() async {
    final cached = await _store.cachedCollections();
    final localFirst = await _store.localFirstCollections();
    if (cached.isEmpty && localFirst.isEmpty) return;
    final cachedBySubject = <String, BangumiCollectionEntry>{
      for (final entry in cached) entry.subjectId: entry,
    };
    for (final entry in localFirst) {
      cachedBySubject[entry.subjectId] = entry;
    }
    if (collections.isEmpty) {
      collections = List.unmodifiable(cachedBySubject.values);
      return;
    }
    final existingSubjectIds = collections
        .map((entry) => entry.subjectId)
        .toSet();
    collections = List.unmodifiable(<BangumiCollectionEntry>[
      ...collections.map((entry) => cachedBySubject[entry.subjectId] ?? entry),
      ...localFirst.where(
        (entry) => !existingSubjectIds.contains(entry.subjectId),
      ),
    ]);
  }

  void _handleApiError(BangumiApiException error) {
    if (_isAuthenticationError(error.code) || error.code == 'reauth_required') {
      _session = null;
      _client = null;
      status = BangumiConnectionStatus.reauthRequired;
    } else {
      status = BangumiConnectionStatus.failed;
    }
  }

  Future<void> adoptConflict(BangumiConflict conflict) async {
    await _store.adoptRemote(conflict);
    await _refreshQueueCounts();
    notifyListeners();
  }

  Future<void> requeueConflict(BangumiConflict conflict) async {
    await _store.requeueAgainstRevision(
      conflict.operation,
      conflict.remoteState.remoteRevision,
    );
    await _refreshQueueCounts();
    notifyListeners();
  }

  Future<void> signOut() async {
    await _authentication.signOut();
    await _store.deactivateActiveAccount();
    _session = null;
    _client = null;
    account = null;
    schedule = const <BangumiScheduleEntry>[];
    collections = const <BangumiCollectionEntry>[];
    selectedSubject = null;
    selectedEpisodes = null;
    selectedRemoteState = null;
    detailStates.clear();
    _detailGenerations.clear();
    schedule = const <BangumiScheduleEntry>[];
    scheduleUpdatedAt = null;
    scheduleIsFresh = false;
    status = BangumiConnectionStatus.disconnected;
    errorCode = null;
    await _refreshQueueCounts();
    notifyListeners();
  }

  Future<void> close() async {
    await _callbackPort?.close();
  }

  Future<T> _withAuthenticatedClient<T>(
    Future<T> Function(BangumiClient api) operation,
  ) async {
    if (!isAvailable) {
      throw const BangumiApiException(code: 'bangumi_service_not_configured');
    }
    await _ensureFreshSession();
    var api = _client;
    if (api == null) {
      throw const BangumiApiException(code: 'reauth_required');
    }
    try {
      return await operation(api);
    } on BangumiApiException catch (error) {
      if (!_isAuthenticationError(error.code) || !await _tryRefreshSession()) {
        rethrow;
      }
      api = _client;
      if (api == null) {
        throw const BangumiApiException(code: 'reauth_required');
      }
      return operation(api);
    }
  }

  Future<void> _ensureFreshSession() async {
    if (!isAvailable) {
      throw const BangumiApiException(code: 'bangumi_service_not_configured');
    }
    final session = _session;
    if (session == null) {
      throw const BangumiApiException(code: 'reauth_required');
    }
    if (!session.isExpired) return;
    if (!await _tryRefreshSession()) {
      throw const BangumiApiException(code: 'reauth_required');
    }
  }

  Future<bool> _tryRefreshSession() async {
    final existing = _refreshFuture;
    if (existing != null) return existing;
    final future = _performRefreshSession();
    _refreshFuture = future;
    try {
      return await future;
    } finally {
      if (identical(_refreshFuture, future)) _refreshFuture = null;
    }
  }

  Future<bool> _performRefreshSession() async {
    final session = _session;
    if (session == null) {
      status = BangumiConnectionStatus.reauthRequired;
      errorCode = 'reauth_required';
      return false;
    }
    try {
      final refreshed = await _authentication.refresh(session);
      if (refreshed.accountId != session.accountId) {
        throw const BangumiApiException(code: 'account_mismatch');
      }
      _session = refreshed;
      _client = _clientFactory(refreshed);
      status = BangumiConnectionStatus.connected;
      errorCode = null;
      return true;
    } on BangumiApiException catch (error) {
      _session = null;
      _client = null;
      if (_shouldDiscardStoredSession(error.code)) {
        final sessionPersistence =
            _authentication is BangumiPersistentAuthenticationPort
            ? _authentication as BangumiPersistentAuthenticationPort
            : null;
        if (sessionPersistence != null) {
          await _clearStoredSessionBestEffort(sessionPersistence);
        }
      }
      status = BangumiConnectionStatus.reauthRequired;
      errorCode = error.code;
      return false;
    } on BangumiPayloadException catch (error) {
      _session = null;
      _client = null;
      status = BangumiConnectionStatus.reauthRequired;
      errorCode = error.code;
      return false;
    } on Object {
      _session = null;
      _client = null;
      status = BangumiConnectionStatus.reauthRequired;
      errorCode = 'reauth_required';
      return false;
    }
  }

  Future<BangumiSyncResult> _syncWithRefresh({required bool forceRetry}) async {
    for (var attempt = 0; attempt < 2; attempt++) {
      final session = _session;
      final api = _client;
      if (session == null || api == null) {
        throw const BangumiApiException(code: 'reauth_required');
      }
      try {
        return await BangumiSyncService(
          client: api,
          store: _store,
          sessionProvider: () => _session ?? session,
        ).syncNow(forceRetry: forceRetry);
      } on BangumiApiException catch (error) {
        if (attempt == 0 &&
            _isAuthenticationError(error.code) &&
            await _tryRefreshSession()) {
          continue;
        }
        rethrow;
      }
    }
    throw const BangumiApiException(code: 'reauth_required');
  }

  static bool _isAuthenticationError(String code) =>
      code == 'auth_required' || code == 'account_mismatch';

  static bool _shouldDiscardStoredSession(String code) =>
      code == 'reauth_required' ||
      code == 'oauth_rejected' ||
      code == 'oauth_account_mismatch' ||
      code == 'account_mismatch' ||
      code == 'oauth_session_storage_invalid';

  Future<void> _clearStoredSessionBestEffort(
    BangumiPersistentAuthenticationPort persistence,
  ) async {
    try {
      await persistence.clearStoredSession();
    } on Object {
      // Keep the original authentication error visible. A later explicit
      // sign-out can retry clearing the platform store.
    }
  }
}
