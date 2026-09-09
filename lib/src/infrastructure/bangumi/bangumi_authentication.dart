// The public constructor deliberately keeps public parameter names while the
// implementation fields remain private.
// ignore_for_file: prefer_initializing_formals

import 'dart:convert';
import 'dart:math';

import '../../domain/models/bangumi_models.dart';
import '../../domain/services/bangumi_ports.dart';
import 'bangumi_api_client.dart';

const _callbackPath = '/oauth/callback';
const _maxAccessTokenLifetime = Duration(days: 30);

final class BangumiBrokerAuthentication implements BangumiAuthenticationPort {
  BangumiBrokerAuthentication({
    required Uri workerOrigin,
    required String clientId,
    required String verifiedAppLinkHost,
    Uri? redirectUri,
    BangumiCallbackPort? callbackPort,
    BangumiHttpTransport? transport,
    DateTime Function()? clock,
    Duration ticketLifetime = const Duration(minutes: 5),
  }) : _workerOrigin = _validateWorkerOrigin(workerOrigin),
       _verifiedAppLinkHost = _validateVerifiedAppLinkHost(verifiedAppLinkHost),
       _clientId = _validateClientId(clientId),
       _redirectUri = null,
       _callbackPort = callbackPort,
       _transport = transport ?? IoBangumiHttpTransport(),
       _clock = clock ?? DateTime.now,
       _ticketLifetime = ticketLifetime {
    if (_workerOrigin.host != _verifiedAppLinkHost) {
      throw ArgumentError(
        'Bangumi broker origin and app-link host must match.',
      );
    }
    if (redirectUri != null) {
      _redirectUri = _validateRedirectUri(redirectUri);
    }
  }

  final Uri _workerOrigin;
  final String _verifiedAppLinkHost;
  final String _clientId;
  Uri? _redirectUri;
  final BangumiCallbackPort? _callbackPort;
  final BangumiHttpTransport _transport;
  final DateTime Function() _clock;
  final Duration _ticketLifetime;
  String? _pendingState;
  DateTime? _pendingAt;

  @override
  Future<BangumiAuthorizationRequest> begin() async {
    final callbackPort = _callbackPort;
    if (callbackPort != null) {
      _redirectUri = _validateRedirectUri(
        await callbackPort.prepareRedirectUri(),
      );
    }
    final redirectUri = _redirectUri;
    if (redirectUri == null) {
      throw const BangumiApiException(code: 'redirect_uri_unavailable');
    }
    final state = _randomToken(32);
    _pendingState = state;
    _pendingAt = _clock().toUtc();
    final uri = _workerOrigin.replace(
      path:
          '${_workerOrigin.path == '/' ? '' : _workerOrigin.path}/oauth/start',
      queryParameters: <String, String>{
        'client_id': _clientId,
        'redirect_uri': redirectUri.toString(),
        'state': state,
      },
    );
    return BangumiAuthorizationRequest(
      authorizationUri: uri,
      state: state,
      redirectUri: redirectUri,
    );
  }

  @override
  Future<BangumiAuthSession> redeem(BangumiAuthCallback callback) async {
    _validateCallbackState(callback.state);
    if (callback.error != null) {
      _clearPending();
      throw BangumiApiException(code: 'oauth_${_safeError(callback.error!)}');
    }
    final ticket = callback.ticket ?? callback.code;
    if (ticket == null || ticket.isEmpty || ticket.length > 4096) {
      _clearPending();
      throw const BangumiApiException(code: 'oauth_ticket_missing');
    }
    final redirectUri = _redirectUri;
    if (redirectUri == null) {
      _clearPending();
      throw const BangumiApiException(code: 'redirect_uri_unavailable');
    }
    final response = await _postJson('/oauth/redeem', <String, Object?>{
      'client_id': _clientId,
      'redirect_uri': redirectUri.toString(),
      'state': callback.state,
      'ticket': ticket,
    });
    _clearPending();
    return _sessionFromJson(response);
  }

  @override
  Future<BangumiAuthSession> refresh(BangumiAuthSession session) async {
    if (session.refreshToken.isEmpty || session.refreshToken.length > 4096) {
      throw const BangumiApiException(code: 'reauth_required');
    }
    final response = await _postJson('/oauth/refresh', <String, Object?>{
      'client_id': _clientId,
      'account_id': session.accountId,
      'refresh_token': session.refreshToken,
    });
    final refreshed = _sessionFromJson(response);
    if (refreshed.accountId != session.accountId) {
      throw const BangumiApiException(code: 'oauth_account_mismatch');
    }
    return refreshed;
  }

  @override
  Future<void> signOut() async {
    _clearPending();
  }

  Future<Map<String, dynamic>> _postJson(
    String path,
    Map<String, Object?> body,
  ) async {
    final uri = _workerOrigin.replace(
      path: '${_workerOrigin.path == '/' ? '' : _workerOrigin.path}$path',
    );
    final response = await _transport.send(
      method: 'POST',
      uri: uri,
      headers: const <String, String>{
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'User-Agent': 'Wynime-Bangumi-Auth/1',
      },
      body: jsonEncode(body),
      maxResponseBytes: 64 * 1024,
    );
    if (response.statusCode == 401 || response.statusCode == 403) {
      throw const BangumiApiException(code: 'oauth_rejected');
    }
    if (response.statusCode == 409) {
      throw const BangumiApiException(code: 'oauth_replay');
    }
    if (response.statusCode == 429) {
      throw const BangumiApiException(
        code: 'oauth_rate_limited',
        retryable: true,
      );
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw BangumiApiException(
        code: 'oauth_failed',
        statusCode: response.statusCode,
      );
    }
    final Object? decoded;
    try {
      decoded = jsonDecode(response.body);
    } on FormatException {
      throw const BangumiApiException(code: 'oauth_payload_invalid');
    }
    if (decoded is! Map) {
      throw const BangumiPayloadException('oauth_payload_not_object');
    }
    return Map<String, dynamic>.from(decoded);
  }

  BangumiAuthSession _sessionFromJson(Map<String, dynamic> json) {
    final accountId = json['account_id'] ?? json['accountId'];
    final accessToken = json['access_token'] ?? json['accessToken'];
    final refreshToken = json['refresh_token'] ?? json['refreshToken'];
    final expiresIn = json['expires_in'] ?? json['expiresIn'];
    if (accountId is! String ||
        accountId.isEmpty ||
        accessToken is! String ||
        accessToken.isEmpty ||
        refreshToken is! String ||
        refreshToken.isEmpty) {
      throw const BangumiPayloadException('oauth_token_payload_invalid');
    }
    final seconds = expiresIn is num
        ? expiresIn.toInt()
        : int.tryParse('$expiresIn');
    if (seconds == null ||
        seconds < 30 ||
        seconds > _maxAccessTokenLifetime.inSeconds) {
      throw const BangumiPayloadException('oauth_expiry_invalid');
    }
    return BangumiAuthSession(
      accountId: accountId,
      accessToken: accessToken,
      refreshToken: refreshToken,
      expiresAt: _clock().toUtc().add(Duration(seconds: seconds)),
    );
  }

  void _validateCallbackState(String state) {
    final pendingState = _pendingState;
    final pendingAt = _pendingAt;
    if (pendingState == null ||
        pendingAt == null ||
        state != pendingState ||
        _clock().toUtc().difference(pendingAt) > _ticketLifetime) {
      _clearPending();
      throw const BangumiApiException(code: 'oauth_state_mismatch');
    }
  }

  void _clearPending() {
    _pendingState = null;
    _pendingAt = null;
  }

  static Uri _validateWorkerOrigin(Uri value) {
    if (value.scheme != 'https' ||
        value.port != 443 ||
        value.host.isEmpty ||
        !_isSafeHost(value.host) ||
        value.userInfo.isNotEmpty ||
        value.hasFragment ||
        value.hasQuery ||
        (value.path.isNotEmpty && value.path != '/')) {
      throw ArgumentError('Bangumi broker must use HTTPS.');
    }
    return value;
  }

  static String _validateVerifiedAppLinkHost(String value) {
    final host = value.trim().toLowerCase();
    if (!_isSafeHost(host)) {
      throw ArgumentError('Bangumi app-link host is invalid.');
    }
    return host;
  }

  Uri _validateRedirectUri(Uri value) {
    final isLoopback =
        value.host == '127.0.0.1' &&
        value.scheme == 'http' &&
        value.port > 0 &&
        value.port <= 65535 &&
        value.path == _callbackPath;
    final isVerifiedAppLink =
        value.scheme == 'https' &&
        value.host == _verifiedAppLinkHost &&
        value.port == 443 &&
        value.path == _callbackPath;
    if ((!isLoopback && !isVerifiedAppLink) ||
        value.userInfo.isNotEmpty ||
        value.hasFragment ||
        value.hasQuery) {
      throw ArgumentError('Bangumi redirect URI is not allowed.');
    }
    return value;
  }

  static bool _isSafeHost(String host) {
    if (host.length > 253) return false;
    final labels = host.split('.');
    if (labels.any((label) => label.isEmpty || label.length > 63)) {
      return false;
    }
    final labelPattern = RegExp(r'^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$');
    return labels.every(labelPattern.hasMatch);
  }

  static String _validateClientId(String value) {
    if (value.isEmpty ||
        value.length > 256 ||
        value.contains(RegExp(r'[\r\n]'))) {
      throw ArgumentError('Bangumi client ID is invalid.');
    }
    return value;
  }

  static String _randomToken(int bytes) {
    final random = Random.secure();
    // Keep the OAuth state in the unpadded base64url alphabet accepted by the
    // broker and avoid introducing a query-string padding contract.
    return base64UrlEncode(
      List<int>.generate(bytes, (_) => random.nextInt(256)),
    ).replaceAll('=', '');
  }

  static String _safeError(String value) {
    final normalized = value.replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_');
    if (normalized.isEmpty) return 'unknown';
    return normalized.length > 64 ? normalized.substring(0, 64) : normalized;
  }
}

final class UnavailableBangumiAuthentication
    implements BangumiAuthenticationPort {
  const UnavailableBangumiAuthentication();

  static const _error = BangumiApiException(
    code: 'bangumi_service_not_configured',
  );

  @override
  Future<BangumiAuthorizationRequest> begin() => Future.error(_error);

  @override
  Future<BangumiAuthSession> redeem(BangumiAuthCallback callback) =>
      Future.error(_error);

  @override
  Future<BangumiAuthSession> refresh(BangumiAuthSession session) =>
      Future.error(_error);

  @override
  Future<void> signOut() async {}
}
