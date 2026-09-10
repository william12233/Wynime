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
  String? errorCode;
  DateTime? scheduleUpdatedAt;
  int failedCount = 0;

  BangumiAuthSession? _session;
  BangumiClient? _client;
  Future<void>? _loginFuture;
  Future<void>? _syncFuture;
  Future<bool>? _refreshFuture;

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
      // Capture subjects before replacing the remote membership page. This
      // lets an explicit refresh observe remote removals and external edits
      // for subjects that are no longer returned by Bangumi collections.
      final cachedBefore = await _store.cachedCollections();
      final localFirstBefore = await _store.localFirstCollections();
      final pendingBefore = await _store.pendingOperations(forceRetry: true);
      final conflictsBefore = await _store.conflictOperations();
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
      await _withAuthenticatedClient((api) async {
        for (final entry in remoteCollections) {
          try {
            final subject = await api.subject(entry.subjectId);
            await _store.cacheSubject(subject);
            try {
              final episodes = await api.episodes(entry.subjectId);
              await _store.cacheEpisodes(episodes);
            } on BangumiApiException catch (error) {
              if (_isAuthenticationError(error.code)) rethrow;
            } on Object {
              // A single unavailable episode page must not discard the
              // collection page or other subjects.
            }
          } on BangumiApiException catch (error) {
            if (_isAuthenticationError(error.code)) rethrow;
            // A single unavailable subject must not discard the collection
            // page or other subjects.
          } on Object {
            // Keep refreshing the remaining subjects.
          }
        }

        final subjectIds = <String>{
          ...remoteCollections.map((entry) => entry.subjectId),
          ...cachedBefore.map((entry) => entry.subjectId),
          ...localFirstBefore.map((entry) => entry.subjectId),
          ...pendingBefore.map((operation) => operation.subjectId),
          ...conflictsBefore.map((operation) => operation.subjectId),
        };
        for (final subjectId in subjectIds) {
          try {
            final remote = await api.remoteState(subjectId);
            if (remote.accountId != _session?.accountId ||
                remote.subjectId != subjectId) {
              throw const BangumiApiException(code: 'account_mismatch');
            }
            await _store.applyRemoteState(remote);
          } on BangumiApiException catch (error) {
            if (_isAuthenticationError(error.code)) rethrow;
            // Keep the last known remote state when one subject is temporarily
            // unavailable; a later explicit sync can retry it.
          } on Object {
            // A single remote-state failure must not hide other collections.
          }
        }
      });
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
    try {
      late BangumiSubject subject;
      late BangumiEpisodePage episodes;
      late BangumiRemoteState remote;
      await _withAuthenticatedClient((api) async {
        subject = await api.subject(subjectId);
        episodes = await api.episodes(subjectId);
        remote = await api.remoteState(subjectId);
      });
      await _store.cacheSubject(subject);
      await _store.cacheEpisodes(episodes);
      await _store.applyRemoteState(remote);
      selectedSubject = subject;
      selectedEpisodes = episodes;
      selectedRemoteState = remote;
      errorCode = null;
    } on BangumiApiException catch (error) {
      _handleApiError(error);
      errorCode = error.code;
    } on BangumiPayloadException catch (error) {
      errorCode = error.code;
    }
    notifyListeners();
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
              )
            : entry,
      ),
    );
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
