import '../domain/models/source_runtime_models.dart';
import '../domain/services/source_health_tracker.dart';

final class SourceHealthReporter {
  const SourceHealthReporter({required this.tracker});

  final SourceHealthTracker tracker;

  void report({required String sourceId, required SourceRuntimeResult result}) {
    switch (result.status) {
      case SourceRuntimeStatus.available:
      case SourceRuntimeStatus.notFound:
        if (result.diagnostics.any(
          (diagnostic) => diagnostic.code == 'challenge_required',
        )) {
          tracker.recordChallenge(sourceId);
        } else {
          tracker.recordSuccess(sourceId);
        }
      case SourceRuntimeStatus.disabled:
      case SourceRuntimeStatus.consentRequired:
      case SourceRuntimeStatus.incompatible:
        return;
      case SourceRuntimeStatus.failed:
        final diagnostic = result.diagnostics.isEmpty
            ? null
            : result.diagnostics.first;
        tracker.recordFailure(sourceId, diagnostic?.code ?? 'source_failure');
    }
  }
}
