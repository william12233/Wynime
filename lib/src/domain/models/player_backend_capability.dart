enum PlayerBackendAvailability { available, unavailable, incompatible }

final class PlayerBackendCapability {
  PlayerBackendCapability({
    required String backendId,
    required this.availability,
    required String code,
    this.clientApiVersion,
    this.runtimeVersion,
  }) : backendId = _requiredToken(backendId, 'backendId'),
       code = _requiredToken(code, 'code') {
    if (clientApiVersion != null && clientApiVersion! < 0) {
      throw ArgumentError.value(
        clientApiVersion,
        'clientApiVersion',
        'Must not be negative.',
      );
    }
  }

  final String backendId;
  final PlayerBackendAvailability availability;
  final String code;
  final int? clientApiVersion;
  final String? runtimeVersion;

  bool get isAvailable => availability == PlayerBackendAvailability.available;

  Map<String, Object?> toDiagnostic() => {
    'backendId': backendId,
    'availability': availability.name,
    'code': code,
    'clientApiVersion': clientApiVersion,
    'runtimeVersion': runtimeVersion,
  };
}

abstract interface class PlayerBackendCapabilitySource {
  Future<PlayerBackendCapability> probe();
}

String _requiredToken(String value, String name) {
  final normalized = value.trim().toLowerCase();
  if (normalized.isEmpty ||
      normalized.length > 96 ||
      !RegExp(r'^[a-z0-9][a-z0-9._-]*$').hasMatch(normalized)) {
    throw ArgumentError.value(value, name, 'Invalid capability token.');
  }
  return normalized;
}
