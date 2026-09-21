import '../models/source_health_models.dart';

abstract interface class SourceHealthTracker {
  SourceHealthSnapshot snapshot(String sourceId);

  void recordSuccess(String sourceId);

  void recordFailure(String sourceId, String code);

  void recordChallenge(String sourceId);

  void clearSource(String sourceId);
}
