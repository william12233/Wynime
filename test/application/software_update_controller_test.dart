import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/updates/software_update_controller.dart';
import 'package:wynime/src/domain/models/software_update_models.dart';

void main() {
  test('automatic checking is disabled by default', () async {
    final service = FakeUpdateService();
    final controller = SoftwareUpdateController(
      service: service,
      installer: FakeUpdateInstaller(),
    );
    addTearDown(controller.dispose);

    final result = await controller.check(automatic: true);

    expect(result.status, UpdateStatus.idle);
    expect(service.checkCalls, 0);
  });

  test(
    'manual check, download and installer handoff expose typed state',
    () async {
      final service = FakeUpdateService(
        result: SoftwareUpdateResult(
          status: UpdateStatus.updateAvailable,
          current: _version('1.0.1'),
          release: _release('1.0.2'),
          asset: const ReleaseAsset(
            name: 'wynime-1.0.2.zip',
            downloadUrl: null,
            size: 1,
          ),
        ),
      );
      final installer = FakeUpdateInstaller();
      final controller = SoftwareUpdateController(
        service: service,
        installer: installer,
      );
      addTearDown(controller.dispose);

      await controller.initialize();
      final checked = await controller.check();
      final installed = await controller.install();

      expect(checked.status, UpdateStatus.updateAvailable);
      expect(installed?.started, isTrue);
      expect(service.downloadCalls, 1);
      expect(installer.updates, hasLength(1));
      expect(controller.status, UpdateStatus.idle);
      expect(controller.downloadProgress, 1);
    },
  );

  test('installer permission result becomes manual update required', () async {
    final service = FakeUpdateService(
      result: SoftwareUpdateResult(
        status: UpdateStatus.updateAvailable,
        current: _version('1.0.1'),
        release: _release('1.0.2'),
        asset: const ReleaseAsset(
          name: 'wynime-1.0.2.apk',
          downloadUrl: null,
          size: 1,
        ),
      ),
    );
    final controller = SoftwareUpdateController(
      service: service,
      installer: FakeUpdateInstaller(
        result: const SoftwareInstallResult(
          started: false,
          requiresUserAction: true,
          message: 'install_permission_required',
        ),
      ),
    );
    addTearDown(controller.dispose);

    await controller.check();
    final install = await controller.install();

    expect(install?.requiresUserAction, isTrue);
    expect(controller.status, UpdateStatus.manualUpdateRequired);
    expect(controller.error, isNull);
  });

  test('duplicate checks share one in-flight request', () async {
    final gate = Completer<SoftwareUpdateResult>();
    final service = FakeUpdateService(resultFuture: gate.future);
    final controller = SoftwareUpdateController(
      service: service,
      installer: FakeUpdateInstaller(),
    );
    addTearDown(controller.dispose);

    final first = controller.check();
    final second = controller.check();
    gate.complete(
      SoftwareUpdateResult(
        status: UpdateStatus.upToDate,
        current: _version('1.0.1'),
      ),
    );
    expect((await first).status, UpdateStatus.upToDate);
    expect((await second).status, UpdateStatus.upToDate);
    expect(service.checkCalls, 1);
  });
}

AppVersionInfo _version(String version) => AppVersionInfo(
  version: version,
  buildNumber: '',
  platform: SoftwareUpdatePlatform.windows,
  architecture: 'x64',
);

SoftwareRelease _release(String version) => SoftwareRelease(
  tagName: 'v$version',
  version: SemanticVersion.parse(version),
  assets: const <ReleaseAsset>[],
);

final class FakeUpdateService implements SoftwareUpdateServicePort {
  FakeUpdateService({this.result, this.resultFuture});

  final SoftwareUpdateResult? result;
  final Future<SoftwareUpdateResult>? resultFuture;
  int checkCalls = 0;
  int downloadCalls = 0;

  @override
  Future<AppVersionInfo> currentVersion() async => _version('1.0.1');

  @override
  Future<SoftwareUpdateResult> checkForUpdates() {
    checkCalls++;
    return resultFuture ??
        Future.value(
          result ??
              SoftwareUpdateResult(
                status: UpdateStatus.upToDate,
                current: _version('1.0.1'),
              ),
        );
  }

  @override
  Future<DownloadedUpdate> download(
    SoftwareUpdateResult result, {
    UpdateProgressCallback? onProgress,
  }) async {
    downloadCalls++;
    onProgress?.call(1, 1);
    return DownloadedUpdate(
      filePath: 'update.zip',
      stagingDirectoryPath: 'staging',
      release: result.release!,
      asset: result.asset!,
    );
  }

  @override
  void dispose() {}
}

final class FakeUpdateInstaller implements SoftwareUpdateInstallerPort {
  FakeUpdateInstaller({
    this.result = const SoftwareInstallResult(started: true),
  });

  final SoftwareInstallResult result;
  final List<DownloadedUpdate> updates = <DownloadedUpdate>[];

  @override
  Future<SoftwareInstallResult> install(DownloadedUpdate update) async {
    updates.add(update);
    return result;
  }
}
