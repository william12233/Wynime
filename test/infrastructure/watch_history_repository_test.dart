import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/watch_progress.dart';
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

  test('the exact source-line-episode identity remains a single record', () async {
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
  });
}
