// The public constructor deliberately keeps public parameter names while the
// implementation fields remain private.
// ignore_for_file: prefer_initializing_formals

import 'package:flutter/foundation.dart';

import '../../domain/models/software_update_models.dart';

final class SoftwareUpdateController extends ChangeNotifier {
  SoftwareUpdateController({
    required SoftwareUpdateServicePort service,
    required SoftwareUpdateInstallerPort installer,
  }) : _service = service,
       _installer = installer;

  final SoftwareUpdateServicePort _service;
  final SoftwareUpdateInstallerPort _installer;

  UpdateStatus status = UpdateStatus.idle;
  AppVersionInfo? currentVersion;
  SoftwareRelease? latestRelease;
  ReleaseAsset? latestAsset;
  SoftwareUpdateException? error;
  double? downloadProgress;
  bool autoCheckUpdates = false;

  Future<SoftwareUpdateResult>? _checkFuture;
  Future<SoftwareInstallResult?>? _installFuture;
  SoftwareUpdateResult? _lastResult;

  bool get isBusy =>
      status == UpdateStatus.checking ||
      status == UpdateStatus.downloading ||
      status == UpdateStatus.verifying ||
      status == UpdateStatus.handingOff;

  Future<void> initialize() async {
    try {
      currentVersion = await _service.currentVersion();
    } on Object {
      error = const SoftwareUpdateException(
        UpdateFailureReason.invalidMetadata,
        'version_unavailable',
      );
    }
    notifyListeners();
  }

  Future<SoftwareUpdateResult> check({bool automatic = false}) async {
    if (automatic && !autoCheckUpdates) {
      return _lastResult ??
          SoftwareUpdateResult(
            status: UpdateStatus.idle,
            current: currentVersion ?? _fallbackVersion,
          );
    }
    final existing = _checkFuture;
    if (existing != null) return existing;
    if (status == UpdateStatus.downloading ||
        status == UpdateStatus.verifying ||
        status == UpdateStatus.handingOff) {
      return _lastResult ??
          SoftwareUpdateResult(
            status: status,
            current: currentVersion ?? _fallbackVersion,
          );
    }
    final future = _performCheck();
    _checkFuture = future;
    try {
      return await future;
    } finally {
      if (identical(_checkFuture, future)) _checkFuture = null;
    }
  }

  Future<SoftwareInstallResult?> install() async {
    final existing = _installFuture;
    if (existing != null) return existing;
    final result = _lastResult;
    if (result == null || !result.hasUpdate) return null;
    final future = _performInstall(result);
    _installFuture = future;
    try {
      return await future;
    } finally {
      if (identical(_installFuture, future)) _installFuture = null;
    }
  }

  @override
  void dispose() {
    _service.dispose();
    super.dispose();
  }

  Future<SoftwareUpdateResult> _performCheck() async {
    status = UpdateStatus.checking;
    error = null;
    notifyListeners();
    try {
      final result = await _service.checkForUpdates();
      currentVersion = result.current;
      latestRelease = result.release;
      latestAsset = result.asset;
      status = result.status;
      error = result.error;
      _lastResult = result;
      notifyListeners();
      return result;
    } on SoftwareUpdateException catch (exception) {
      return _setCheckError(exception);
    } on Object {
      return _setCheckError(
        const SoftwareUpdateException(
          UpdateFailureReason.network,
          'check_failed',
        ),
      );
    }
  }

  Future<SoftwareUpdateResult> _setCheckError(
    SoftwareUpdateException exception,
  ) async {
    currentVersion ??= await _loadCurrentOrFallback();
    status = UpdateStatus.failed;
    error = exception;
    final result = SoftwareUpdateResult(
      status: status,
      current: currentVersion!,
      error: exception,
    );
    _lastResult = result;
    notifyListeners();
    return result;
  }

  Future<SoftwareInstallResult?> _performInstall(
    SoftwareUpdateResult result,
  ) async {
    status = UpdateStatus.downloading;
    error = null;
    downloadProgress = null;
    notifyListeners();
    try {
      final downloaded = await _service.download(
        result,
        onProgress: (received, total) {
          downloadProgress = total <= 0 ? null : received / total;
          notifyListeners();
        },
      );
      status = UpdateStatus.verifying;
      notifyListeners();
      status = UpdateStatus.handingOff;
      notifyListeners();
      final installed = await _installer.install(downloaded);
      if (!installed.started) {
        status = installed.requiresUserAction
            ? UpdateStatus.manualUpdateRequired
            : UpdateStatus.failed;
        if (!installed.requiresUserAction) {
          error = const SoftwareUpdateException(
            UpdateFailureReason.installer,
            'installer_failed',
          );
        }
      } else {
        status = UpdateStatus.idle;
      }
      notifyListeners();
      return installed;
    } on SoftwareUpdateException catch (exception) {
      status = UpdateStatus.failed;
      error = exception;
      notifyListeners();
      return const SoftwareInstallResult(started: false);
    } on Object {
      status = UpdateStatus.failed;
      error = const SoftwareUpdateException(
        UpdateFailureReason.installer,
        'installer_failed',
      );
      notifyListeners();
      return const SoftwareInstallResult(started: false);
    }
  }

  Future<AppVersionInfo> _loadCurrentOrFallback() async {
    try {
      return await _service.currentVersion();
    } on Object {
      return _fallbackVersion;
    }
  }

  static const _fallbackVersion = AppVersionInfo(
    version: '0.0.0',
    buildNumber: '',
    platform: SoftwareUpdatePlatform.unsupported,
    architecture: 'unknown',
  );
}
