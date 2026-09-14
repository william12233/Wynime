import 'dart:collection';

import 'package:flutter/foundation.dart';

import '../application/source_installed_live_search_pipeline.dart';
import '../application/source_live_operation_plan_factory.dart';
import '../domain/models/source_models.dart';
import '../domain/models/source_package_manager_models.dart';
import '../domain/models/source_search_coordinator_models.dart';

typedef InstalledSourcePackagesProvider =
    Iterable<InstalledSourcePackage> Function();

typedef SourceInstalledLiveSearchOperation =
    Future<SourceInstalledLiveSearchPipelineResult> Function({
      required Iterable<InstalledSourcePackage> installedPackages,
      required String query,
    });

enum SourceSearchPresentationStatus {
  idle,
  loading,
  available,
  partial,
  notFound,
  noSources,
  noUsableSources,
  invalidQuery,
  failed,
}

/// One safe UI projection of a normalized source result.
///
/// The normalized [result] remains the source of the subject identity and
/// title. The package display name is presentation-only provenance obtained
/// from the same installed-package snapshot; no request or diagnostic data is
/// copied into this value.
final class SourceSearchPresentationItem {
  SourceSearchPresentationItem({
    required this.result,
    required String sourceDisplayName,
  }) : sourceDisplayName = sourceDisplayName.trim();

  final SourceSearchResult result;
  final String sourceDisplayName;
}

final class SourceSearchPresentationState {
  SourceSearchPresentationState({
    required this.status,
    required String query,
    Iterable<SourceSearchPresentationItem> results = const [],
  }) : query = query.trim(),
       results = UnmodifiableListView(
         List<SourceSearchPresentationItem>.unmodifiable(results),
       );

  SourceSearchPresentationState.idle()
    : status = SourceSearchPresentationStatus.idle,
      query = '',
      results = UnmodifiableListView<SourceSearchPresentationItem>(const []);

  final SourceSearchPresentationStatus status;
  final String query;
  final UnmodifiableListView<SourceSearchPresentationItem> results;

  @override
  String toString() => {
    'status': status.name,
    'queryLength': query.length,
    'resultCount': results.length,
  }.toString();
}

/// Owns only Search presentation state and request identity.
///
/// The installed-package provider remains the application's package-lifecycle
/// authority, and [searchOperation] is the one application boundary supplied
/// by the caller. This controller never creates source runtimes, plans,
/// transports or coordinators, and never closes a shared pipeline.
final class SourceSearchPresentationController extends ChangeNotifier {
  SourceSearchPresentationController({
    required this.installedPackages,
    this.searchOperation,
  });

  final InstalledSourcePackagesProvider installedPackages;
  final SourceInstalledLiveSearchOperation? searchOperation;

  SourceSearchPresentationState _state = SourceSearchPresentationState.idle();
  String? _lastValidQuery;
  var _generation = 0;
  var _disposed = false;

  SourceSearchPresentationState get state => _state;

  Future<void> search(String rawQuery) async {
    if (_disposed) return;
    final operation = ++_generation;
    final query = rawQuery.trim();
    _lastValidQuery = null;

    if (query.isEmpty) {
      _emit(SourceSearchPresentationState.idle());
      return;
    }
    if (!_validQuery(query)) {
      _emit(
        SourceSearchPresentationState(
          status: SourceSearchPresentationStatus.invalidQuery,
          query: '',
        ),
      );
      return;
    }

    _lastValidQuery = query;
    _emit(
      SourceSearchPresentationState(
        status: SourceSearchPresentationStatus.loading,
        query: query,
      ),
    );

    final applicationSearch = searchOperation;
    if (applicationSearch == null) {
      if (_isCurrent(operation)) {
        _emit(
          SourceSearchPresentationState(
            status: SourceSearchPresentationStatus.noSources,
            query: query,
          ),
        );
      }
      return;
    }

    final Iterable<InstalledSourcePackage> packageSnapshot;
    try {
      packageSnapshot = installedPackages();
    } on Object {
      if (_isCurrent(operation)) _emit(_failedState(query));
      return;
    }

    SourceInstalledLiveSearchPipelineResult result;
    try {
      result = await applicationSearch(
        installedPackages: packageSnapshot,
        query: query,
      );
    } on Object {
      if (_isCurrent(operation)) _emit(_failedState(query));
      return;
    }
    if (!_isCurrent(operation)) return;

    try {
      _emit(_stateForResult(query, result));
    } on Object {
      _emit(_failedState(query));
    }
  }

  Future<void> retry() {
    final query = _lastValidQuery;
    if (query == null || _disposed) return Future<void>.value();
    return search(query);
  }

  /// Invalidates the current presentation operation without closing the
  /// application-owned live-search pipeline.
  void reset() {
    if (_disposed) return;
    _generation++;
    _lastValidQuery = null;
    if (_state.status == SourceSearchPresentationStatus.idle &&
        _state.query.isEmpty) {
      return;
    }
    _emit(SourceSearchPresentationState.idle());
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    _lastValidQuery = null;
    super.dispose();
  }

  bool _isCurrent(int operation) => !_disposed && operation == _generation;

  SourceSearchPresentationState _stateForResult(
    String query,
    SourceInstalledLiveSearchPipelineResult result,
  ) {
    final status = switch (result.status) {
      SourceInstalledLiveSearchPipelineStatus.available =>
        SourceSearchPresentationStatus.available,
      SourceInstalledLiveSearchPipelineStatus.partial =>
        SourceSearchPresentationStatus.partial,
      SourceInstalledLiveSearchPipelineStatus.notFound =>
        SourceSearchPresentationStatus.notFound,
      SourceInstalledLiveSearchPipelineStatus.noSources =>
        SourceSearchPresentationStatus.noSources,
      SourceInstalledLiveSearchPipelineStatus.noUsableSources =>
        SourceSearchPresentationStatus.noUsableSources,
      SourceInstalledLiveSearchPipelineStatus.failed =>
        SourceSearchPresentationStatus.failed,
    };
    final coordinator = result.coordinatorResult;
    if (status == SourceSearchPresentationStatus.available ||
        status == SourceSearchPresentationStatus.partial) {
      if (coordinator == null || coordinator.results.isEmpty) {
        throw StateError('An available search state requires results.');
      }
      return SourceSearchPresentationState(
        status: status,
        query: query,
        results: _presentationItems(result, coordinator),
      );
    }
    return SourceSearchPresentationState(status: status, query: query);
  }

  List<SourceSearchPresentationItem> _presentationItems(
    SourceInstalledLiveSearchPipelineResult result,
    SourceSearchCoordinatorResult coordinator,
  ) {
    final sourceNames = <String, String>{};
    for (final packageResult in result.packageResults) {
      if (packageResult.status != SourceLiveOperationPlanFactoryStatus.ready) {
        continue;
      }
      final plan = packageResult.plan;
      if (plan == null) throw StateError('A ready package has no plan.');
      sourceNames[packageResult.packageId] =
          plan.requestPlan.installedPackage.package.displayName;
    }
    return [
      for (final sourceResult in coordinator.results)
        SourceSearchPresentationItem(
          result: sourceResult,
          sourceDisplayName:
              sourceNames[sourceResult.sourceId] ?? sourceResult.sourceId,
        ),
    ];
  }

  SourceSearchPresentationState _failedState(String query) =>
      SourceSearchPresentationState(
        status: SourceSearchPresentationStatus.failed,
        query: query,
      );

  void _emit(SourceSearchPresentationState next) {
    if (_disposed) return;
    _state = next;
    notifyListeners();
  }

  static bool _validQuery(String value) {
    return value.isNotEmpty &&
        value.length <= 128 &&
        !value.codeUnits.any(
          (unit) => unit < 0x20 || (unit >= 0x7f && unit <= 0x9f),
        );
  }
}
