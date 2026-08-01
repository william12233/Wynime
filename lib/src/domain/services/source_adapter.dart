import 'package:wynime/src/domain/models/playback_session.dart';
import 'package:wynime/src/domain/models/source_models.dart';

final class SourceCapabilities {
  const SourceCapabilities({
    required this.supportsSearch,
    required this.requiresDynamicPageResolution,
  });

  final bool supportsSearch;
  final bool requiresDynamicPageResolution;
}

abstract interface class SourceAdapter {
  String get sourceId;

  SourceCapabilities get capabilities;

  Future<List<SourceSearchResult>> search(String query);

  Future<PlaybackSession> resolve(SourceEpisode episode);
}
