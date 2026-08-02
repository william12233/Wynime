enum PlaybackIntent { playing, paused }

final class PlaybackHandoffSnapshot {
  PlaybackHandoffSnapshot({
    required String sessionId,
    required String timelineMapIdentity,
    required this.position,
    required this.intent,
    required String sourceBackendId,
    required String targetBackendId,
  }) : sessionId = _requiredText(sessionId, 'sessionId', 128),
       timelineMapIdentity = _requiredText(
         timelineMapIdentity,
         'timelineMapIdentity',
         1024,
       ),
       sourceBackendId = _requiredText(
         sourceBackendId,
         'sourceBackendId',
         96,
       ),
       targetBackendId = _requiredText(
         targetBackendId,
         'targetBackendId',
         96,
       ) {
    if (position.isNegative) {
      throw ArgumentError.value(position, 'position', 'Must not be negative.');
    }
    if (this.sourceBackendId == this.targetBackendId) {
      throw ArgumentError('A handoff requires distinct source and target backends.');
    }
  }

  final String sessionId;
  final String timelineMapIdentity;
  final Duration position;
  final PlaybackIntent intent;
  final String sourceBackendId;
  final String targetBackendId;
}

String _requiredText(String value, String name, int maxLength) {
  final normalized = value.trim();
  if (normalized.isEmpty || normalized.length > maxLength) {
    throw ArgumentError.value(value, name, 'Invalid playback handoff text.');
  }
  return normalized;
}
