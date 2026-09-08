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
