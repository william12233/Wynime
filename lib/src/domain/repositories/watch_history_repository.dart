import '../models/watch_progress.dart';
import '../models/source_identity.dart';

abstract interface class WatchHistoryRepository {
  Future<void> save(WatchProgress progress);

  Future<WatchProgress?> findById(String progressId);

  /// Loads the one local row for the authoritative source episode identity.
  Future<WatchProgress?> findByIdentity(SourceEpisodeIdentity identity);

  Stream<List<WatchProgress>> watchRecent({int limit = 50});

  Future<void> remove(String progressId);
}
