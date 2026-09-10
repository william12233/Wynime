import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/bangumi_session_controller.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/services/bangumi_ports.dart';
import 'package:wynime/src/infrastructure/repositories/drift_bangumi_local_store.dart';

import '../helpers/test_database.dart';

void main() {
  test(
    'reauth refresh keeps a local-only queued collection in controller state',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(
        database,
        clock: () => DateTime.utc(2026, 9, 8, 12),
      );
      await store.saveAccount(
        const BangumiUserIdentity(id: '7', username: 'alice'),
      );
      await store.cacheSubject(
        const BangumiSubject(
          id: '42',
          name: 'Title',
          nameCn: '作品',
          summary: '',
          eps: 1,
        ),
      );
      await store.saveCollectionStatus('42', BangumiCollectionStatus.watching);

      final controller = BangumiSessionController(
        authentication: _FakeAuthentication(),
        store: store,
        clientFactory: (_) => _FakeBangumiClient(),
      );
      addTearDown(controller.dispose);

      await controller.completeSignIn(
        const BangumiAuthCallback(state: 'state', ticket: 'ticket'),
      );

      expect(controller.status, BangumiConnectionStatus.connected);
      expect(controller.collections, hasLength(1));
      expect(controller.collections.single.subjectId, '42');
      expect(
        controller.collections.single.status,
        BangumiCollectionStatus.watching,
      );
      expect(controller.pendingCount, 1);
    },
  );

  test('unavailable Bangumi never starts browser authentication', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database);
    final authentication = _CountingAuthentication();
    var openedAuthorizationUri = 0;
    final controller = BangumiSessionController(
      authentication: authentication,
      store: store,
      availability: BangumiAvailability.unavailable,
      clientFactory: (_) => throw StateError('client must not be created'),
      openAuthorizationUri: (uri) async {
        openedAuthorizationUri++;
        return true;
      },
    );
    addTearDown(controller.dispose);

    await controller.initialize();
    await controller.signIn();

    expect(controller.isAvailable, isFalse);
    expect(controller.isAuthenticated, isFalse);
    expect(controller.status, BangumiConnectionStatus.disconnected);
    expect(controller.errorCode, 'bangumi_service_not_configured');
    expect(authentication.beginCalls, 0);
    expect(openedAuthorizationUri, 0);
  });

  test('explicit collection refresh imports external Bangumi state', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database);
    await store.cacheSubject(
      const BangumiSubject(
        id: '42',
        name: 'Title',
        nameCn: '作品',
        summary: '',
        eps: 1,
      ),
    );
    await store.cacheEpisodes(
      const BangumiEpisodePage(
        episodes: [
          BangumiEpisode(
            id: '1001',
            subjectId: '42',
            name: 'Episode 1',
            nameCn: '第一集',
            sort: 1,
            type: 0,
          ),
        ],
        offset: 0,
        limit: 1,
        total: 1,
      ),
    );
    final remote = BangumiRemoteState(
      accountId: '7',
      subjectId: '42',
      status: BangumiCollectionStatus.completed,
      watchedEpisodeIds: const {'1001'},
      remoteRevision: BangumiRemoteState.fingerprint(
        subjectId: '42',
        status: BangumiCollectionStatus.completed,
        watchedEpisodeIds: const ['1001'],
      ),
    );
    final client = _FakeBangumiClient(
      remoteCollections: const [
        BangumiCollectionEntry(
          subjectId: '42',
          status: BangumiCollectionStatus.completed,
        ),
      ],
      remote: remote,
    );
    final controller = BangumiSessionController(
      authentication: _FakeAuthentication(),
      store: store,
      clientFactory: (_) => client,
    );
    addTearDown(controller.dispose);

    await controller.completeSignIn(
      const BangumiAuthCallback(state: 'state', ticket: 'ticket'),
    );

    expect(
      controller.collections.single.status,
      BangumiCollectionStatus.completed,
    );
    expect((await store.loadEpisodeProgress('42'))?.watchedEpisodeIds, {
      '1001',
    });
  });

  test(
    'initialize resumes a callback delivered during process restart',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final store = DriftBangumiLocalStore(database);
      final callbackPort = _PendingCallbackPort(
        const BangumiAuthCallback(state: 'state', ticket: 'ticket'),
      );
      final controller = BangumiSessionController(
        authentication: _FakeAuthentication(),
        store: store,
        clientFactory: (_) => _FakeBangumiClient(),
        callbackPort: callbackPort,
      );
      addTearDown(controller.dispose);

      await controller.initialize();

      expect(controller.status, BangumiConnectionStatus.connected);
      expect(controller.isAuthenticated, isTrue);
      expect(callbackPort.takeCalls, 1);
    },
  );

  test('initialize silently restores a persisted Bangumi session', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final store = DriftBangumiLocalStore(database);
    await store.saveAccount(
      const BangumiUserIdentity(id: '7', username: 'alice'),
    );
    final callbackPort = _PendingCallbackPort(null);
    final authentication = _RestorableAuthentication();
    final controller = BangumiSessionController(
      authentication: authentication,
      store: store,
      clientFactory: (_) => _FakeBangumiClient(),
      callbackPort: callbackPort,
    );
    addTearDown(controller.dispose);

    await controller.initialize();

    expect(controller.status, BangumiConnectionStatus.connected);
    expect(controller.isAuthenticated, isTrue);
    expect(authentication.restoreCalls, 1);
    expect(authentication.refreshCalls, 1);
  });
}

final class _CountingAuthentication implements BangumiAuthenticationPort {
  int beginCalls = 0;

  @override
  Future<BangumiAuthorizationRequest> begin() async {
    beginCalls++;
    throw StateError('begin must not be called');
  }

  @override
  Future<BangumiAuthSession> redeem(BangumiAuthCallback callback) =>
      throw StateError('redeem must not be called');

  @override
  Future<BangumiAuthSession> refresh(BangumiAuthSession session) =>
      throw StateError('refresh must not be called');

  @override
  Future<void> signOut() async {}
}

final class _FakeAuthentication implements BangumiAuthenticationPort {
  static final _session = BangumiAuthSession(
    accountId: '7',
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    expiresAt: DateTime.utc(2030),
  );

  @override
  Future<BangumiAuthorizationRequest> begin() => throw UnimplementedError();

  @override
  Future<BangumiAuthSession> redeem(BangumiAuthCallback callback) async =>
      _session;

  @override
  Future<BangumiAuthSession> refresh(BangumiAuthSession session) async =>
      _session;

  @override
  Future<void> signOut() async {}
}

final class _RestorableAuthentication
    implements BangumiAuthenticationPort, BangumiPersistentAuthenticationPort {
  int restoreCalls = 0;
  int refreshCalls = 0;

  @override
  Future<BangumiAuthorizationRequest> begin() => throw UnimplementedError();

  @override
  Future<BangumiAuthSession> redeem(BangumiAuthCallback callback) =>
      throw UnimplementedError();

  @override
  Future<BangumiAuthSession?> restoreSession() async {
    restoreCalls++;
    return BangumiAuthSession(
      accountId: '7',
      accessToken: '',
      refreshToken: 'persisted-refresh',
      expiresAt: DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
    );
  }

  @override
  Future<BangumiAuthSession> refresh(BangumiAuthSession session) async {
    refreshCalls++;
    return _FakeAuthentication._session;
  }

  @override
  Future<void> clearStoredSession() async {}

  @override
  Future<void> signOut() async {}
}

final class _PendingCallbackPort
    implements BangumiCallbackPort, BangumiPendingCallbackPort {
  _PendingCallbackPort(this.pending);

  final BangumiAuthCallback? pending;
  int takeCalls = 0;

  @override
  Future<Uri> prepareRedirectUri() async => Uri.https(
    'wynime-broker-test.example.workers.dev',
    '/oauth/app-callback',
  );

  @override
  Future<BangumiAuthCallback> waitForCallback() => throw UnimplementedError();

  @override
  Future<BangumiAuthCallback?> takePendingCallback() async {
    takeCalls++;
    return pending;
  }

  @override
  Future<void> close() async {}
}

final class _FakeBangumiClient implements BangumiClient {
  _FakeBangumiClient({
    this.remoteCollections = const <BangumiCollectionEntry>[],
    this.remote,
  });

  final List<BangumiCollectionEntry> remoteCollections;
  final BangumiRemoteState? remote;

  @override
  Future<BangumiUserIdentity> currentUser() async =>
      const BangumiUserIdentity(id: '7', username: 'alice');

  @override
  Future<BangumiCollectionPage> collections({
    int offset = 0,
    int limit = 30,
  }) async => BangumiCollectionPage(
    collections: remoteCollections,
    offset: offset,
    limit: limit,
    total: remoteCollections.length,
  );

  @override
  Future<List<BangumiScheduleEntry>> calendar() async =>
      const <BangumiScheduleEntry>[];

  @override
  Future<BangumiSubject> subject(String id) async => const BangumiSubject(
    id: '42',
    name: 'Title',
    nameCn: '作品',
    summary: '',
    eps: 1,
  );

  @override
  Future<BangumiEpisodePage> episodes(String subjectId) async =>
      const BangumiEpisodePage(
        episodes: <BangumiEpisode>[],
        offset: 0,
        limit: 1,
        total: 0,
      );

  @override
  Future<BangumiRemoteState> remoteState(String subjectId) async {
    final value = remote;
    if (value == null) throw UnimplementedError();
    return value;
  }

  @override
  Future<void> setCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  ) => throw UnimplementedError();

  @override
  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  ) => throw UnimplementedError();
}
