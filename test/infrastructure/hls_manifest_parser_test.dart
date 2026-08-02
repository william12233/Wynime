import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/hls_manifest.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_parser.dart';

void main() {
  const parser = HlsManifestParser();

  test('parses bounded master playlist variants and renditions', () {
    final playlist = parser.parse(
      source: _fixture('master.m3u8'),
      sourceUri: Uri.parse('https://media.example/root/master.m3u8'),
    );

    expect(playlist, isA<HlsMasterPlaylist>());
    final master = playlist as HlsMasterPlaylist;
    expect(master.version, 7);
    expect(master.independentSegments, isTrue);
    expect(master.variants, hasLength(2));
    expect(master.variants.first.bandwidth, 1600000);
    expect(master.variants.first.resolution, const HlsResolution(1920, 1080));
    expect(master.variants.first.uri.host, 'media.example');
    expect(master.renditions.single.name, '繁中');
  });

  test(
    'parses media sequence, effective crypto context and explicit cue segments',
    () {
      final playlist =
          parser.parse(
                source: _fixture('cue_ads.m3u8'),
                sourceUri: Uri.parse('https://media.example/show/master.m3u8'),
              )
              as HlsMediaPlaylist;

      expect(playlist.mediaSequence, 100);
      expect(playlist.endList, isTrue);
      expect(playlist.playlistType, HlsPlaylistType.vod);
      expect(playlist.segments, hasLength(6));
      expect(playlist.segments[2].explicitAdCue, isTrue);
      expect(playlist.segments[3].explicitAdCue, isTrue);
      expect(playlist.segments[4].explicitAdCue, isFalse);
      expect(playlist.segments[2].discontinuityGroup, 1);
      expect(playlist.segments[4].discontinuityGroup, 2);
      expect(playlist.segments.first.initializationMap?.byteRange?.offset, 0);
      expect(playlist.segments.last.key?.iv, endsWith('02'));
    },
  );

  test('fails closed on unsupported low-latency semantics', () {
    expect(
      () => parser.parse(
        source: '''
#EXTM3U
#EXT-X-TARGETDURATION:2
#EXT-X-PART:DURATION=0.5,URI="part.m4s"
#EXTINF:2,
segment.m4s
''',
        sourceUri: Uri.parse('https://media.example/live.m3u8'),
      ),
      throwsA(
        isA<HlsManifestParseException>().having(
          (error) => error.code,
          'code',
          'unsupported_media_semantics',
        ),
      ),
    );
  });

  test('fails closed on mixed playlist kinds and unsafe URI authority', () {
    expect(
      () => parser.parse(
        source: '''
#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=1
variant.m3u8
#EXTINF:5,
segment.ts
''',
        sourceUri: Uri.parse('https://media.example/master.m3u8'),
      ),
      throwsA(
        isA<HlsManifestParseException>().having(
          (error) => error.code,
          'code',
          'mixed_playlist_kinds',
        ),
      ),
    );
    expect(
      () => parser.parse(
        source: '''
#EXTM3U
#EXT-X-TARGETDURATION:5
#EXTINF:5,
https://user:secret@media.example/segment.ts
''',
        sourceUri: Uri.parse('https://media.example/master.m3u8'),
      ),
      throwsA(
        isA<HlsManifestParseException>().having(
          (error) => error.code,
          'code',
          'unsafe_uri',
        ),
      ),
    );
  });

  test(
    'enforces character and segment budgets without echoing manifest content',
    () {
      const limited = HlsManifestParser(
        limits: HlsParseLimits(maxCharacters: 32, maxSegments: 1),
      );
      expect(
        () => limited.parse(
          source:
              '#EXTM3U\n#EXT-X-TARGETDURATION:5\n#EXTINF:5,\nsecret-token.ts',
          sourceUri: Uri.parse('https://media.example/a.m3u8'),
        ),
        throwsA(
          isA<HlsManifestParseException>()
              .having((error) => error.code, 'code', 'manifest_budget_exceeded')
              .having(
                (error) => error.toString(),
                'diagnostic',
                isNot(contains('secret-token')),
              ),
        ),
      );
    },
  );

  test(
    'materializes implicit BYTERANGE offsets only for the same resource',
    () {
      final playlist =
          parser.parse(
                source: '''#EXTM3U
#EXT-X-TARGETDURATION:6
#EXT-X-MEDIA-SEQUENCE:10
#EXT-X-PLAYLIST-TYPE:VOD
#EXTINF:6,
#EXT-X-BYTERANGE:100@0
media.ts
#EXTINF:6,
#EXT-X-BYTERANGE:50
media.ts
#EXT-X-ENDLIST
''',
                sourceUri: Uri.parse('https://media.example/vod/playlist.m3u8'),
              )
              as HlsMediaPlaylist;

      expect(playlist.segments[1].byteRange?.offset, 100);
      expect(playlist.segments[1].byteRange?.length, 50);

      expect(
        () => parser.parse(
          source: '''#EXTM3U
#EXT-X-TARGETDURATION:6
#EXT-X-PLAYLIST-TYPE:VOD
#EXTINF:6,
#EXT-X-BYTERANGE:100@0
first.ts
#EXTINF:6,
#EXT-X-BYTERANGE:50
second.ts
#EXT-X-ENDLIST
''',
          sourceUri: Uri.parse('https://media.example/vod/playlist.m3u8'),
        ),
        throwsA(
          isA<HlsManifestParseException>().having(
            (error) => error.code,
            'code',
            'ambiguous_byte_range_offset',
          ),
        ),
      );
    },
  );

  test('rejects ambiguous MAP ranges and malformed variant numerics', () {
    expect(
      () => parser.parse(
        source: '''#EXTM3U
#EXT-X-TARGETDURATION:6
#EXT-X-PLAYLIST-TYPE:VOD
#EXT-X-MAP:URI="init.mp4",BYTERANGE="100"
#EXTINF:6,
segment.m4s
#EXT-X-ENDLIST
''',
        sourceUri: Uri.parse('https://media.example/vod/playlist.m3u8'),
      ),
      throwsA(
        isA<HlsManifestParseException>().having(
          (error) => error.code,
          'code',
          'ambiguous_map_byte_range_offset',
        ),
      ),
    );

    expect(
      () => parser.parse(
        source: '''#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=1000,AVERAGE-BANDWIDTH=oops,FRAME-RATE=NaN
video.m3u8
''',
        sourceUri: Uri.parse('https://media.example/master.m3u8'),
      ),
      throwsA(isA<HlsManifestParseException>()),
    );
  });

  test('CUE-OUT-CONT without a preceding cue fails closed', () {
    expect(
      () => parser.parse(
        source: '''#EXTM3U
#EXT-X-TARGETDURATION:6
#EXT-X-PLAYLIST-TYPE:VOD
#EXT-X-CUE-OUT-CONT:ElapsedTime=3,Duration=12
#EXTINF:6,
segment.ts
#EXT-X-ENDLIST
''',
        sourceUri: Uri.parse('https://media.example/vod/playlist.m3u8'),
      ),
      throwsA(
        isA<HlsManifestParseException>().having(
          (error) => error.code,
          'code',
          'cue_continuation_without_start',
        ),
      ),
    );
  });

  test('pre-parses bounded ad DATERANGE records without merging cue types', () {
    final playlist =
        parser.parse(
              source: _fixture('daterange_ads.m3u8'),
              sourceUri: Uri.parse('https://media.example/show/index.m3u8'),
            )
            as HlsMediaPlaylist;

    expect(playlist.dateRanges, hasLength(1));
    expect(playlist.segments[0].adDateRangeCue, isFalse);
    expect(playlist.segments[1].adDateRangeCue, isTrue);
    expect(playlist.segments[1].explicitAdCue, isFalse);
    expect(playlist.segments[2].adDateRangeCue, isFalse);
  });

  test('rejects unsupported key formats and tags after ENDLIST', () {
    expect(
      () => parser.parse(
        source: '''#EXTM3U
#EXT-X-TARGETDURATION:6
#EXT-X-PLAYLIST-TYPE:VOD
#EXT-X-KEY:METHOD=SAMPLE-AES,URI="license.key",KEYFORMAT="com.example.drm"
#EXTINF:6,
segment.ts
#EXT-X-ENDLIST
''',
        sourceUri: Uri.parse('https://media.example/vod/playlist.m3u8'),
      ),
      throwsA(
        isA<HlsManifestParseException>().having(
          (error) => error.code,
          'code',
          'unsupported_key_method',
        ),
      ),
    );

    expect(
      () => parser.parse(
        source: '''#EXTM3U
#EXT-X-TARGETDURATION:6
#EXT-X-PLAYLIST-TYPE:VOD
#EXTINF:6,
segment.ts
#EXT-X-ENDLIST
#EXTINF:6,
late.ts
''',
        sourceUri: Uri.parse('https://media.example/vod/playlist.m3u8'),
      ),
      throwsA(
        isA<HlsManifestParseException>().having(
          (error) => error.code,
          'code',
          'content_after_endlist',
        ),
      ),
    );
  });

  test('normalizes CR-only input before enforcing the line budget', () {
    const limited = HlsManifestParser(limits: HlsParseLimits(maxLines: 4));
    expect(
      () => limited.parse(
        source:
            '#EXTM3U\r#EXT-X-TARGETDURATION:6\r#EXTINF:6,\rsegment.ts\r#EXT-X-ENDLIST',
        sourceUri: Uri.parse('https://media.example/vod/playlist.m3u8'),
      ),
      throwsA(
        isA<HlsManifestParseException>().having(
          (error) => error.code,
          'code',
          'line_count_exceeded',
        ),
      ),
    );
  });
}

String _fixture(String name) =>
    File('test/fixtures/hls/$name').readAsStringSync();
