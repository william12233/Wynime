import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as path;

import '../../domain/models/software_update_models.dart';
import '../../infrastructure/updates/update_startup_marker.dart';

abstract interface class SoftwareUpdateInstaller
    implements SoftwareUpdateInstallerPort {
  factory SoftwareUpdateInstaller.forCurrentPlatform({
    DatabaseRecoveryPort? databaseRecovery,
    bool exitAfterHandoff = true,
  }) {
    if (Platform.isAndroid) return AndroidSoftwareUpdateInstaller();
    if (Platform.isWindows) {
      return WindowsSoftwareUpdateInstaller(
        databaseRecovery: databaseRecovery,
        exitAfterHandoff: exitAfterHandoff,
      );
    }
    return const UnsupportedSoftwareUpdateInstaller();
  }

  @override
  Future<SoftwareInstallResult> install(DownloadedUpdate update);
}

final class AndroidSoftwareUpdateInstaller implements SoftwareUpdateInstaller {
  AndroidSoftwareUpdateInstaller({MethodChannel? channel})
    : _channel =
          channel ??
          const MethodChannel('io.github.william12233.wynime/software_update');

  final MethodChannel _channel;

  @override
  Future<SoftwareInstallResult> install(DownloadedUpdate update) async {
    final canInstall =
        await _channel.invokeMethod<bool>('canInstallPackages') ?? false;
    if (!canInstall) {
      await _channel.invokeMethod<void>('openInstallPermissionSettings');
      return const SoftwareInstallResult(
        started: false,
        requiresUserAction: true,
        message: 'install_permission_required',
      );
    }
    final started =
        await _channel.invokeMethod<bool>('installApk', <String, Object>{
          'path': update.filePath,
        }) ??
        false;
    return SoftwareInstallResult(
      started: started,
      message: started ? null : 'package_installer_not_started',
    );
  }
}

final class WindowsSoftwareUpdateInstaller implements SoftwareUpdateInstaller {
  WindowsSoftwareUpdateInstaller({
    this.databaseRecovery,
    this.exitAfterHandoff = true,
  });

  final DatabaseRecoveryPort? databaseRecovery;
  final bool exitAfterHandoff;

  @override
  Future<SoftwareInstallResult> install(DownloadedUpdate update) async {
    if (!Platform.isWindows) {
      return const SoftwareInstallResult(
        started: false,
        message: 'windows_installer_unavailable',
      );
    }
    final installRoot = File(Platform.resolvedExecutable).parent;
    final updater = File(path.join(installRoot.path, 'wynime_update.exe'));
    final extractedPath = update.extractedDirectoryPath;
    if (!await updater.exists() || extractedPath == null) {
      return const SoftwareInstallResult(
        started: false,
        requiresUserAction: true,
        message: 'manual_update_required',
      );
    }
    final recovery = databaseRecovery;
    if (recovery == null) {
      // A Windows handoff without a recovery point cannot prove that the
      // database remains recoverable if activation or startup fails.
      return const SoftwareInstallResult(
        started: false,
        requiresUserAction: true,
        message: 'manual_update_required',
      );
    }

    final handoffStage = Directory(
      path.join(
        installRoot.parent.path,
        '.wynime-update-${DateTime.now().microsecondsSinceEpoch}-$pid',
      ),
    );
    final helperDirectory = Directory(
      path.join(
        Directory.systemTemp.path,
        'wynime-update-helper-${DateTime.now().microsecondsSinceEpoch}-$pid',
      ),
    );
    final helperCopy = File(
      path.join(helperDirectory.path, 'wynime_update.exe'),
    );
    final startupMarker = File(
      path.join(installRoot.path, windowsUpdateStartupMarker),
    );
    final downloadedStaging = Directory(update.stagingDirectoryPath);
    var helperStarted = false;
    var databaseQuiesced = false;
    DatabaseRecoveryPoint? recoveryPoint;

    try {
      // Prepare every path that can fail before quiescing the database. Once
      // the snapshot exists, the only permitted next operation is starting
      // the detached helper.
      await _copyDirectoryContents(Directory(extractedPath), handoffStage);
      await helperDirectory.create(recursive: true);
      await updater.copy(helperCopy.path);
      if (await startupMarker.exists()) await startupMarker.delete();
      final point = await recovery.createRecoveryPointAndQuiesce();
      recoveryPoint = point;
      databaseQuiesced = true;
      // The native helper waits for this process to exit before replacing the
      // install directory. Keep the live Drift connection open until the
      // handoff is successfully started; closing it earlier would leave the
      // still-running app unusable if Process.start or argument validation
      // failed. Process termination releases the SQLite handles atomically.
      final arguments = <String>[
        '--install-root',
        installRoot.path,
        '--stage-root',
        handoffStage.path,
        '--parent-pid',
        '$pid',
        '--startup-marker',
        startupMarker.path,
      ];
      if (point.databasePath != null && point.databasePath!.isNotEmpty) {
        arguments.addAll(<String>[
          '--recovery-snapshot',
          point.identifier,
          '--recovery-database',
          point.databasePath!,
        ]);
      }
      final process = await Process.start(
        helperCopy.path,
        arguments,
        workingDirectory: helperDirectory.path,
        mode: ProcessStartMode.detached,
      );
      if (process.pid <= 0) {
        throw const ProcessException(
          'wynime_update.exe',
          <String>[],
          'start_failed',
        );
      }
      helperStarted = true;
      try {
        if (await downloadedStaging.exists()) {
          await downloadedStaging.delete(recursive: true);
        }
      } on Object {
        // The detached helper owns handoffStage. A cleanup failure in the
        // downloaded archive directory must never enter the outer error path
        // and delete paths the helper is already consuming.
      }
    } on FileSystemException {
      if (!helperStarted) {
        await _abortDatabaseHandoff(recovery, recoveryPoint, databaseQuiesced);
        await _cleanup(handoffStage, helperDirectory, downloadedStaging);
      }
      return const SoftwareInstallResult(
        started: false,
        requiresUserAction: true,
        message: 'manual_update_required',
      );
    } on SoftwareUpdateException catch (error) {
      if (!helperStarted) {
        await _abortDatabaseHandoff(recovery, recoveryPoint, databaseQuiesced);
        await _cleanup(handoffStage, helperDirectory, downloadedStaging);
      }
      return SoftwareInstallResult(started: false, message: error.code);
    } on Object {
      if (!helperStarted) {
        await _abortDatabaseHandoff(recovery, recoveryPoint, databaseQuiesced);
        await _cleanup(handoffStage, helperDirectory, downloadedStaging);
      }
      return const SoftwareInstallResult(
        started: false,
        message: 'installer_failed',
      );
    }
    if (exitAfterHandoff) exit(0);
    return const SoftwareInstallResult(started: true);
  }

  Future<void> _abortDatabaseHandoff(
    DatabaseRecoveryPort recovery,
    DatabaseRecoveryPoint? point,
    bool databaseQuiesced,
  ) async {
    if (!databaseQuiesced || point == null) return;
    try {
      await recovery.resumeAfterAbortedHandoff(point);
    } on Object {
      // The concrete port only releases the in-process write barrier. Keep
      // attempting snapshot cleanup so a failed Process.start cannot leave a
      // stale recovery copy behind.
    }
    try {
      await recovery.discardRecoveryPoint(point);
    } on Object {
      // Best effort cleanup; the next update attempt will recreate the
      // recovery directory and the user-facing result remains manual-safe.
    }
  }

  Future<void> _copyDirectoryContents(
    Directory source,
    Directory destination,
  ) async {
    await destination.create(recursive: true);
    await for (final entity in source.list(followLinks: false)) {
      final target = path.join(destination.path, path.basename(entity.path));
      if (entity is Directory) {
        await _copyDirectoryContents(entity, Directory(target));
      } else if (entity is File) {
        await entity.copy(target);
      } else {
        throw const FileSystemException('unsupported_archive_entry');
      }
    }
  }

  Future<void> _cleanup(
    Directory handoffStage,
    Directory helperDirectory,
    Directory downloadedStaging,
  ) async {
    try {
      if (await handoffStage.exists()) {
        await handoffStage.delete(recursive: true);
      }
      if (await helperDirectory.exists()) {
        await helperDirectory.delete(recursive: true);
      }
      if (await downloadedStaging.exists()) {
        await downloadedStaging.delete(recursive: true);
      }
    } on Object {
      // Best effort cleanup; the helper never receives a partially staged path.
    }
  }
}

final class UnsupportedSoftwareUpdateInstaller
    implements SoftwareUpdateInstaller {
  const UnsupportedSoftwareUpdateInstaller();

  @override
  Future<SoftwareInstallResult> install(DownloadedUpdate update) async =>
      const SoftwareInstallResult(
        started: false,
        requiresUserAction: true,
        message: 'manual_update_required',
      );
}
