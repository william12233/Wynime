import '../domain/models/source_live_capture_models.dart';
import '../domain/models/web_capture_models.dart';
import '../domain/services/source_live_capture.dart';
import 'source_live_capture_snapshot_validator.dart';

/// Admits one platform capture result only after rechecking the request's
/// allowlist, permissions, ordering and resource budgets.
///
/// Each new capture supersedes an older one. A platform port cannot be
/// force-cancelled through this contract, so late completions are converted
/// to a typed outcome and never update [latestResult].
final class SourceLiveCaptureCoordinator {
  SourceLiveCaptureCoordinator({required this.port});

  final SourceLiveCapturePort port;
  final SourceLiveCaptureSnapshotValidator _snapshotValidator =
      const SourceLiveCaptureSnapshotValidator();

  SourceLiveCaptureResult? get latestResult => _latestResult;
  bool get isClosed => _closed;

  int _generation = 0;
  bool _closed = false;
  SourceLiveCaptureResult? _latestResult;

  Future<SourceLiveCaptureResult> capture(
    SourceLiveCaptureRequest request,
  ) async {
    if (_closed) {
      return _closedResult(request);
    }

    final generation = ++_generation;
    try {
      final snapshot = await port.capture(request);
      if (_closed) {
        return _closedResult(request);
      }
      if (generation != _generation) {
        return _supersededResult(request);
      }

      final result = _validateSnapshot(request, snapshot);
      _latestResult = result;
      return result;
    } on SourceLiveCaptureFailure catch (error) {
      if (_closed) {
        return _closedResult(request);
      }
      if (generation != _generation) {
        return _supersededResult(request);
      }
      final result = SourceLiveCaptureResult(
        packageId: request.packageId,
        packageVersion: request.packageVersion,
        programId: request.programId,
        status: SourceLiveCaptureStatus.failed,
        reasonCode: _safeCaptureFailureCode(error.code),
      );
      _latestResult = result;
      return result;
    } catch (_) {
      if (_closed) {
        return _closedResult(request);
      }
      if (generation != _generation) {
        return _supersededResult(request);
      }

      final result = SourceLiveCaptureResult(
        packageId: request.packageId,
        packageVersion: request.packageVersion,
        programId: request.programId,
        status: SourceLiveCaptureStatus.failed,
        reasonCode: 'capture_failed',
      );
      _latestResult = result;
      return result;
    }
  }

  /// Closes admission. In-flight platform work may finish, but its result is
  /// ignored and returned as [SourceLiveCaptureStatus.closed].
  void close() {
    if (_closed) {
      return;
    }
    _closed = true;
    ++_generation;
  }

  SourceLiveCaptureResult _validateSnapshot(
    SourceLiveCaptureRequest request,
    WebCaptureSnapshot snapshot,
  ) => _snapshotValidator.validate(request, snapshot);

  SourceLiveCaptureResult _supersededResult(SourceLiveCaptureRequest request) =>
      SourceLiveCaptureResult(
        packageId: request.packageId,
        packageVersion: request.packageVersion,
        programId: request.programId,
        status: SourceLiveCaptureStatus.superseded,
        reasonCode: 'capture_superseded',
      );

  SourceLiveCaptureResult _closedResult(SourceLiveCaptureRequest request) =>
      SourceLiveCaptureResult(
        packageId: request.packageId,
        packageVersion: request.packageVersion,
        programId: request.programId,
        status: SourceLiveCaptureStatus.closed,
        reasonCode: 'capture_closed',
      );
}

String _safeCaptureFailureCode(String code) =>
    RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(code) ? code : 'capture_failed';
