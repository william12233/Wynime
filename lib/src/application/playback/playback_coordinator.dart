import 'dart:async';

import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/services/playback_proxy.dart';
import 'package:wynime/src/domain/services/playback_session_resolver.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

final class PlaybackOpenRequest {
  PlaybackOpenRequest({
    required this.resolution,
    required this.proxyBudget,
    this.addressFamily = LoopbackAddressFamily.ipv4,
    this.refreshLeeway = const Duration(seconds: 30),
    this.maxAutomaticRefreshes = 1,
  }) {
    if (refreshLeeway.isNegative) {
      throw ArgumentError.value(
        refreshLeeway,
        'refreshLeeway',
        'Must not be negative.',
      );
    }
    if (maxAutomaticRefreshes < 0 || maxAutomaticRefreshes > 3) {
      throw ArgumentError.value(
        maxAutomaticRefreshes,
        'maxAutomaticRefreshes',
        'Must be between 0 and 3.',
      );
    }
  }

  final PlaybackSessionResolutionRequest resolution;
  final PlaybackProxyBudget proxyBudget;
  final LoopbackAddressFamily addressFamily;
  final Duration refreshLeeway;
  final int maxAutomaticRefreshes;
}

final class PlaybackCoordinator {
  factory PlaybackCoordinator({
    required PlaybackSessionResolver resolver,
    required PlaybackProxyService proxy,
    required PlayerBackend player,
  }) => PlaybackCoordinator._(resolver, proxy, player);

  PlaybackCoordinator._(this._resolver, this._proxy, this._player) {
    _playerSubscription = _player.events.listen(
      _handlePlayerEvent,
      onError: (Object error, StackTrace stackTrace) {
        _events.addError(error, stackTrace);
      },
    );
  }

  final PlaybackSessionResolver _resolver;
  final PlaybackProxyService _proxy;
  final PlayerBackend _player;
  final StreamController<PlaybackEvent> _events =
      StreamController<PlaybackEvent>.broadcast();
  late final StreamSubscription<PlaybackEvent> _playerSubscription;

  _ActivePlayback? _active;
  int _operation = 0;
  bool _closed = false;
  int? _refreshingOperation;

  Stream<PlaybackEvent> get events => _events.stream;

  PlaybackSession? get currentSession => _active?.session;

  bool get hasActivePlayback => _active != null;

  Future<PlaybackSession> open(PlaybackOpenRequest request) async {
    _ensureOpen();
    final operation = ++_operation;
    await _stopActive();

    var session = await _resolver.resolve(request.resolution);
    if (session.expiresWithin(request.refreshLeeway) &&
        session.refresh != null) {
      session = await session.refreshed();
    }
    if (operation != _operation || _closed) {
      throw StateError(
        'Playback open was superseded or coordinator was closed.',
      );
    }

    final lease = await _proxy.expose(
      PlaybackProxyRequest(
        session: session,
        securityPolicy: request.resolution.securityPolicy,
        budget: request.proxyBudget,
        addressFamily: request.addressFamily,
      ),
    );
    if (operation != _operation || _closed) {
      await lease.close();
      throw StateError(
        'Playback open was superseded or coordinator was closed.',
      );
    }

    final playable = session.withPlaybackUri(lease.playbackUri);
    final active = _ActivePlayback(
      session: playable,
      lease: lease,
      request: request,
      operation: operation,
    );
    _active = active;
    try {
      await _player.open(playable);
    } on Object {
      if (identical(_active, active)) {
        _active = null;
      }
      await lease.close();
      rethrow;
    }
    return playable;
  }

  Future<void> play() async {
    _ensureActive();
    await _player.play();
  }

  Future<void> pause() async {
    _ensureActive();
    await _player.pause();
  }

  Future<void> seek(Duration position) async {
    _ensureActive();
    await _player.seek(position);
  }

  Future<void> setVolume(double volume) async {
    _ensureActive();
    await _player.setVolume(volume);
  }

  Future<void> setRate(double rate) async {
    _ensureActive();
    await _player.setRate(rate);
  }

  Future<void> selectAudioTrack(String? trackId) async {
    _ensureActive();
    await _player.selectAudioTrack(trackId);
  }

  Future<void> selectSubtitleTrack(String? trackId) async {
    _ensureActive();
    await _player.selectSubtitleTrack(trackId);
  }

  Future<void> stop() async {
    _ensureOpen();
    ++_operation;
    await _stopActive();
  }

  Future<void> close() async {
    if (_closed) {
      return;
    }
    _closed = true;
    ++_operation;
    await _stopActive();
    await _proxy.close();
    await _playerSubscription.cancel();
    await _events.close();
  }

  void _handlePlayerEvent(PlaybackEvent event) {
    if (!_events.isClosed) {
      _events.add(event);
    }
    final failure = event.failure;
    final active = _active;
    if (failure?.shouldRefreshSession == true &&
        active != null &&
        _refreshingOperation != active.operation &&
        active.automaticRefreshes < active.request.maxAutomaticRefreshes &&
        active.session.refresh != null) {
      unawaited(_refresh(active));
    }
  }

  Future<void> _refresh(_ActivePlayback expected) async {
    _refreshingOperation = expected.operation;
    try {
      var refreshed = await expected.session.refreshed();
      if (_closed || !identical(_active, expected)) {
        return;
      }

      _active = null;
      try {
        await _player.close();
      } finally {
        await expected.lease.close();
      }
      if (_closed || expected.operation != _operation || _active != null) {
        return;
      }

      final lease = await _proxy.expose(
        PlaybackProxyRequest(
          session: refreshed,
          securityPolicy: expected.request.resolution.securityPolicy,
          budget: expected.request.proxyBudget,
          addressFamily: expected.request.addressFamily,
        ),
      );
      if (_closed || expected.operation != _operation || _active != null) {
        await lease.close();
        return;
      }

      refreshed = refreshed.withPlaybackUri(lease.playbackUri);
      final next = _ActivePlayback(
        session: refreshed,
        lease: lease,
        request: expected.request,
        operation: expected.operation,
        automaticRefreshes: expected.automaticRefreshes + 1,
      );
      _active = next;
      try {
        await _player.open(refreshed);
      } on Object {
        if (identical(_active, next)) {
          _active = null;
        }
        await lease.close();
        rethrow;
      }
    } on Object catch (error, stackTrace) {
      if (!_events.isClosed) {
        _events.addError(error, stackTrace);
      }
    } finally {
      if (_refreshingOperation == expected.operation) {
        _refreshingOperation = null;
      }
    }
  }

  Future<void> _stopActive() async {
    final active = _active;
    _active = null;
    if (active == null) {
      return;
    }
    try {
      await _player.close();
    } finally {
      await active.lease.close();
    }
  }

  void _ensureOpen() {
    if (_closed) {
      throw StateError('PlaybackCoordinator is closed.');
    }
  }

  void _ensureActive() {
    _ensureOpen();
    if (_active == null) {
      throw StateError('No active PlaybackSession.');
    }
  }
}

final class _ActivePlayback {
  _ActivePlayback({
    required this.session,
    required this.lease,
    required this.request,
    required this.operation,
    this.automaticRefreshes = 0,
  });

  final PlaybackSession session;
  final PlaybackProxyLease lease;
  final PlaybackOpenRequest request;
  final int operation;
  final int automaticRefreshes;
}
