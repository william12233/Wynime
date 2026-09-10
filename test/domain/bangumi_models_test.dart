import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/models/software_update_models.dart';

void main() {
  test('maps the five official collection states without reordering them', () {
    expect(
      BangumiCollectionStatus.fromApiType(1),
      BangumiCollectionStatus.wish,
    );
    expect(
      BangumiCollectionStatus.fromApiType(2),
      BangumiCollectionStatus.completed,
    );
    expect(
      BangumiCollectionStatus.fromApiType(3),
      BangumiCollectionStatus.watching,
    );
    expect(
      BangumiCollectionStatus.fromApiType(4),
      BangumiCollectionStatus.onHold,
    );
    expect(
      BangumiCollectionStatus.fromApiType(5),
      BangumiCollectionStatus.dropped,
    );
    expect(
      () => BangumiCollectionStatus.fromApiType(6),
      throwsA(isA<BangumiPayloadException>()),
    );
  });

  test(
    'remote revision fingerprint is deterministic and order independent',
    () {
      final first = BangumiRemoteState.fingerprint(
        subjectId: '42',
        status: BangumiCollectionStatus.watching,
        watchedEpisodeIds: const ['ep-2', 'ep-1'],
      );
      final second = BangumiRemoteState.fingerprint(
        subjectId: '42',
        status: BangumiCollectionStatus.watching,
        watchedEpisodeIds: const ['ep-1', 'ep-2'],
      );
      final changed = BangumiRemoteState.fingerprint(
        subjectId: '42',
        status: BangumiCollectionStatus.completed,
        watchedEpisodeIds: const ['ep-1', 'ep-2'],
      );

      expect(first, second);
      expect(first, isNot(changed));
      expect(first, hasLength(64));
    },
  );

  test('auth session diagnostics never expose tokens', () {
    final session = BangumiAuthSession(
      accountId: 'account-1',
      accessToken: 'access-secret',
      refreshToken: 'refresh-secret',
      expiresAt: DateTime.utc(2030),
    );

    expect(session.toString(), contains('account-1'));
    expect(session.toString(), contains('redacted'));
    expect(session.toString(), isNot(contains('access-secret')));
    expect(session.toString(), isNot(contains('refresh-secret')));
  });

  test('detail snapshot carries typed metadata and related people', () {
    final snapshot = BangumiSubjectDetailSnapshot(
      subject: const BangumiSubject(
        id: '42',
        name: 'Title',
        nameCn: '作品',
        summary: 'Summary',
        eps: 12,
        totalEpisodes: 12,
        rating: BangumiRating(total: 100, score: 8.5, count: {10: 20}),
        tags: [BangumiTag(name: 'Action', count: 4)],
      ),
      episodes: const BangumiEpisodePage(
        episodes: <BangumiEpisode>[],
        offset: 0,
        limit: 100,
        total: 12,
      ),
      characters: const [
        BangumiCharacter(
          id: 'c1',
          name: 'Character',
          actors: [BangumiActor(id: 'p1', name: 'Actor')],
        ),
      ],
      persons: const [
        BangumiPersonCredit(id: 'p2', name: 'Director', career: ['Producer']),
      ],
      relations: const [
        BangumiSubjectRelation(
          id: '43',
          type: 2,
          name: 'Related',
          nameCn: '相關作品',
        ),
      ],
      collectionStatus: BangumiCollectionStatus.watching,
      epStatus: 3,
      watchedEpisodeIds: const {'ep-1', 'ep-2', 'ep-3'},
    );

    expect(snapshot.subject.rating?.score, 8.5);
    expect(snapshot.subject.tags.single.name, 'Action');
    expect(snapshot.characters.single.actors.single.name, 'Actor');
    expect(snapshot.persons.single.career, ['Producer']);
    expect(snapshot.relations.single.nameCn, '相關作品');
    expect(snapshot.epStatus, 3);
  });

  test('semantic versions compare numerically and accept release tags', () {
    expect(
      SemanticVersion.parse(
        'v1.10.0',
      ).compareTo(SemanticVersion.parse('1.9.9')),
      greaterThan(0),
    );
    expect(SemanticVersion.parse('1.0.1').toString(), '1.0.1');
    expect(() => SemanticVersion.parse('1.0'), throwsFormatException);
  });
}
