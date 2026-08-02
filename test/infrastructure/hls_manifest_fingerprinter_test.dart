import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/domain/models/hls_manifest.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_fingerprinter.dart';
import 'package:wynime/src/infrastructure/playback/hls_manifest_parser.dart';

void main() {
  const parser = HlsManifestParser();
  const fingerprinter = HlsManifestFingerprinter();

  HlsMediaPlaylist parseMedia(String query, {required String duration}) =>
      parser.parse(
            source:
                '''
#EXTM3U
#EXT-X-VERSION:7
#EXT-X-TARGETDURATION:10
#EXT-X-MEDIA-SEQUENCE:1
#EXT-X-PLAYLIST-TYPE:VOD
#EXTINF:$duration,
segment-1.ts?$query
#EXTINF:6.0,
segment-2.ts?$query
#EXT-X-ENDLIST
''',
            sourceUri: Uri.parse('https://media.example/show/index.m3u8'),
          )
          as HlsMediaPlaylist;

  test(
    'ignores volatile authorization values but retains structural identity',
    () {
      final first = parseMedia('token=first', duration: '6.0');
      final renewed = parseMedia('token=renewed', duration: '6.0');
      final changedDuration = parseMedia('token=renewed', duration: '7.0');

      final firstFingerprint = fingerprinter.fingerprint(first);
      expect(firstFingerprint.algorithm, 'sha256');
      expect(firstFingerprint.value, hasLength(64));
      expect(fingerprinter.fingerprint(renewed), firstFingerprint);
      expect(
        fingerprinter.fingerprint(changedDuration),
        isNot(firstFingerprint),
      );
    },
  );

  test('preserves segment order and byte-range identity', () {
    final normal =
        parser.parse(
              source: '''
#EXTM3U
#EXT-X-TARGETDURATION:5
#EXT-X-PLAYLIST-TYPE:VOD
#EXT-X-BYTERANGE:100@0
#EXTINF:5,
media.bin?signature=a
#EXT-X-BYTERANGE:100@100
#EXTINF:5,
media.bin?signature=b
#EXT-X-ENDLIST
''',
              sourceUri: Uri.parse('https://media.example/a.m3u8'),
            )
            as HlsMediaPlaylist;
    final changedRange =
        parser.parse(
              source: '''
#EXTM3U
#EXT-X-TARGETDURATION:5
#EXT-X-PLAYLIST-TYPE:VOD
#EXT-X-BYTERANGE:100@0
#EXTINF:5,
media.bin?signature=a
#EXT-X-BYTERANGE:101@100
#EXTINF:5,
media.bin?signature=b
#EXT-X-ENDLIST
''',
              sourceUri: Uri.parse('https://media.example/a.m3u8'),
            )
            as HlsMediaPlaylist;

    expect(
      fingerprinter.fingerprint(normal),
      isNot(fingerprinter.fingerprint(changedRange)),
    );
  });

  test('binds rendered segment metadata with unambiguous JSON framing', () {
    HlsMediaPlaylist parseTitle(String title) =>
        parser.parse(
              source:
                  '''
#EXTM3U
#EXT-X-TARGETDURATION:6
#EXT-X-PLAYLIST-TYPE:VOD
#EXTINF:6,$title
segment.ts?token=renewable
#EXT-X-ENDLIST
''',
              sourceUri: Uri.parse('https://media.example/a.m3u8'),
            )
            as HlsMediaPlaylist;

    expect(
      fingerprinter.fingerprint(parseTitle('chapter|one=alpha')),
      isNot(fingerprinter.fingerprint(parseTitle('chapter|one=beta'))),
    );
  });
}
