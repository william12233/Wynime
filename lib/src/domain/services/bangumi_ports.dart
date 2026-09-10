import '../models/bangumi_models.dart';

abstract interface class BangumiAuthenticationPort {
  Future<BangumiAuthorizationRequest> begin();

  Future<BangumiAuthSession> redeem(BangumiAuthCallback callback);

  Future<BangumiAuthSession> refresh(BangumiAuthSession session);

  Future<void> signOut();
}

abstract interface class BangumiCallbackPort {
  Future<Uri> prepareRedirectUri();

  Future<BangumiAuthCallback> waitForCallback();

  Future<void> close();
}

/// Optional platform capability for recovering a callback delivered while the
/// Flutter isolate was being recreated.
abstract interface class BangumiPendingCallbackPort {
  Future<BangumiAuthCallback?> takePendingCallback();
}

/// Stores only the short-lived OAuth state needed to complete a browser
/// handoff after an Android process restart. Implementations must never store
/// access or refresh tokens through this interface.
abstract interface class BangumiOAuthStateStore {
  Future<void> savePendingState({
    required String state,
    required DateTime createdAt,
  });

  Future<BangumiPendingOAuthState?> loadPendingState();

  Future<void> clearPendingState();
}

/// Stores the refresh token needed to restore one Bangumi account after an
/// application restart. Implementations must encrypt it at rest and must
/// never expose the access token through this interface.
abstract interface class BangumiRefreshTokenStore {
  Future<void> saveRefreshToken({
    required String accountId,
    required String refreshToken,
  });

  Future<BangumiStoredRefreshToken?> loadRefreshToken();

  Future<void> clearRefreshToken();
}

final class BangumiStoredRefreshToken {
  const BangumiStoredRefreshToken({
    required this.accountId,
    required this.refreshToken,
  });

  final String accountId;
  final String refreshToken;

  @override
  String toString() =>
      'BangumiStoredRefreshToken(accountId: $accountId, redacted)';
}

/// Optional authentication capability for restoring a session without
/// showing the browser again. Only the refresh token may be restored; the
/// access token is freshly issued by [BangumiAuthenticationPort.refresh].
abstract interface class BangumiPersistentAuthenticationPort {
  Future<BangumiAuthSession?> restoreSession();

  Future<void> clearStoredSession();
}

abstract interface class BangumiClient {
  Future<BangumiUserIdentity> currentUser();

  Future<BangumiCollectionPage> collections({int offset = 0, int limit = 30});

  Future<List<BangumiScheduleEntry>> calendar();

  Future<BangumiSubject> subject(String id);

  Future<BangumiEpisodePage> episodes(String subjectId);

  Future<BangumiRemoteState> remoteState(String subjectId);

  Future<void> setCollectionStatus(
    String subjectId,
    BangumiCollectionStatus status,
  );

  Future<void> setEpisodeWatched(
    String subjectId,
    String episodeId,
    bool watched,
  );
}
