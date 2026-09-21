import '../../domain/models/source_health_models.dart';
import '../../domain/services/source_health_tracker.dart';

final class InMemorySourceHealthTracker implements SourceHealthTracker {
  final _states = <String, _MutableHealth>{};

  @override
  SourceHealthSnapshot snapshot(String sourceId) {
    final state = _states[sourceId];
    if (state == null) {
      return SourceHealthSnapshot(
        sourceId: sourceId,
        state: SourceHealthState.unknown,
        successCount: 0,
        failureCount: 0,
        challengeCount: 0,
      );
    }
    return state.snapshot(sourceId);
  }

  @override
  void recordSuccess(String sourceId) {
    final state = _state(sourceId);
    state.successCount++;
    state.updatedAt = DateTime.now().toUtc();
    state.lastFailureCode = null;
    state.state = SourceHealthState.healthy;
  }

  @override
  void recordFailure(String sourceId, String code) {
    final state = _state(sourceId);
    state.failureCount++;
    state.updatedAt = DateTime.now().toUtc();
    state.lastFailureCode = _safeCode(code);
    state.state = SourceHealthState.degraded;
  }

  @override
  void recordChallenge(String sourceId) {
    final state = _state(sourceId);
    state.challengeCount++;
    state.updatedAt = DateTime.now().toUtc();
    state.state = SourceHealthState.degraded;
  }

  @override
  void clearSource(String sourceId) => _states.remove(sourceId);

  _MutableHealth _state(String sourceId) =>
      _states.putIfAbsent(sourceId, _MutableHealth.new);

  static String _safeCode(String code) {
    final normalized = code.trim();
    if (RegExp(r'^[a-z][a-z0-9_-]{0,63}$').hasMatch(normalized)) {
      return normalized;
    }
    return 'source_failure';
  }
}

final class _MutableHealth {
  SourceHealthState state = SourceHealthState.unknown;
  int successCount = 0;
  int failureCount = 0;
  int challengeCount = 0;
  String? lastFailureCode;
  DateTime? updatedAt;

  SourceHealthSnapshot snapshot(String sourceId) => SourceHealthSnapshot(
    sourceId: sourceId,
    state: state,
    successCount: successCount,
    failureCount: failureCount,
    challengeCount: challengeCount,
    lastFailureCode: lastFailureCode,
    updatedAt: updatedAt,
  );
}
