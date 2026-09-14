import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/app/wynime_app.dart';
import 'package:wynime/src/application/bangumi_session_controller.dart';
import 'package:wynime/src/application/updates/software_update_controller.dart';
import 'package:wynime/src/domain/models/bangumi_models.dart';
import 'package:wynime/src/domain/models/software_update_models.dart';
import 'package:wynime/src/domain/models/source_identity.dart';
import 'package:wynime/src/domain/models/watch_progress.dart';
import 'package:wynime/src/domain/repositories/watch_history_repository.dart';
import 'package:wynime/src/infrastructure/bangumi/bangumi_authentication.dart';
import 'package:wynime/src/infrastructure/repositories/drift_bangumi_local_store.dart';
import 'package:wynime/src/presentation/pages/subject_detail_page.dart';

import '../helpers/test_database.dart';

void main() {
  Future<void> pumpApp(
    WidgetTester tester, {
    Size size = const Size(360, 800),
    BangumiSessionController? bangumi,
    SoftwareUpdateController? softwareUpdates,
    WatchHistoryRepository? watchHistory,
  }) async {
    await tester.binding.setSurfaceSize(size);
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      WynimeApp(
        locale: const Locale('en'),
        bangumi: bangumi,
        softwareUpdates: softwareUpdates,
        watchHistory: watchHistory,
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('search keeps a submitted query local without active sources', (
    tester,
  ) async {
    await pumpApp(tester);

    await tester.tap(find.byIcon(Icons.search_outlined));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(EditableText), 'example title');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(find.text('No active sources'), findsOneWidget);
    expect(
      find.text(
        'The query stays local. Install and explicitly enable a reviewed source package before searching.',
      ),
      findsOneWidget,
    );
  });

  testWidgets(
    'EPROG-R11/R12 and matrix 29/30/31 show only meaningful local progress',
    (tester) async {
      final repository = _MemoryWatchHistoryRepository([
        WatchProgress(
          progressId: 'continue-1',
          sourceId: 'source',
          lineId: 'line',
          subjectId: 'subject',
          episodeId: 'episode-5',
          position: const Duration(minutes: 8),
          duration: const Duration(minutes: 24),
          isCompleted: false,
          updatedAt: DateTime.utc(2026, 9, 12),
        ),
        WatchProgress(
          progressId: 'completed-1',
          sourceId: 'source',
          lineId: 'line',
          subjectId: 'subject',
          episodeId: 'episode-6',
          position: const Duration(minutes: 24),
          duration: const Duration(minutes: 24),
          isCompleted: true,
          updatedAt: DateTime.utc(2026, 9, 12, 1),
        ),
      ]);
      await pumpApp(tester, watchHistory: repository);

      expect(
        find.byKey(const ValueKey('continue-watching-continue-1')),
        findsOneWidget,
      );
      expect(find.text('Episode episode-5'), findsOneWidget);
      expect(find.text('33% watched · Resume at 8:00'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('continue-watching-completed-1')),
        findsNothing,
      );
    },
  );

  testWidgets('library filter controls are available on compact layout', (
    tester,
  ) async {
    await pumpApp(tester);

    await tester.tap(
      find.descendant(
        of: find.byType(NavigationBar),
        matching: find.byIcon(Icons.video_library_outlined),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(SegmentedButton), findsNothing);
    for (final name in ['dropped', 'wish', 'watching', 'onHold', 'completed']) {
      expect(find.byKey(ValueKey('library-status-tab-$name')), findsOneWidget);
    }

    final watchingTab = find.byKey(
      const ValueKey('library-status-tab-watching'),
    );
    await tester.ensureVisible(watchingTab);
    await tester.tap(watchingTab);
    await tester.pumpAndSettle();
    expect(find.text('Your library is empty'), findsOneWidget);
  });

  testWidgets('library renders poster artwork for every collection entry', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final database = openTestDatabase();
    addTearDown(database.close);
    final controller = BangumiSessionController(
      authentication: const UnavailableBangumiAuthentication(),
      store: DriftBangumiLocalStore(database),
      clientFactory: (_) => throw StateError('client must not be created'),
    );

    await tester.pumpWidget(
      WynimeApp(
        locale: const Locale('en'),
        bangumi: controller,
        onReady: () async {
          controller.collections = [
            BangumiCollectionEntry(
              subjectId: '42',
              status: BangumiCollectionStatus.watching,
              nameCn: '作品',
              imageUrl: Uri.parse('https://lain.bgm.tv/pic/cover/c/42.jpg'),
              totalEpisodes: 12,
              epStatus: 3,
            ),
            BangumiCollectionEntry(
              subjectId: '43',
              status: BangumiCollectionStatus.completed,
              nameCn: '沒有封面的作品',
              totalEpisodes: 12,
              epStatus: 10,
            ),
            BangumiCollectionEntry(
              subjectId: '44',
              status: BangumiCollectionStatus.completed,
              nameCn: '進度未知的作品',
            ),
          ];
          controller.notifyListeners();
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(
      find.descendant(
        of: find.byType(NavigationBar),
        matching: find.byIcon(Icons.video_library_outlined),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('bangumi-collection-artwork-42')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('bangumi-collection-artwork-43')),
      findsNothing,
    );

    expect(find.text('Watched 3 · 12 episodes'), findsOneWidget);

    final completedTab = find.byKey(
      const ValueKey('library-status-tab-completed'),
    );
    await tester.ensureVisible(completedTab);
    await tester.tap(completedTab);
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('bangumi-collection-artwork-43')),
      findsOneWidget,
    );
    expect(find.text('Watched 10 · 12 episodes'), findsOneWidget);
    expect(find.text('Watch progress not synced'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('bangumi-collection-43')));
    await tester.pumpAndSettle();
    expect(find.byType(BangumiSubjectDetailPage), findsOneWidget);
  });

  testWidgets('privacy diagnostics remain off until explicitly enabled', (
    tester,
  ) async {
    await pumpApp(tester, size: const Size(1024, 768));

    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    final before = tester.widget<SwitchListTile>(find.byType(SwitchListTile));
    expect(before.value, isFalse);

    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    final after = tester.widget<SwitchListTile>(find.byType(SwitchListTile));
    expect(after.value, isTrue);
  });

  testWidgets('unconfigured Bangumi does not show a login action', (
    tester,
  ) async {
    final database = openTestDatabase();
    addTearDown(database.close);
    final controller = BangumiSessionController(
      authentication: const UnavailableBangumiAuthentication(),
      store: DriftBangumiLocalStore(database),
      availability: BangumiAvailability.unavailable,
      clientFactory: (_) => throw StateError('client must not be created'),
    );
    await pumpApp(tester, size: const Size(1024, 768), bangumi: controller);

    expect(find.text('Bangumi is not enabled'), findsOneWidget);
    expect(find.byKey(const ValueKey('bangumi-login')), findsNothing);

    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    expect(find.text('Bangumi is not enabled'), findsOneWidget);
    expect(find.byKey(const ValueKey('bangumi-settings-login')), findsNothing);
  });

  testWidgets('software update install shows real modal download progress', (
    tester,
  ) async {
    final downloadGate = Completer<DownloadedUpdate>();
    final service = _WidgetUpdateService(downloadGate: downloadGate);
    final installGate = Completer<SoftwareInstallResult>();
    final installer = _WidgetUpdateInstaller(gate: installGate);
    final controller = SoftwareUpdateController(
      service: service,
      installer: installer,
    );
    await pumpApp(
      tester,
      size: const Size(1024, 768),
      softwareUpdates: controller,
    );

    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    final installButton = find.byKey(const ValueKey('software-update-now'));
    await tester.ensureVisible(installButton);
    await tester.tap(installButton);
    await tester.pump();

    expect(
      find.byKey(const ValueKey('software-update-progress-dialog')),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('software-update-progress-dialog')),
        matching: find.text('Downloading update…'),
      ),
      findsOneWidget,
    );
    expect(service.downloadCalls, 1);

    service.emitProgress(25, 100);
    await tester.pump();
    expect(find.text('Downloaded 25%'), findsOneWidget);
    expect(
      tester
          .widget<LinearProgressIndicator>(
            find.byKey(const ValueKey('software-update-progress')),
          )
          .value,
      0.25,
    );

    service.emitProgress(75, 100);
    await tester.pump();
    expect(find.text('Downloaded 75%'), findsOneWidget);
    expect(
      tester
          .widget<LinearProgressIndicator>(
            find.byKey(const ValueKey('software-update-progress')),
          )
          .value,
      0.75,
    );

    controller.status = UpdateStatus.verifying;
    controller.notifyListeners();
    await tester.pump();
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('software-update-progress-dialog')),
        matching: find.text('Verifying update…'),
      ),
      findsOneWidget,
    );
    expect(
      tester
          .widget<LinearProgressIndicator>(
            find.byKey(const ValueKey('software-update-progress')),
          )
          .value,
      isNull,
    );
    expect(find.text('Downloaded 100%'), findsNothing);

    controller.status = UpdateStatus.handingOff;
    controller.notifyListeners();
    await tester.pump();
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('software-update-progress-dialog')),
        matching: find.text('Preparing to install…'),
      ),
      findsOneWidget,
    );
    expect(find.text('Installed'), findsNothing);

    await tester.tapAt(Offset.zero);
    await tester.pump();
    expect(
      find.byKey(const ValueKey('software-update-progress-dialog')),
      findsOneWidget,
    );
    await tester.binding.handlePopRoute();
    await tester.pump();
    expect(
      find.byKey(const ValueKey('software-update-progress-dialog')),
      findsOneWidget,
    );

    downloadGate.complete(_widgetDownloadedUpdate(_widgetUpdateResult()));
    await tester.pump();
    expect(
      find.byKey(const ValueKey('software-update-progress-dialog')),
      findsOneWidget,
    );
    installGate.complete(const SoftwareInstallResult(started: true));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('software-update-progress-dialog')),
      findsNothing,
    );
  });

  testWidgets('failed install closes modal and keeps diagnostic state', (
    tester,
  ) async {
    final service = _WidgetUpdateService(
      failure: const SoftwareUpdateException(
        UpdateFailureReason.download,
        'download_failed',
      ),
    );
    final controller = SoftwareUpdateController(
      service: service,
      installer: _WidgetUpdateInstaller(),
    );
    await pumpApp(
      tester,
      size: const Size(1024, 768),
      softwareUpdates: controller,
    );

    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    final installButton = find.byKey(const ValueKey('software-update-now'));
    await tester.ensureVisible(installButton);
    await tester.tap(installButton);
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('software-update-progress-dialog')),
      findsNothing,
    );
    expect(find.text('Update failed'), findsOneWidget);
    expect(find.text('Update error: download_failed'), findsOneWidget);
  });

  testWidgets('manual-required install closes modal without fake success', (
    tester,
  ) async {
    final controller = SoftwareUpdateController(
      service: _WidgetUpdateService(),
      installer: _WidgetUpdateInstaller(
        result: const SoftwareInstallResult(
          started: false,
          requiresUserAction: true,
        ),
      ),
    );
    await pumpApp(
      tester,
      size: const Size(1024, 768),
      softwareUpdates: controller,
    );

    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    final installButton = find.byKey(const ValueKey('software-update-now'));
    await tester.ensureVisible(installButton);
    await tester.tap(installButton);
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('software-update-progress-dialog')),
      findsNothing,
    );
    expect(find.text('Manual update required'), findsOneWidget);
    expect(find.text('Installed'), findsNothing);
  });
}

final class _MemoryWatchHistoryRepository implements WatchHistoryRepository {
  _MemoryWatchHistoryRepository(Iterable<WatchProgress> initial)
    : _rows = [...initial];

  final List<WatchProgress> _rows;

  @override
  Future<void> save(WatchProgress progress) async {
    _rows.removeWhere((row) => row.progressId == progress.progressId);
    _rows.add(progress);
  }

  @override
  Future<WatchProgress?> findById(String progressId) async {
    for (final row in _rows) {
      if (row.progressId == progressId) return row;
    }
    return null;
  }

  @override
  Future<WatchProgress?> findByIdentity(SourceEpisodeIdentity identity) async {
    for (final row in _rows) {
      if (row.sourceId == identity.sourceId &&
          row.lineId == identity.lineId &&
          row.subjectId == identity.subjectId &&
          row.episodeId == identity.episodeId) {
        return row;
      }
    }
    return null;
  }

  @override
  Stream<List<WatchProgress>> watchRecent({int limit = 50}) =>
      Stream.value(_rows.take(limit).toList(growable: false));

  @override
  Future<void> remove(String progressId) async {
    _rows.removeWhere((row) => row.progressId == progressId);
  }
}

SoftwareUpdateResult _widgetUpdateResult() => SoftwareUpdateResult(
  status: UpdateStatus.updateAvailable,
  current: const AppVersionInfo(
    version: '1.0.1',
    buildNumber: '9',
    platform: SoftwareUpdatePlatform.windows,
    architecture: 'x64',
  ),
  release: SoftwareRelease(
    tagName: 'v1.0.2',
    version: SemanticVersion.parse('1.0.2'),
    assets: const <ReleaseAsset>[],
  ),
  asset: const ReleaseAsset(
    name: 'wynime-1.0.2.zip',
    downloadUrl: null,
    size: 1,
  ),
);

DownloadedUpdate _widgetDownloadedUpdate(SoftwareUpdateResult result) =>
    DownloadedUpdate(
      filePath: 'update.zip',
      stagingDirectoryPath: 'staging',
      release: result.release!,
      asset: result.asset!,
    );

final class _WidgetUpdateService implements SoftwareUpdateServicePort {
  _WidgetUpdateService({this.downloadGate, this.failure});

  final Completer<DownloadedUpdate>? downloadGate;
  final SoftwareUpdateException? failure;
  UpdateProgressCallback? _onProgress;
  int downloadCalls = 0;

  @override
  Future<AppVersionInfo> currentVersion() async => const AppVersionInfo(
    version: '1.0.1',
    buildNumber: '9',
    platform: SoftwareUpdatePlatform.windows,
    architecture: 'x64',
  );

  @override
  Future<SoftwareUpdateResult> checkForUpdates() async => _widgetUpdateResult();

  @override
  Future<DownloadedUpdate> download(
    SoftwareUpdateResult result, {
    UpdateProgressCallback? onProgress,
  }) {
    downloadCalls++;
    _onProgress = onProgress;
    final error = failure;
    if (error != null) return Future<DownloadedUpdate>.error(error);
    final gate = downloadGate;
    return gate?.future ?? Future.value(_widgetDownloadedUpdate(result));
  }

  void emitProgress(int received, int total) =>
      _onProgress?.call(received, total);

  @override
  void dispose() {}
}

final class _WidgetUpdateInstaller implements SoftwareUpdateInstallerPort {
  _WidgetUpdateInstaller({
    this.result = const SoftwareInstallResult(started: true),
    this.gate,
  });

  final SoftwareInstallResult result;
  final Completer<SoftwareInstallResult>? gate;

  @override
  Future<SoftwareInstallResult> install(DownloadedUpdate update) =>
      gate?.future ?? Future.value(result);
}
