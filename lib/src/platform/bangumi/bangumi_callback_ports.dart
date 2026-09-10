import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

import '../../domain/models/bangumi_models.dart';
import '../../domain/services/bangumi_ports.dart';

final class WindowsLoopbackBangumiCallbackPort implements BangumiCallbackPort {
  HttpServer? _server;
  Completer<BangumiAuthCallback>? _callback;

  @override
  Future<Uri> prepareRedirectUri() async {
    await close();
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    _server = server;
    final callback = Completer<BangumiAuthCallback>();
    _callback = callback;
    unawaited(_serve(server, callback));
    return Uri.http('127.0.0.1:${server.port}', '/oauth/callback');
  }

  Future<void> _serve(
    HttpServer server,
    Completer<BangumiAuthCallback> callback,
  ) async {
    try {
      await for (final request in server) {
        if (request.method != 'GET' || request.uri.path != '/oauth/callback') {
          request.response.statusCode = HttpStatus.notFound;
          await request.response.close();
          continue;
        }
        final query = request.uri.queryParameters;
        final state = query['state'];
        if (state == null) {
          request.response.statusCode = HttpStatus.badRequest;
          await request.response.close();
          continue;
        }
        callback.complete(
          BangumiAuthCallback(
            state: state,
            ticket: query['ticket'],
            code: query['code'],
            error: query['error'],
          ),
        );
        request.response
          ..statusCode = HttpStatus.ok
          ..headers.contentType = ContentType.html
          ..write(
            '<!doctype html><title>Wynime</title>'
            '<p>登入完成，請返回 Wynime。</p>',
          );
        await request.response.close();
        await server.close(force: true);
        break;
      }
    } on Object {
      if (!callback.isCompleted) {
        callback.completeError(
          const BangumiApiException(code: 'callback_failed'),
        );
      }
    }
  }

  @override
  Future<BangumiAuthCallback> waitForCallback() {
    final callback = _callback;
    if (callback == null) {
      throw const BangumiApiException(code: 'callback_not_started');
    }
    return callback.future;
  }

  @override
  Future<void> close() async {
    await _server?.close(force: true);
    _server = null;
    _callback = null;
  }
}

final class AndroidAppLinkBangumiCallbackPort
    implements
        BangumiCallbackPort,
        BangumiPendingCallbackPort,
        BangumiOAuthStateStore,
        BangumiRefreshTokenStore {
  AndroidAppLinkBangumiCallbackPort({
    required this.redirectUri,
    MethodChannel? channel,
  }) : _channel =
           channel ??
           const MethodChannel('io.github.william12233.wynime/bangumi_auth');

  final MethodChannel _channel;
  final Uri redirectUri;

  @override
  Future<Uri> prepareRedirectUri() async {
    await _channel.invokeMethod<void>('prepareCallback');
    return redirectUri;
  }

  @override
  Future<BangumiAuthCallback> waitForCallback() async {
    return _callbackFromPlatform(
      await _channel.invokeMapMethod<String, Object?>('waitForCallback'),
    );
  }

  @override
  Future<BangumiAuthCallback?> takePendingCallback() async {
    final value = await _channel.invokeMapMethod<String, Object?>(
      'takePendingCallback',
    );
    if (value == null) return null;
    return _callbackFromPlatform(value);
  }

  @override
  Future<void> savePendingState({
    required String state,
    required DateTime createdAt,
  }) async {
    await _channel.invokeMethod<void>('savePendingState', <String, Object?>{
      'state': state,
      'createdAtEpochMs': createdAt.toUtc().millisecondsSinceEpoch,
    });
  }

  @override
  Future<BangumiPendingOAuthState?> loadPendingState() async {
    final value = await _channel.invokeMapMethod<String, Object?>(
      'loadPendingState',
    );
    if (value == null) return null;
    final state = value['state'];
    final epochMs = value['createdAtEpochMs'];
    if (state is! String ||
        state.isEmpty ||
        state.length > 256 ||
        epochMs is! num ||
        !epochMs.isFinite) {
      throw const BangumiApiException(code: 'oauth_state_storage_invalid');
    }
    return BangumiPendingOAuthState(
      state: state,
      createdAt: DateTime.fromMillisecondsSinceEpoch(
        epochMs.toInt(),
        isUtc: true,
      ),
    );
  }

  @override
  Future<void> clearPendingState() async {
    await _channel.invokeMethod<void>('clearPendingState');
  }

  @override
  Future<void> saveRefreshToken({
    required String accountId,
    required String refreshToken,
  }) async {
    if (!_isSafeAccountId(accountId) ||
        refreshToken.isEmpty ||
        refreshToken.length > 4096) {
      throw const BangumiApiException(code: 'oauth_session_storage_invalid');
    }
    await _channel.invokeMethod<void>('saveRefreshToken', <String, Object?>{
      'accountId': accountId,
      'refreshToken': refreshToken,
    });
  }

  @override
  Future<BangumiStoredRefreshToken?> loadRefreshToken() async {
    final value = await _channel.invokeMapMethod<String, Object?>(
      'loadRefreshToken',
    );
    if (value == null) return null;
    final accountId = value['accountId'];
    final refreshToken = value['refreshToken'];
    if (accountId is! String ||
        refreshToken is! String ||
        !_isSafeAccountId(accountId) ||
        refreshToken.isEmpty ||
        refreshToken.length > 4096) {
      throw const BangumiApiException(code: 'oauth_session_storage_invalid');
    }
    return BangumiStoredRefreshToken(
      accountId: accountId,
      refreshToken: refreshToken,
    );
  }

  @override
  Future<void> clearRefreshToken() async {
    await _channel.invokeMethod<void>('clearRefreshToken');
  }

  @override
  Future<void> close() async {}

  BangumiAuthCallback _callbackFromPlatform(Map<String, Object?>? value) {
    if (value == null || value['state'] is! String) {
      throw const BangumiApiException(code: 'callback_failed');
    }
    return BangumiAuthCallback(
      state: value['state']! as String,
      ticket: value['ticket'] as String?,
      code: value['code'] as String?,
      error: value['error'] as String?,
    );
  }

  static bool _isSafeAccountId(String value) =>
      value.isNotEmpty &&
      value.length <= 256 &&
      RegExp(r'^[0-9]+$').hasMatch(value);
}
