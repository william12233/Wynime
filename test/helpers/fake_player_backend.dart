import 'dart:async';

import 'package:wynime/src/domain/models/playback_events.dart';
import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/player_backend.dart';
import 'package:wynime/src/domain/services/player_backend.dart';

final class FakePlayerBackend implements PlayerBackend {
  FakePlayerBackend({
    required this.kind,
    required this.backendId,
    this.availabilityStatus = PlayerBackendAvailabilityStatus.available,
    this.availabilityReason = 'test_unavailable',
    this.openError,
    this.emitDuringOpen,
  });

  @override
  final PlayerBackendKind kind;

  @override
  final String backendId;

  PlayerBackendAvailabilityStatus availabilityStatus;
  String availabilityReason;
  Object? openError;
  PlaybackEvent? emitDuringOpen;

  final StreamController<PlaybackEvent> _events =
      StreamController<PlaybackEvent>.broadcast();
  final List<PlaybackSession> openedSessions = [];
  final List<Duration> seeks = [];
  final List<double> volumes = [];
  final List<double> rates = [];
  final List<String?> audioTracks = [];
  final List<String?> subtitleTracks = [];
  int probeCount = 0;
  int playCount = 0;
  int pauseCount = 0;
  int closeCount = 0;

  @override
  Stream<PlaybackEvent> get events => _events.stream;

  @override
  Future<PlayerBackendAvailability> probe() async {
    probeCount += 1;
    return switch (availabilityStatus) {
      PlayerBackendAvailabilityStatus.available =>
        PlayerBackendAvailability.available(backendId),
      PlayerBackendAvailabilityStatus.unavailable =>
        PlayerBackendAvailability.unavailable(
          backendId,
          reasonCode: availabilityReason,
        ),
      PlayerBackendAvailabilityStatus.probeFailed =>
        PlayerBackendAvailability.probeFailed(
          backendId,
          reasonCode: availabilityReason,
        ),
    };
  }

  @override
  Future<void> open(PlaybackSession session) async {
    final error = openError;
    if (error != null) {
      throw error;
    }
    openedSessions.add(session);
    final event = emitDuringOpen;
    if (event != null) {
      _events.add(event);
    }
  }

  @override
  Future<void> play() async {
    playCount += 1;
  }

  @override
  Future<void> pause() async {
    pauseCount += 1;
  }

  @override
  Future<void> seek(Duration position) async {
    seeks.add(position);
  }

  @override
  Future<void> setVolume(double volume) async {
    volumes.add(volume);
  }

  @override
  Future<void> setRate(double rate) async {
    rates.add(rate);
  }

  @override
  Future<void> selectAudioTrack(String? trackId) async {
    audioTracks.add(trackId);
  }

  @override
  Future<void> selectSubtitleTrack(String? trackId) async {
    subtitleTracks.add(trackId);
  }

  @override
  Future<void> close() async {
    closeCount += 1;
  }

  void emit(PlaybackEvent event) => _events.add(event);
}
