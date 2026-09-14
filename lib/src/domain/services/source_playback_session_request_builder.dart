import '../models/ad_removal_plan.dart';
import '../models/source_package_manifest.dart';
import '../models/source_playback_route_models.dart';
import '../models/source_playback_session_request_models.dart';

abstract interface class SourcePlaybackSessionRequestBuilder {
  SourcePlaybackSessionRequestBuildResult buildRequest({
    required SourcePlaybackRoute route,
    required SourcePackageManifest package,
    required AdRemovalPlan adRemovalPlan,
    required int sourceEventSequence,
  });
}
