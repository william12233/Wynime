import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/l10n/app_localizations.dart';
import 'package:wynime/src/application/bangumi_session_controller.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/services/bangumi_ports.dart';
import 'package:wynime/src/infrastructure/repositories/drift_bangumi_local_store.dart';
import 'package:wynime/src/presentation/pages/subject_detail_page.dart';

import '../helpers/test_database.dart';

void main() {
  testWidgets(
    'subject detail has independent sections and separates selection from watched writes',
    (tester) async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final client = _DetailClient();
      final store = DriftBangumiLocalStore(database);
      final controller = BangumiSessionController(
        authentication: _DetailAuthentication(),
        store: store,
        clientFactory: (_) => client,
      );
      addTearDown(controller.dispose);

      await controller.completeSignIn(
        const BangumiAuthCallback(state: 'state', ticket: 'ticket'),
      );
      await tester.pumpWidget(
        MaterialApp(
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: BangumiSubjectDetailPage(
            controller: controller,
            subjectId: '42',
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Summary'), findsOneWidget);
      expect(find.text('Characters'), findsOneWidget);
      expect(find.text('Staff and creators'), findsOneWidget);
      expect(find.text('Related subjects'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('subject-episode-watched-1001')),
        findsNothing,
      );

      final firstEpisode = find.text('First episode');
      await tester.ensureVisible(firstEpisode);
      await tester.tap(firstEpisode);
      await tester.pump();
      expect(client.watchedWrites, isEmpty);
      final watchedAction = find.byKey(
        const ValueKey('subject-episode-watched-1001'),
      );
      await tester.ensureVisible(watchedAction);
      expect(watchedAction, findsOneWidget);

      await tester.tap(watchedAction);
      await tester.pumpAndSettle();
      expect(controller.pendingCount, 1);
      expect((await store.loadEpisodeProgress('42'))?.watchedEpisodeIds, {
        '1001',
      });
    },
  );

  testWidgets('partial detail state keeps the base subject visible', (
    tester,
  ) async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final client = _DetailClient(failCharacters: true);
    final controller = BangumiSessionController(
      authentication: _DetailAuthentication(),
      store: DriftBangumiLocalStore(database),
      clientFactory: (_) => client,
    );
    addTearDown(controller.dispose);
    await controller.completeSignIn(
      const BangumiAuthCallback(state: 'state', ticket: 'ticket'),
    );

    await tester.pumpWidget(
      MaterialApp(
        locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: BangumiSubjectDetailPage(controller: controller, subjectId: '42'),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      controller.subjectDetailState('42')?.phase,
      BangumiDetailPhase.partial,
    );
    expect(find.text('Title'), findsOneWidget);
    expect(find.textContaining('character_error'), findsOneWidget);
  });
}

final class _DetailAuthentication implements BangumiAuthenticationPort {
  static final session = BangumiAuthSession(
    accountId: '7',
    accessToken: 'access-token',
    refreshToken: 'refresh-token',
    expiresAt: DateTime.utc(2030),
  );

  @override
  Future<BangumiAuthorizationRequest> begin() => throw UnimplementedError();

  @override
  Future<BangumiAuthSession> redeem(BangumiAuthCallback callback) async =>
      session;

  @override
  Future<BangumiAuthSession> refresh(BangumiAuthSession session) async =>
      _DetailAuthentication.session;

  @override
  Future<void> signOut() async {}
}

final class _DetailClient implements BangumiClient {
  _DetailClient({this.failCharacters = false});

  final bool failCharacters;
  final List<(String, String, bool)> watchedWrites = [];

  @override
  Future<BangumiUserIdentity> currentUser() async =>
      const BangumiUserIdentity(id: '7', username: 'alice');

  @override
  Future<BangumiCollectionPage> collections({
    int offset = 0,
    int limit = 30,
  }) async => const BangumiCollectionPage(
    collections: [
      BangumiCollectionEntry(
        subjectId: '42',
        status: BangumiCollectionStatus.watching,
        name: 'Title',
        nameCn: '作品',
        totalEpisodes: 2,
        epStatus: 0,
      ),
    ],
    offset: 0,
    limit: 30,
    total: 1,
  );

  @override
  Future<List<BangumiScheduleEntry>> calendar() async =>
      const <BangumiScheduleEntry>[];

  @override
  Future<BangumiSubject> subject(String id) async => const BangumiSubject(
    id: '42',
    name: 'Title',
    nameCn: '作品',
    summary: 'Summary text',
    eps: 2,
    totalEpisodes: 2,
    rating: BangumiRating(total: 10, score: 8.5, count: {9: 4}),
    tags: [BangumiTag(name: 'Action', count: 2)],
  );

  @override
  Future<BangumiEpisodePage> episodes(String subjectId) async =>
      const BangumiEpisodePage(
        episodes: [
          BangumiEpisode(
            id: '1001',
            subjectId: '42',
            name: 'First episode',
            nameCn: '',
            sort: 1,
            type: 0,
          ),
          BangumiEpisode(
            id: '1002',
            subjectId: '42',
            name: 'Second episode',
            nameCn: '',
            sort: 2,
            type: 0,
          ),
        ],
        offset: 0,
        limit: 100,
        total: 2,
      );

  @override
  Future<List<BangumiCharacter>> characters(String subjectId) async {
    if (failCharacters) {
      throw const BangumiApiException(code: 'character_error');
    }
    return const [
      BangumiCharacter(
        id: 'c1',
        name: 'Character',
        actors: [BangumiActor(id: 'p1', name: 'Actor')],
      ),
    ];
  }

  @override
  Future<List<BangumiPersonCredit>> persons(String subjectId) async => const [
    BangumiPersonCredit(id: 'p2', name: 'Director', career: ['Director']),
  ];

  @override
  Future<List<BangumiSubjectRelation>> relations(String subjectId) async =>
      const [
        BangumiSubjectRelation(
          id: '43',
          type: 2,
          name: 'Related',
          nameCn: 'Related',
        ),
      ];

  @override
  Future<BangumiRemoteState> remoteState(String subjectId) async =>
      BangumiRemoteState(
        accountId: '7',
        subjectId: subjectId,
        status: BangumiCollectionStatus.watching,
        watchedEpisodeIds: const <String>{},
        remoteRevision: BangumiRemoteState.fingerprint(
          subjectId: subjectId,
          status: BangumiCollectionStatus.watching,
          watchedEpisodeIds: const <String>{},
        ),
      );

  @override
  Future<void> setCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  ) async {}

  @override
  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  ) async {
    watchedWrites.add((subjectId, episodeId, watched));
  }
}
