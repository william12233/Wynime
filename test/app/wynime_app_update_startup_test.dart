import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/app/wynime_app.dart';
import 'package:wynime/src/application/updates/software_update_controller.dart';
import 'package:wynime/src/domain/models/software_update_models.dart';

void main() {
  testWidgets('startup checks once without blocking onReady or downloading', (
    tester,
  ) async {
    final networkGate = Completer<SoftwareUpdateResult>();
    final service = _StartupUpdateService(networkGate.future);
    final controller = SoftwareUpdateController(
      service: service,
      installer: _StartupUpdateInstaller(),
    );
    var readyCalls = 0;

    await tester.pumpWidget(
      WynimeApp(
        locale: const Locale('en'),
        softwareUpdates: controller,
        onReady: () async => readyCalls++,
      ),
    );
    await tester.pump();

    expect(service.currentVersionCalls, 1);
    expect(service.checkCalls, 1);
    expect(service.downloadCalls, 0);
    expect(controller.currentVersion?.version, '1.0.1');
    expect(readyCalls, 1);
    expect(controller.status, UpdateStatus.checking);

    networkGate.complete(
      SoftwareUpdateResult(
        status: UpdateStatus.upToDate,
        current: _startupVersion(),
      ),
    );
    await tester.pump();
    await tester.pumpWidget(const SizedBox());
  });
}

AppVersionInfo _startupVersion() => const AppVersionInfo(
  version: '1.0.1',
  buildNumber: '9',
  platform: SoftwareUpdatePlatform.windows,
  architecture: 'x64',
);

final class _StartupUpdateService implements SoftwareUpdateServicePort {
  _StartupUpdateService(this.checkResult);

  final Future<SoftwareUpdateResult> checkResult;
  int currentVersionCalls = 0;
  int checkCalls = 0;
  int downloadCalls = 0;

  @override
  Future<AppVersionInfo> currentVersion() async {
    currentVersionCalls++;
    return _startupVersion();
  }

  @override
  Future<SoftwareUpdateResult> checkForUpdates() {
    checkCalls++;
    return checkResult;
  }

  @override
  Future<DownloadedUpdate> download(
    SoftwareUpdateResult result, {
    UpdateProgressCallback? onProgress,
  }) async {
    downloadCalls++;
    throw StateError('startup check must not download');
  }

  @override
  void dispose() {}
}

final class _StartupUpdateInstaller implements SoftwareUpdateInstallerPort {
  @override
  Future<SoftwareInstallResult> install(DownloadedUpdate update) async {
    throw StateError('startup check must not install');
  }
}
