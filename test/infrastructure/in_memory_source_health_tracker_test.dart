import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_health_reporter.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/infrastructure/source_health/in_memory_source_health_tracker.dart';

void main() {
  test('health tracker keeps only safe per-source aggregate state', () {
    final tracker = InMemorySourceHealthTracker();
    final reporter = SourceHealthReporter(tracker: tracker);
    final available = _runtime(SourceRuntimeStatus.available);
    final failed = _runtime(
      SourceRuntimeStatus.failed,
      diagnosticCode: 'network_error',
    );

    reporter.report(sourceId: 'xifan', result: available);
    expect(tracker.snapshot('xifan').state.name, 'healthy');
    reporter.report(sourceId: 'xifan', result: failed);
    final degraded = tracker.snapshot('xifan');
    expect(degraded.state.name, 'degraded');
    expect(degraded.lastFailureCode, 'network_error');
    expect(degraded.toString(), isNot(contains('token')));

    reporter.report(
      sourceId: 'xifan',
      result: _runtime(
        SourceRuntimeStatus.available,
        diagnosticCode: 'challenge_required',
      ),
    );
    expect(tracker.snapshot('xifan').challengeCount, 1);
    tracker.clearSource('xifan');
    expect(tracker.snapshot('xifan').state.name, 'unknown');
  });
}

SourceRuntimeResult _runtime(
  SourceRuntimeStatus status, {
  String? diagnosticCode,
}) {
  return SourceRuntimeResult(
    packageId: 'xifan',
    packageVersion: Version.parse('1.0.0'),
    programId: 'program',
    status: status,
    records: const [],
    diagnostics: diagnosticCode == null
        ? const []
        : [
            SourceRuntimeDiagnostic(
              code: diagnosticCode,
              message: 'safe diagnostic',
            ),
          ],
    consumedSteps: 0,
    selectorMatches: 0,
  );
}
