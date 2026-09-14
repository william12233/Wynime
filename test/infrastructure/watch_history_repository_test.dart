import 'package:flutter_test/flutter_test.dart';
import 'package:drift/drift.dart' show Value;
import 'package:wynime/src/domain/models/watch_progress.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/infrastructure/database/wynime_database.dart';
import 'package:wynime/src/infrastructure/repositories/drift_watch_history_repository.dart';

import '../helpers/test_database.dart';

void main() {
  WatchProgress progress({
    required String progressId,
    required Duration position,
    required DateTime updatedAt,
  }) {
    return WatchProgress(
      progressId: progressId,
      sourceId: 'source-a',
      lineId: 'line-b',
      subjectId: 'subject-c',
      episodeId: 'episode-12',
      position: position,
      duration: const Duration(minutes: 24),
      isCompleted: false,
      playerBackendId: 'media3',
      timelineMapId: 'timeline-1',
      updatedAt: updatedAt,
    );
  }

  test(
    'watch progress persists exact source line episode and resume data',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final repository = DriftWatchHistoryRepository(database);
      final value = progress(
        progressId: 'progress-1',
        position: const Duration(minutes: 8, seconds: 4),
        updatedAt: DateTime.utc(2026, 8, 2),
      );

      await repository.save(value);
      final loaded = await repository.findById(value.progressId);

      expect(loaded, isNotNull);
      expect(loaded!.sourceId, value.sourceId);
      expect(loaded.lineId, value.lineId);
      expect(loaded.position, value.position);
      expect(loaded.timelineMapId, value.timelineMapId);
    },
  );

  test(
    'the exact source-line-episode identity remains a single record',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final repository = DriftWatchHistoryRepository(database);
      final first = progress(
        progressId: 'progress-old',
        position: const Duration(minutes: 4),
        updatedAt: DateTime.utc(2026, 8, 2, 1),
      );
      final replacement = progress(
        progressId: 'progress-current',
        position: const Duration(minutes: 12),
        updatedAt: DateTime.utc(2026, 8, 2, 2),
      );

      await repository.save(first);
      await repository.save(replacement);

      expect(await repository.findById(first.progressId), isNull);
      final loaded = await repository.findById(replacement.progressId);
      expect(loaded, isNotNull);
      expect(loaded!.position, replacement.position);
      expect(await repository.watchRecent().first, hasLength(1));
    },
  );

  test(
    'EPROG-R10 and matrix 4/5 sanitize corrupt persisted positions',
    () async {
      final database = openTestDatabase();
      addTearDown(database.close);
      final repository = DriftWatchHistoryRepository(database);
      final updatedAt = DateTime.utc(2026, 9, 12);
      await database
          .into(database.watchHistoryRows)
          .insert(
            WatchHistoryRowsCompanion(
              progressId: const Value('corrupt-negative'),
              sourceId: const Value('source'),
              lineId: const Value('line'),
              subjectId: const Value('subject'),
              episodeId: const Value('negative'),
              positionMs: const Value(-1),
              durationMs: const Value(1000),
              isCompleted: const Value(false),
              updatedAt: Value(updatedAt),
            ),
          );
      await database
          .into(database.watchHistoryRows)
          .insert(
            WatchHistoryRowsCompanion(
              progressId: const Value('corrupt-overflow'),
              sourceId: const Value('source'),
              lineId: const Value('line'),
              subjectId: const Value('subject'),
              episodeId: const Value('overflow'),
              positionMs: const Value(2000),
              durationMs: const Value(1000),
              isCompleted: const Value(false),
              updatedAt: Value(updatedAt),
            ),
          );

      final negative = await repository.findByIdentity(
        SourceEpisodeIdentity(
          sourceId: 'source',
          lineId: 'line',
          subjectId: 'subject',
          episodeId: 'negative',
        ),
      );
      final overflow = await repository.findByIdentity(
        SourceEpisodeIdentity(
          sourceId: 'source',
          lineId: 'line',
          subjectId: 'subject',
          episodeId: 'overflow',
        ),
      );
      expect(negative!.position, Duration.zero);
      expect(overflow!.position, const Duration(seconds: 1));
      expect(negative.duration, const Duration(seconds: 1));
    },
  );
}
