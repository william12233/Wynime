import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/watch_progress.dart';
import 'package:wynime/src/infrastructure/repositories/drift_watch_history_repository.dart';

import '../helpers/test_database.dart';

void main() {
  test('watch progress persists exact source line episode and resume data', () async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final repository = DriftWatchHistoryRepository(database);
    final progress = WatchProgress(
      progressId: 'progress-1',
      sourceId: 'source-a',
      lineId: 'line-b',
      subjectId: 'subject-c',
      episodeId: 'episode-12',
      position: const Duration(minutes: 8, seconds: 4),
      duration: const Duration(minutes: 24),
      isCompleted: false,
      playerBackendId: 'media3',
      timelineMapId: 'timeline-1',
      updatedAt: DateTime.utc(2026, 8, 2),
    );

    await repository.save(progress);
    final loaded = await repository.findById(progress.progressId);

    expect(loaded, isNotNull);
    expect(loaded!.sourceId, progress.sourceId);
    expect(loaded.lineId, progress.lineId);
    expect(loaded.position, progress.position);
    expect(loaded.timelineMapId, progress.timelineMapId);
  });
}
