import 'package:crypto/crypto.dart';

/// The five collection states documented by the Bangumi v0 API.
enum BangumiCollectionStatus {
  wish(1),
  completed(2),
  watching(3),
  onHold(4),
  dropped(5);

  const BangumiCollectionStatus(this.apiType);

  final int apiType;

  static BangumiCollectionStatus fromApiType(int value) {
    return switch (value) {
      1 => BangumiCollectionStatus.wish,
      2 => BangumiCollectionStatus.completed,
      3 => BangumiCollectionStatus.watching,
      4 => BangumiCollectionStatus.onHold,
      5 => BangumiCollectionStatus.dropped,
      _ => throw const BangumiPayloadException('unsupported_collection_type'),
    };
  }
}

final class BangumiAuthorizationRequest {
  const BangumiAuthorizationRequest({
    required this.authorizationUri,
    required this.state,
    required this.redirectUri,
  });

  final Uri authorizationUri;
  final String state;
  final Uri redirectUri;
}

final class BangumiAuthCallback {
  const BangumiAuthCallback({
    required this.state,
    this.code,
    this.ticket,
    this.error,
  });

  final String state;
  final String? code;
  final String? ticket;
  final String? error;

  bool get isError => error != null;
}

/// The only OAuth value allowed to survive an Android process restart.
///
/// This is a short-lived CSRF handoff value, not an access or refresh token.
final class BangumiPendingOAuthState {
  const BangumiPendingOAuthState({
    required this.state,
    required this.createdAt,
  });

  final String state;
  final DateTime createdAt;
}

/// Access tokens are deliberately kept only in memory. Refresh tokens may be
/// kept in a platform-provided encrypted store so a session can be restored
/// after an application restart.
final class BangumiAuthSession {
  const BangumiAuthSession({
    required this.accountId,
    required this.accessToken,
    required this.refreshToken,
    required this.expiresAt,
  });

  final String accountId;
  final String accessToken;
  final String refreshToken;
  final DateTime expiresAt;

  bool get isExpired => !expiresAt.isAfter(DateTime.now().toUtc());

  BangumiAuthSession copyWith({
    String? accountId,
    String? accessToken,
    String? refreshToken,
    DateTime? expiresAt,
  }) {
    return BangumiAuthSession(
      accountId: accountId ?? this.accountId,
      accessToken: accessToken ?? this.accessToken,
      refreshToken: refreshToken ?? this.refreshToken,
      expiresAt: expiresAt ?? this.expiresAt,
    );
  }

  @override
  String toString() => 'BangumiAuthSession(accountId: $accountId, redacted)';
}

final class BangumiUserIdentity {
  const BangumiUserIdentity({
    required this.id,
    required this.username,
    this.nickname,
    this.avatarUrl,
  });

  final String id;
  final String username;
  final String? nickname;
  final Uri? avatarUrl;
}

final class BangumiScheduleEntry {
  const BangumiScheduleEntry({
    required this.id,
    required this.subjectId,
    required this.subjectName,
    required this.airWeekday,
    this.airDate,
    this.episodeNumber,
    this.imageUrl,
  });

  final String id;
  final String subjectId;
  final String subjectName;
  final int airWeekday;
  final DateTime? airDate;
  final double? episodeNumber;
  final Uri? imageUrl;
}

final class BangumiSubject {
  const BangumiSubject({
    required this.id,
    required this.name,
    required this.nameCn,
    required this.summary,
    required this.eps,
    this.imageUrl,
  });

  final String id;
  final String name;
  final String nameCn;
  final String summary;
  final int? eps;
  final Uri? imageUrl;
}

final class BangumiEpisode {
  const BangumiEpisode({
    required this.id,
    required this.subjectId,
    required this.name,
    required this.nameCn,
    required this.sort,
    required this.type,
    this.duration,
  });

  final String id;
  final String subjectId;
  final String name;
  final String nameCn;
  final double sort;
  final int type;
  final int? duration;
}

final class BangumiEpisodePage {
  const BangumiEpisodePage({
    required this.episodes,
    required this.offset,
    required this.limit,
    required this.total,
  });

  final List<BangumiEpisode> episodes;
  final int offset;
  final int limit;
  final int total;

  bool get hasMore => offset + episodes.length < total;
}

final class BangumiCollectionEntry {
  const BangumiCollectionEntry({
    required this.subjectId,
    required this.status,
    this.name,
    this.nameCn,
    this.imageUrl,
  });

  final String subjectId;
  final BangumiCollectionStatus status;
  final String? name;
  final String? nameCn;
  final Uri? imageUrl;
}

final class BangumiCollectionPage {
  const BangumiCollectionPage({
    required this.collections,
    required this.offset,
    required this.limit,
    required this.total,
  });

  final List<BangumiCollectionEntry> collections;
  final int offset;
  final int limit;
  final int total;

  bool get hasMore => offset + collections.length < total;
}

final class BangumiRemoteState {
  const BangumiRemoteState({
    required this.accountId,
    required this.subjectId,
    required this.status,
    required this.watchedEpisodeIds,
    required this.remoteRevision,
  });

  final String accountId;
  final String subjectId;
  final BangumiCollectionStatus? status;
  final Set<String> watchedEpisodeIds;
  final String remoteRevision;

  static String fingerprint({
    required String subjectId,
    required BangumiCollectionStatus? status,
    required Iterable<String> watchedEpisodeIds,
  }) {
    final payload = [
      subjectId,
      status?.apiType.toString() ?? 'null',
      ...watchedEpisodeIds.toList()..sort(),
    ].join('|');
    return sha256.convert(payload.codeUnits).toString();
  }
}

final class BangumiPayloadException implements Exception {
  const BangumiPayloadException(this.code);

  final String code;

  @override
  String toString() => 'BangumiPayloadException($code)';
}

final class BangumiApiException implements Exception {
  const BangumiApiException({
    required this.code,
    this.statusCode,
    this.retryable = false,
  });

  final String code;
  final int? statusCode;
  final bool retryable;

  @override
  String toString() =>
      'BangumiApiException(code: $code, statusCode: $statusCode, '
      'retryable: $retryable)';
}
