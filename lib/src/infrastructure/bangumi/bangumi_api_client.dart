// The public constructor deliberately keeps public parameter names while the
// implementation fields remain private.
// ignore_for_file: prefer_initializing_formals

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';

import '../../domain/models/bangumi_models.dart';
import '../../domain/services/bangumi_ports.dart';

const _productionApiHost = 'api.bgm.tv';
const _debugApiHost = 'api.bgm38.tv';

final class BangumiHttpResponse {
  const BangumiHttpResponse({
    required this.statusCode,
    required this.body,
    this.headers = const <String, String>{},
  });

  final int statusCode;
  final String body;
  final Map<String, String> headers;
}

abstract interface class BangumiHttpTransport {
  Future<BangumiHttpResponse> send({
    required String method,
    required Uri uri,
    required Map<String, String> headers,
    String? body,
    required int maxResponseBytes,
  });
}

final class IoBangumiHttpTransport implements BangumiHttpTransport {
  IoBangumiHttpTransport({
    HttpClient? client,
    this.timeout = const Duration(seconds: 15),
  }) : _client = client ?? HttpClient();

  final HttpClient _client;
  final Duration timeout;

  @override
  Future<BangumiHttpResponse> send({
    required String method,
    required Uri uri,
    required Map<String, String> headers,
    String? body,
    required int maxResponseBytes,
  }) async {
    try {
      final request = await _client.openUrl(method, uri).timeout(timeout);
      request.followRedirects = false;
      headers.forEach(request.headers.set);
      if (body != null) {
        request.headers.contentType = ContentType.json;
        request.write(body);
      }
      final response = await request.close().timeout(timeout);
      final bytes = <int>[];
      await for (final chunk in response.timeout(timeout)) {
        if (bytes.length + chunk.length > maxResponseBytes) {
          throw const BangumiApiException(code: 'response_too_large');
        }
        bytes.addAll(chunk);
      }
      return BangumiHttpResponse(
        statusCode: response.statusCode,
        body: utf8.decode(bytes, allowMalformed: false),
        headers: _copyHeaders(response.headers),
      );
    } on BangumiApiException {
      rethrow;
    } on TimeoutException {
      throw const BangumiApiException(code: 'network_timeout', retryable: true);
    } on SocketException {
      throw const BangumiApiException(code: 'network_error', retryable: true);
    } on FormatException {
      throw const BangumiApiException(code: 'invalid_response_encoding');
    } catch (_) {
      throw const BangumiApiException(code: 'network_error', retryable: true);
    }
  }

  void close() => _client.close(force: true);
}

Map<String, String> _copyHeaders(HttpHeaders headers) {
  final copied = <String, String>{};
  headers.forEach((name, values) {
    copied[name.toLowerCase()] = values.join(',');
  });
  return copied;
}

final class BangumiApiClient implements BangumiClient {
  BangumiApiClient({
    required BangumiAuthSession Function() sessionProvider,
    BangumiHttpTransport? transport,
    Uri? apiOrigin,
    bool allowDebugHost = false,
    this.userAgent = 'Wynime/1.0 (+https://github.com/william12233/Wynime)',
    this.maxResponseBytes = 1024 * 1024,
  }) : _sessionProvider = sessionProvider,
       _transport = transport ?? IoBangumiHttpTransport(),
       _apiOrigin = _validateOrigin(
         apiOrigin ?? Uri.https(_productionApiHost, ''),
         allowDebugHost: allowDebugHost,
       );

  final BangumiAuthSession Function() _sessionProvider;
  final BangumiHttpTransport _transport;
  final Uri _apiOrigin;
  final String userAgent;
  final int maxResponseBytes;
  String? _username;

  @override
  Future<BangumiUserIdentity> currentUser() async {
    final object = await _getObject('/v0/me');
    final identity = BangumiUserIdentity(
      id: _requiredId(object['id']),
      username: _requiredString(object, 'username'),
      nickname: _optionalString(object['nickname']),
      avatarUrl: _avatarUrl(object['avatar']),
    );
    _username = identity.username;
    return identity;
  }

  @override
  Future<BangumiCollectionPage> collections({
    int offset = 0,
    int limit = 30,
  }) async {
    _validatePage(offset, limit);
    final username = await _currentUsername();
    final object = await _getJsonObjectOrArray(
      '/v0/users/${_safeUsername(username)}/collections',
      query: <String, String>{
        'subject_type': '2',
        'limit': '$limit',
        'offset': '$offset',
      },
    );
    final rawItems = object is List ? object : _list(object, 'data');
    final collections = <BangumiCollectionEntry>[];
    for (final raw in rawItems) {
      if (raw is! Map) {
        throw const BangumiPayloadException('collection_item_not_object');
      }
      final status = _intValue(raw['type'] ?? raw['status']);
      if (status == null) {
        throw const BangumiPayloadException('collection_status_missing');
      }
      final subject = _map(raw['subject']);
      final subjectImages = subject['images'] is Map
          ? _map(subject['images'])
          : const <dynamic, dynamic>{};
      collections.add(
        BangumiCollectionEntry(
          subjectId: _requiredId(raw['subject_id'] ?? subject['id']),
          status: BangumiCollectionStatus.fromApiType(status),
          name: _optionalString(subject['name']),
          nameCn: _optionalString(subject['name_cn']),
          imageUrl: _optionalUri(
            subjectImages['common'] ??
                subjectImages['medium'] ??
                subjectImages['large'] ??
                subjectImages['small'] ??
                subject['image'] ??
                raw['image'],
          ),
        ),
      );
    }
    final total = object is Map
        ? (_intValue(object['total']) ?? collections.length)
        : collections.length;
    return BangumiCollectionPage(
      collections: List.unmodifiable(collections),
      offset: offset,
      limit: limit,
      total: total,
    );
  }

  @override
  Future<List<BangumiScheduleEntry>> calendar() async {
    final json = await _getJsonObjectOrArray('/calendar');
    final entries = <BangumiScheduleEntry>[];
    for (final weekday in json is List ? json : _list(json, 'data')) {
      if (weekday is! Map) {
        throw const BangumiPayloadException('calendar_item_not_object');
      }
      final weekdayValue = weekday['weekday'];
      final weekdayId =
          _intValue(weekdayValue) ?? _intValue(_map(weekdayValue)['id']) ?? 0;
      final items = _list(weekday, 'items');
      for (final raw in items) {
        if (raw is! Map) {
          throw const BangumiPayloadException('calendar_entry_not_object');
        }
        // The official /calendar response puts the subject fields directly on
        // each item. Keep the nested shape as a compatibility fallback for
        // older fixtures, but never require it in production.
        final subject = raw['subject'] is Map ? _map(raw['subject']) : raw;
        final subjectId = _requiredId(raw['subject_id'] ?? subject['id']);
        entries.add(
          BangumiScheduleEntry(
            id: _requiredId(raw['id'] ?? '${subjectId}_$weekdayId'),
            subjectId: subjectId,
            subjectName:
                _optionalString(subject['name_cn']) ??
                _optionalString(subject['name']) ??
                subjectId,
            airWeekday: weekdayId,
            airDate: _optionalDate(raw['air_date'] ?? raw['date']),
            episodeNumber: _doubleValue(raw['sort'] ?? raw['ep']),
            imageUrl: _optionalUri(
              subject['images'] is Map
                  ? _map(subject['images'])['common']
                  : subject['image'] ?? raw['image'],
            ),
          ),
        );
      }
    }
    return List.unmodifiable(entries);
  }

  @override
  Future<BangumiSubject> subject(String id) async {
    final object = await _getObject('/v0/subjects/${_safeId(id)}');
    return _subjectFromJson(object);
  }

  @override
  Future<BangumiEpisodePage> episodes(String subjectId) async {
    const limit = 100;
    final safeSubjectId = _safeId(subjectId);
    final episodes = <BangumiEpisode>[];
    var offset = 0;
    var total = 0;
    for (var page = 0; page < 50; page++) {
      final object = await _getJsonObjectOrArray(
        '/v0/episodes',
        query: <String, String>{
          'subject_id': safeSubjectId,
          'limit': '$limit',
          'offset': '$offset',
        },
      );
      final rawItems = object is List ? object : _list(object, 'data');
      final currentPage = rawItems
          .map((raw) {
            if (raw is! Map) {
              throw const BangumiPayloadException('episode_item_not_object');
            }
            return _episodeFromJson(raw, subjectId);
          })
          .toList(growable: false);
      episodes.addAll(currentPage);
      total = object is Map
          ? (_intValue(object['total']) ?? episodes.length)
          : episodes.length;
      if (currentPage.isEmpty || offset + currentPage.length >= total) break;
      offset += currentPage.length;
    }
    if (episodes.length < total) {
      throw const BangumiPayloadException('pagination_limit_exceeded');
    }
    return BangumiEpisodePage(
      episodes: List.unmodifiable(episodes),
      offset: 0,
      limit: limit,
      total: total,
    );
  }

  @override
  Future<BangumiRemoteState> remoteState(String subjectId) async {
    final safeSubjectId = _safeId(subjectId);
    BangumiCollectionStatus? status;
    try {
      final username = await _currentUsername();
      final collection = await _getObject(
        '/v0/users/${_safeUsername(username)}/collections/$safeSubjectId',
      );
      final type = _intValue(collection['type'] ?? collection['status']);
      if (type != null) {
        status = BangumiCollectionStatus.fromApiType(type);
      }
    } on BangumiApiException catch (error) {
      if (error.code != 'not_found') rethrow;
    }

    final watched = <String>{};
    try {
      var offset = 0;
      for (var page = 0; page < 20; page++) {
        final object = await _getJsonObjectOrArray(
          '/v0/users/-/collections/$safeSubjectId/episodes',
          query: <String, String>{'offset': '$offset', 'limit': '100'},
        );
        final rawItems = object is List ? object : _list(object, 'data');
        for (final raw in rawItems) {
          if (raw is! Map) {
            throw const BangumiPayloadException(
              'episode_collection_not_object',
            );
          }
          final type = _intValue(raw['type'] ?? raw['status']);
          if (type == 2) {
            watched.add(
              _requiredId(raw['episode_id'] ?? _map(raw['episode'])['id']),
            );
          }
        }
        final total = object is Map
            ? (_intValue(object['total']) ?? rawItems.length)
            : rawItems.length;
        if (rawItems.isEmpty || offset + rawItems.length >= total) break;
        offset += rawItems.length;
        if (page == 19) {
          throw const BangumiPayloadException('pagination_limit_exceeded');
        }
      }
    } on BangumiApiException catch (error) {
      if (error.code != 'not_found') rethrow;
    }

    final accountId = _sessionProvider().accountId;
    return BangumiRemoteState(
      accountId: accountId,
      subjectId: safeSubjectId,
      status: status,
      watchedEpisodeIds: Set.unmodifiable(watched),
      remoteRevision: BangumiRemoteState.fingerprint(
        subjectId: safeSubjectId,
        status: status,
        watchedEpisodeIds: watched,
      ),
    );
  }

  @override
  Future<void> setCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  ) async {
    await _sendJson(
      'POST',
      '/v0/users/-/collections/${_safeId(subjectId)}',
      <String, Object?>{'type': status.apiType},
    );
  }

  @override
  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  ) async {
    _safeId(subjectId);
    await _sendJson(
      'PUT',
      '/v0/users/-/collections/-/episodes/${_safeId(episodeId)}',
      <String, Object?>{'type': watched ? 2 : 1},
    );
  }

  Future<Object?> _getJsonObjectOrArray(
    String path, {
    Map<String, String> query = const <String, String>{},
  }) async {
    final response = await _send('GET', path, query: query);
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is! Map && decoded is! List) {
        throw const BangumiPayloadException('response_not_object_or_array');
      }
      return decoded;
    } on BangumiPayloadException {
      rethrow;
    } on FormatException {
      throw const BangumiPayloadException('malformed_json');
    }
  }

  Future<Map<String, dynamic>> _getObject(String path) async {
    final decoded = await _getJsonObjectOrArray(path);
    if (decoded is! Map) {
      throw const BangumiPayloadException('response_not_object');
    }
    return Map<String, dynamic>.from(decoded);
  }

  Future<String> _currentUsername() async {
    final cached = _username;
    if (cached != null) return cached;
    return (await currentUser()).username;
  }

  Future<void> _sendJson(
    String method,
    String path,
    Map<String, Object?> body,
  ) async {
    await _send(method, path, body: jsonEncode(body));
  }

  Future<BangumiHttpResponse> _send(
    String method,
    String path, {
    Map<String, String> query = const <String, String>{},
    String? body,
  }) async {
    final session = _sessionProvider();
    if (session.isExpired) {
      throw const BangumiApiException(code: 'auth_required');
    }
    final uri = _apiOrigin.replace(
      path: '${_apiOrigin.path}$path',
      queryParameters: query.isEmpty ? null : query,
    );
    final response = await _transport.send(
      method: method,
      uri: uri,
      headers: <String, String>{
        'Accept': 'application/json',
        'Authorization': 'Bearer ${session.accessToken}',
        'User-Agent': userAgent,
      },
      body: body,
      maxResponseBytes: maxResponseBytes,
    );
    if (response.statusCode == 401) {
      throw const BangumiApiException(code: 'auth_required', statusCode: 401);
    }
    if (response.statusCode == 404) {
      throw const BangumiApiException(code: 'not_found', statusCode: 404);
    }
    if (response.statusCode == 429) {
      throw const BangumiApiException(
        code: 'rate_limited',
        statusCode: 429,
        retryable: true,
      );
    }
    if (response.statusCode >= 500) {
      throw BangumiApiException(
        code: 'remote_server_error',
        statusCode: response.statusCode,
        retryable: true,
      );
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw BangumiApiException(
        code: _httpErrorCode(response.statusCode),
        statusCode: response.statusCode,
        retryable: _isRetryableHttpStatus(response.statusCode),
      );
    }
    return response;
  }

  static String _httpErrorCode(int statusCode) {
    if (statusCode == 408) return 'http_408';
    if (statusCode == 409) return 'http_409';
    if (statusCode == 425) return 'http_425';
    if (statusCode >= 400 && statusCode < 500) return 'http_$statusCode';
    return 'http_$statusCode';
  }

  static bool _isRetryableHttpStatus(int statusCode) =>
      statusCode == 408 ||
      statusCode == 409 ||
      statusCode == 425 ||
      statusCode == 429 ||
      statusCode >= 500;

  static Uri _validateOrigin(Uri origin, {required bool allowDebugHost}) {
    final isAllowedHost =
        origin.host == _productionApiHost ||
        (allowDebugHost && origin.host == _debugApiHost);
    if (origin.scheme != 'https' ||
        origin.port != 443 ||
        !isAllowedHost ||
        origin.userInfo.isNotEmpty ||
        origin.path != '' && origin.path != '/') {
      throw ArgumentError('Bangumi API origin is not allowed.');
    }
    return Uri.https(origin.host, '');
  }

  static String _safeId(String value) {
    if (!RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(value) ||
        value.contains('\u0000')) {
      throw const BangumiPayloadException('invalid_identifier');
    }
    return value;
  }

  static String _safeUsername(String value) {
    if (value.isEmpty ||
        value.length > 128 ||
        value.codeUnits.any((code) => code < 0x21 || code == 0x7f) ||
        value.contains('/') ||
        value.contains('\\')) {
      throw const BangumiPayloadException('invalid_username');
    }
    return Uri.encodeComponent(value);
  }

  static void _validatePage(int offset, int limit) {
    if (offset < 0 || limit < 1 || limit > 100) {
      throw const BangumiPayloadException('invalid_pagination');
    }
  }

  static BangumiSubject _subjectFromJson(Map<String, dynamic> raw) {
    return BangumiSubject(
      id: _requiredId(raw['id']),
      name: _requiredString(raw, 'name'),
      nameCn: _optionalString(raw['name_cn']) ?? '',
      summary: _optionalString(raw['summary']) ?? '',
      eps: _intValue(raw['eps']),
      imageUrl: _optionalUri(
        raw['images'] is Map ? _map(raw['images'])['common'] : raw['image'],
      ),
    );
  }

  static BangumiEpisode _episodeFromJson(
    Map<dynamic, dynamic> raw,
    String subjectId,
  ) {
    return BangumiEpisode(
      id: _requiredId(raw['id']),
      subjectId: _requiredId(raw['subject_id'] ?? subjectId),
      name: _optionalString(raw['name']) ?? '',
      nameCn: _optionalString(raw['name_cn']) ?? '',
      sort: _doubleValue(raw['sort']) ?? 0,
      type: _intValue(raw['type']) ?? 0,
      duration: _intValue(raw['duration']),
    );
  }

  static List<dynamic> _list(Object? value, String key) {
    final object = _map(value);
    final list = object[key];
    if (list is! List) {
      throw BangumiPayloadException('missing_$key');
    }
    return list;
  }

  static Map<dynamic, dynamic> _map(Object? value) {
    return value is Map ? value : const <dynamic, dynamic>{};
  }

  static String _requiredId(Object? value) {
    if (value is int && value > 0) return '$value';
    if (value is String && RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(value)) {
      return value;
    }
    throw const BangumiPayloadException('id_missing');
  }

  static String _requiredString(Map<dynamic, dynamic> object, String key) {
    final value = object[key];
    if (value is String && value.isNotEmpty && value.length <= 2048) {
      return value;
    }
    throw BangumiPayloadException('${key}_missing');
  }

  static String? _optionalString(Object? value) {
    if (value is String && value.length <= 8192) return value;
    return null;
  }

  static int? _intValue(Object? value) {
    if (value is int) return value;
    if (value is num && value == value.roundToDouble()) return value.toInt();
    if (value is String) return int.tryParse(value);
    return null;
  }

  static double? _doubleValue(Object? value) {
    if (value is num) return value.toDouble();
    if (value is String) return double.tryParse(value);
    return null;
  }

  static DateTime? _optionalDate(Object? value) {
    if (value is! String) return null;
    return DateTime.tryParse(value)?.toUtc();
  }

  static Uri? _optionalUri(Object? value) {
    if (value is! String || value.length > 4096) return null;
    final uri = Uri.tryParse(value);
    if (uri == null ||
        uri.scheme != 'https' ||
        uri.userInfo.isNotEmpty ||
        uri.fragment.isNotEmpty) {
      return null;
    }
    return uri;
  }

  static Uri? _avatarUrl(Object? value) {
    if (value is Map) {
      final avatar = _map(value);
      return _optionalUri(
        avatar['large'] ?? avatar['medium'] ?? avatar['small'],
      );
    }
    return _optionalUri(value);
  }

  static String fingerprintPayload(Object value) =>
      sha256.convert(utf8.encode(jsonEncode(value))).toString();
}
