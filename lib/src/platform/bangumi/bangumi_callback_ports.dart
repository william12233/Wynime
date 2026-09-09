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

final class AndroidAppLinkBangumiCallbackPort implements BangumiCallbackPort {
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
    final value = await _channel.invokeMapMethod<String, Object?>(
      'waitForCallback',
    );
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

  @override
  Future<void> close() async {}
}
