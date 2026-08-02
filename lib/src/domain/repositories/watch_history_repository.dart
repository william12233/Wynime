import '../models/watch_progress.dart';

abstract interface class WatchHistoryRepository {
  Future<void> save(WatchProgress progress);

  Future<WatchProgress?> findById(String progressId);

  Stream<List<WatchProgress>> watchRecent({int limit = 50});

  Future<void> remove(String progressId);
}
