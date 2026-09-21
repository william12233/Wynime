enum SourceHealthState { unknown, healthy, degraded }

final class SourceHealthSnapshot {
  const SourceHealthSnapshot({
    required this.sourceId,
    required this.state,
    required this.successCount,
    required this.failureCount,
    required this.challengeCount,
    this.lastFailureCode,
    this.updatedAt,
  });

  final String sourceId;
  final SourceHealthState state;
  final int successCount;
  final int failureCount;
  final int challengeCount;
  final String? lastFailureCode;
  final DateTime? updatedAt;
}
