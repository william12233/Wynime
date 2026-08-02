import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/infrastructure/playback/hls_playlist_rewriter.dart';

void main() {
  const rewriter = HlsPlaylistRewriter();

  test('rewrites media lines and quoted or unquoted URI attributes', () {
    final registered = <Uri>[];
    Uri register(Uri uri) {
      registered.add(uri);
      return Uri.parse(
        'http://127.0.0.1:41000/v1/session/token/resource/${registered.length}',
      );
    }

    final output = rewriter.rewrite(
      playlist: '''
#EXTM3U
#EXT-X-KEY:METHOD=AES-128,URI="keys/key.bin"
#EXT-X-MAP:URI=init.mp4
#EXTINF:6,
segments/one.ts?token=secret
#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=1000,URI="iframe.m3u8"
''',
      baseUri: Uri.parse('https://media.example/path/master.m3u8'),
      register: register,
    );

    expect(registered, [
      Uri.parse('https://media.example/path/keys/key.bin'),
      Uri.parse('https://media.example/path/init.mp4'),
      Uri.parse('https://media.example/path/segments/one.ts?token=secret'),
      Uri.parse('https://media.example/path/iframe.m3u8'),
    ]);
    expect(output, isNot(contains('token=secret')));
    expect(output, contains('http://127.0.0.1:41000/'));
    expect(output, endsWith('\n'));
  });

  test('rejects non-HLS input and non-HTTP resources', () {
    expect(
      () => rewriter.rewrite(
        playlist: 'segment.ts\n',
        baseUri: Uri.parse('https://media.example/master.m3u8'),
        register: (uri) => uri,
      ),
      throwsFormatException,
    );
    expect(
      () => rewriter.rewrite(
        playlist: '#EXTM3U\nfile:///tmp/segment.ts\n',
        baseUri: Uri.parse('https://media.example/master.m3u8'),
        register: (uri) => uri,
      ),
      throwsFormatException,
    );
  });
}
