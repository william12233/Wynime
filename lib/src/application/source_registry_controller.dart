import 'dart:async';

import 'package:flutter/foundation.dart';

import '../infrastructure/source_registry/github_source_registry_repository.dart';
import '../infrastructure/source_rules/source_registry_artifact_catalog.dart';

enum SourceRegistryStatus { idle, loading, ready, failed }

/// Coordinates one read-only remote source-registry snapshot for presentation.
///
/// The controller owns no package lifecycle state. A catalog is only an
/// immutable candidate snapshot; installation, consent and activation remain
/// separate manager operations.
final class SourceRegistryController extends ChangeNotifier {
  SourceRegistryController({required this.loadCatalog, this.closeRepository});

  final Future<SourceRegistryCatalog> Function() loadCatalog;
  final VoidCallback? closeRepository;

  SourceRegistryStatus get status => _status;
  String? get errorCode => _errorCode;
  SourceRegistryCatalog? get catalog => _catalog;

  SourceRegistryStatus _status = SourceRegistryStatus.idle;
  String? _errorCode;
  SourceRegistryCatalog? _catalog;
  Future<void>? _initializeFuture;
  Future<void>? _inFlight;
  bool _disposed = false;

  /// Starts the first remote snapshot load at most once.
  Future<void> initialize() {
    final existing = _initializeFuture;
    if (existing != null) return existing;
    final future = refresh();
    _initializeFuture = future;
    return future;
  }

  /// Requests one explicit read-only refresh. Concurrent callers share it.
  Future<void> refresh() {
    if (_disposed) return Future<void>.value();
    final existing = _inFlight;
    if (existing != null) return existing;

    _status = SourceRegistryStatus.loading;
    _errorCode = null;
    notifyListeners();
    final completion = Completer<void>();
    _inFlight = completion.future;
    unawaited(_load(completion));
    return completion.future;
  }

  Future<void> _load(Completer<void> completion) async {
    try {
      final loaded = await loadCatalog();
      if (_disposed) return;
      _catalog = loaded;
      _status = SourceRegistryStatus.ready;
    } on SourceRegistryRepositoryException catch (error) {
      if (_disposed) return;
      _status = SourceRegistryStatus.failed;
      _errorCode = _safeCode(error.code, fallback: 'load_failed');
    } on Object {
      if (_disposed) return;
      _status = SourceRegistryStatus.failed;
      _errorCode = 'load_failed';
    } finally {
      if (identical(_inFlight, completion.future)) _inFlight = null;
      try {
        if (!_disposed) notifyListeners();
      } finally {
        if (!completion.isCompleted) completion.complete();
      }
    }
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    closeRepository?.call();
    super.dispose();
  }
}

String _safeCode(String value, {required String fallback}) {
  return RegExp(r'^[a-z][a-z0-9_]{0,63}$').hasMatch(value) ? value : fallback;
}
