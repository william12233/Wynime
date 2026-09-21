import 'dart:collection';

import 'package:pub_semver/pub_semver.dart';

enum SourceCacheStage {
  search,
  subjectMetadata,
  episodeList,
  playbackResolution,
}

final class SourceCachePolicy {
  SourceCachePolicy({
    this.searchTtl = Duration.zero,
    this.subjectMetadataTtl = Duration.zero,
    this.episodeListTtl = Duration.zero,
    this.playbackResolutionTtl = Duration.zero,
  }) {
    _validate(searchTtl, 'searchTtl');
    _validate(subjectMetadataTtl, 'subjectMetadataTtl');
    _validate(episodeListTtl, 'episodeListTtl');
    _validate(playbackResolutionTtl, 'playbackResolutionTtl');
  }

  static const maxTtl = Duration(days: 7);

  final Duration searchTtl;
  final Duration subjectMetadataTtl;
  final Duration episodeListTtl;
  final Duration playbackResolutionTtl;

  SourceCachePolicy.zero() : this();

  Duration ttlFor(SourceCacheStage stage) {
    return switch (stage) {
      SourceCacheStage.search => searchTtl,
      SourceCacheStage.subjectMetadata => subjectMetadataTtl,
      SourceCacheStage.episodeList => episodeListTtl,
      SourceCacheStage.playbackResolution => playbackResolutionTtl,
    };
  }

  SourceCachePolicy copyWith({
    Duration? searchTtl,
    Duration? subjectMetadataTtl,
    Duration? episodeListTtl,
    Duration? playbackResolutionTtl,
  }) {
    return SourceCachePolicy(
      searchTtl: searchTtl ?? this.searchTtl,
      subjectMetadataTtl: subjectMetadataTtl ?? this.subjectMetadataTtl,
      episodeListTtl: episodeListTtl ?? this.episodeListTtl,
      playbackResolutionTtl:
          playbackResolutionTtl ?? this.playbackResolutionTtl,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SourceCachePolicy &&
          searchTtl == other.searchTtl &&
          subjectMetadataTtl == other.subjectMetadataTtl &&
          episodeListTtl == other.episodeListTtl &&
          playbackResolutionTtl == other.playbackResolutionTtl;

  @override
  int get hashCode => Object.hash(
    searchTtl,
    subjectMetadataTtl,
    episodeListTtl,
    playbackResolutionTtl,
  );

  static void _validate(Duration value, String name) {
    if (value < Duration.zero ||
        value > maxTtl ||
        value.inMicroseconds % 1000000 != 0) {
      throw ArgumentError.value(
        value,
        name,
        'Must be a whole-second duration between zero and $maxTtl.',
      );
    }
  }
}

final class SourceCacheKey {
  SourceCacheKey({
    required String sourceId,
    required this.packageVersion,
    required this.stage,
    required String identity,
  }) : sourceId = _bounded(sourceId, 'sourceId', 128),
       identity = _bounded(identity, 'identity', 512) {
    if (identity.contains('\u0000')) {
      throw ArgumentError.value(identity, 'identity', 'Must not contain NUL.');
    }
  }

  final String sourceId;
  final Version packageVersion;
  final SourceCacheStage stage;
  final String identity;

  String get stableKey =>
      '$sourceId@${packageVersion.toString()}/${stage.name}/$identity';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SourceCacheKey && stableKey == other.stableKey;

  @override
  int get hashCode => stableKey.hashCode;

  static String _bounded(String value, String name, int maxLength) {
    final normalized = value.trim();
    if (normalized.isEmpty ||
        normalized.length > maxLength ||
        normalized != value) {
      throw ArgumentError.value(
        value,
        name,
        'Must be a trimmed non-empty value of at most $maxLength characters.',
      );
    }
    return normalized;
  }
}

final class SourceCacheEntry<T> {
  SourceCacheEntry({
    required this.key,
    required this.value,
    required this.createdAt,
    required this.expiresAt,
  }) {
    if (!expiresAt.isAfter(createdAt)) {
      throw ArgumentError.value(
        expiresAt,
        'expiresAt',
        'An entry must expire after it is created.',
      );
    }
  }

  final SourceCacheKey key;
  final T value;
  final DateTime createdAt;
  final DateTime expiresAt;

  bool isExpired(DateTime now) => !expiresAt.isAfter(now);
}

UnmodifiableMapView<String, Object?> sourceCachePolicyWireValues(
  SourceCachePolicy policy,
) {
  return UnmodifiableMapView({
    'searchTtlSeconds': policy.searchTtl.inSeconds,
    'subjectMetadataTtlSeconds': policy.subjectMetadataTtl.inSeconds,
    'episodeListTtlSeconds': policy.episodeListTtl.inSeconds,
    'playbackResolutionTtlSeconds': policy.playbackResolutionTtl.inSeconds,
  });
}
