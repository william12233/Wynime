import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_installed_live_subject_pipeline.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_operation_plan_factory.dart';
import 'package:wynime/src/application/source_live_subject_coordinator.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_subject_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';

void main() {
  test(
    'xifan subject pipeline uses one GET for metadata and episode programs',
    () async {
      final package = const SourcePackageDecoder().decode(
        File('sources/xifan.wynsrc.json').readAsStringSync(),
      );
      final manager = DeclarativeSourcePackageManager(
        wynimeVersion: Version.parse('1.0.15'),
      );
      final pending = manager.install(package);
      final installed = manager.enable(
        packageId: pending.package.packageId,
        version: pending.package.version,
        userApproved: true,
        reconsentGranted: false,
      );
      final transport = _RecordingTransport(
        body: File(
          'test/fixtures/source_packages/xifan/detail_3541.html',
        ).readAsStringSync(),
      );
      final runtime = SourceLiveHttpPackageRuntime(
        httpExecutor: SourceLiveHttpRequestExecutor(
          requestCoordinator: SourceLiveHttpRequestCoordinator(
            wynimeVersion: Version.parse('1.0.15'),
          ),
          transport: transport,
        ),
        fixtureRuntime: DeclarativeSourcePackageRuntime(
          wynimeVersion: Version.parse('1.0.15'),
        ),
      );
      final pipeline = SourceInstalledLiveSubjectPipeline(
        planFactory: SourceLiveOperationPlanFactory(
          wynimeVersion: Version.parse('1.0.15'),
        ),
        subjectCoordinator: SourceLiveSubjectCoordinator(
          runtime: runtime,
          normalizer: const DeclarativeSourceSubjectNormalizer(),
        ),
      );

      final result = await pipeline.listSubjects(
        targets: [
          SourceInstalledLiveSubjectTarget(
            installedPackage: installed,
            subject: SourceSubjectIdentity(sourceId: 'xifan', subjectId: '633'),
          ),
        ],
      );

      expect(result.status, SourceInstalledLiveSubjectPipelineStatus.available);
      expect(result.subjectResults, hasLength(1));
      final details = result.subjectResults.single.details!;
      expect(details.title, contains('無職轉生'));
      expect(details.lines.single.episodes, hasLength(13));
      expect(transport.requests, hasLength(1));
      expect(
        transport.requests.single.uri,
        Uri.parse('https://next.xifanacg.com/anime/633'),
      );
    },
  );

  test('disabled xifan subject target never reaches live transport', () async {
    final package = const SourcePackageDecoder().decode(
      File('sources/xifan.wynsrc.json').readAsStringSync(),
    );
    final transport = _RecordingTransport(body: '<html></html>');
    final runtime = SourceLiveHttpPackageRuntime(
      httpExecutor: SourceLiveHttpRequestExecutor(
        requestCoordinator: SourceLiveHttpRequestCoordinator(
          wynimeVersion: Version.parse('1.0.15'),
        ),
        transport: transport,
      ),
      fixtureRuntime: DeclarativeSourcePackageRuntime(
        wynimeVersion: Version.parse('1.0.15'),
      ),
    );
    final pipeline = SourceInstalledLiveSubjectPipeline(
      planFactory: SourceLiveOperationPlanFactory(
        wynimeVersion: Version.parse('1.0.15'),
      ),
      subjectCoordinator: SourceLiveSubjectCoordinator(
        runtime: runtime,
        normalizer: const DeclarativeSourceSubjectNormalizer(),
      ),
    );
    final installed = InstalledSourcePackage(
      package: package,
      status: SourcePackageStatus.disabled,
      requiresConsent: false,
      requiresReconsent: false,
    );

    final result = await pipeline.listSubjects(
      targets: [
        SourceInstalledLiveSubjectTarget(
          installedPackage: installed,
          subject: SourceSubjectIdentity(sourceId: 'xifan', subjectId: '633'),
        ),
      ],
    );

    expect(
      result.status,
      SourceInstalledLiveSubjectPipelineStatus.noUsableSources,
    );
    expect(
      result.targetResults.single.planResult.reasonCode,
      'package_disabled',
    );
    expect(transport.requests, isEmpty);
  });

  test(
    'subject document fallback evaluates metadata and episodes once',
    () async {
      final package = const SourcePackageDecoder().decode(
        File('sources/xifan.wynsrc.json').readAsStringSync(),
      );
      final manager = DeclarativeSourcePackageManager(
        wynimeVersion: Version.parse('1.0.15'),
      );
      final pending = manager.install(package);
      final installed = manager.enable(
        packageId: pending.package.packageId,
        version: pending.package.version,
        userApproved: true,
        reconsentGranted: false,
      );
      final transport = _RecordingTransport(body: '<main></main>');
      final fallback = _RecordingSubjectFallback(installed: installed);
      final runtime = SourceLiveHttpPackageRuntime(
        httpExecutor: SourceLiveHttpRequestExecutor(
          requestCoordinator: SourceLiveHttpRequestCoordinator(
            wynimeVersion: Version.parse('1.0.15'),
          ),
          transport: transport,
        ),
        fixtureRuntime: DeclarativeSourcePackageRuntime(
          wynimeVersion: Version.parse('1.0.15'),
        ),
      );
      final pipeline = SourceInstalledLiveSubjectPipeline(
        planFactory: SourceLiveOperationPlanFactory(
          wynimeVersion: Version.parse('1.0.15'),
        ),
        subjectCoordinator: SourceLiveSubjectCoordinator(
          runtime: runtime,
          normalizer: const DeclarativeSourceSubjectNormalizer(),
          documentFallback: fallback,
        ),
      );

      final result = await pipeline.listSubjects(
        targets: [
          SourceInstalledLiveSubjectTarget(
            installedPackage: installed,
            subject: SourceSubjectIdentity(
              sourceId: 'xifan',
              subjectId: '3408',
            ),
          ),
        ],
      );

      expect(result.status, SourceInstalledLiveSubjectPipelineStatus.available);
      expect(result.subjectResults.single.details!.title, 'Hydrated title');
      expect(
        result.subjectResults.single.details!.lines.single.episodes,
        hasLength(1),
      );
      expect(fallback.plans, hasLength(1));
      expect(transport.requests, hasLength(1));
    },
  );
}

final class _RecordingTransport implements SourceHttpTransport {
  _RecordingTransport({required this.body});

  final String body;
  final requests = <SourceHttpRequest>[];

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    requests.add(request);
    return SourceHttpTransportResult(
      status: SourceHttpTransportStatus.success,
      response: SourceHttpResponse(
        statusCode: 200,
        finalUri: request.uri,
        redirectChain: const [],
        body: body,
      ),
    );
  }

  @override
  Future<void> close() async {}
}

final class _RecordingSubjectFallback
    implements SourceLiveSubjectDocumentFallback {
  _RecordingSubjectFallback({required this.installed});

  final InstalledSourcePackage installed;
  final plans = <SourceLiveSubjectPlan>[];

  @override
  Future<SourceLiveSubjectDocumentFallbackResult> capture(
    SourceLiveSubjectPlan plan,
  ) async {
    plans.add(plan);
    return SourceLiveSubjectDocumentFallbackResult(
      metadata: SourceRuntimeResult(
        packageId: installed.package.packageId,
        packageVersion: installed.package.version,
        programId: 'subject_metadata',
        status: SourceRuntimeStatus.available,
        records: [
          SourceRuntimeRecord({'title': 'Hydrated title'}),
        ],
        diagnostics: const [],
        consumedSteps: 1,
        selectorMatches: 1,
      ),
      episodes: SourceRuntimeResult(
        packageId: installed.package.packageId,
        packageVersion: installed.package.version,
        programId: 'episode_links',
        status: SourceRuntimeStatus.available,
        records: [
          SourceRuntimeRecord({
            'lineId': 'xfxf1',
            'subjectId': '3408',
            'episodeId': '154427',
            'episodeTitle': '第 1 集',
          }),
        ],
        diagnostics: const [],
        consumedSteps: 1,
        selectorMatches: 1,
      ),
    );
  }
}
