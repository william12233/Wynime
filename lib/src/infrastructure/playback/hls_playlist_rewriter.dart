typedef HlsResourceRegistrar = Uri Function(Uri upstreamUri);

final class HlsPlaylistRewriter {
  const HlsPlaylistRewriter();

  String rewrite({
    required String playlist,
    required Uri baseUri,
    required HlsResourceRegistrar register,
  }) {
    final normalized = playlist.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
    final lines = normalized.split('\n');
    final firstContent = lines
        .map((line) => line.trim())
        .firstWhere((line) => line.isNotEmpty, orElse: () => '');
    if (firstContent.replaceFirst('\uFEFF', '') != '#EXTM3U') {
      throw const FormatException('HLS playlist must begin with #EXTM3U.');
    }

    final output = <String>[];
    for (final line in lines) {
      final trimmed = line.trim();
      if (trimmed.isEmpty) {
        output.add(line);
        continue;
      }
      if (trimmed.startsWith('#')) {
        output.add(_rewriteUriAttributes(line, baseUri, register));
        continue;
      }
      final upstream = _resolveHttpUri(baseUri, trimmed);
      output.add(register(upstream).toString());
    }
    return '${output.join('\n').replaceFirst(RegExp(r'\n+$'), '')}\n';
  }
}

String _rewriteUriAttributes(
  String line,
  Uri baseUri,
  HlsResourceRegistrar register,
) {
  var result = line.replaceAllMapped(RegExp(r'URI="([^"]+)"'), (match) {
    final upstream = _resolveHttpUri(baseUri, match.group(1)!);
    return 'URI="${register(upstream)}"';
  });

  result = result.replaceAllMapped(RegExp(r'URI=([^",][^,]*)'), (match) {
    final upstream = _resolveHttpUri(baseUri, match.group(1)!.trim());
    return 'URI=${register(upstream)}';
  });
  return result;
}

Uri _resolveHttpUri(Uri baseUri, String reference) {
  final resolved = baseUri.resolve(reference.trim());
  if ((resolved.scheme != 'https' && resolved.scheme != 'http') ||
      resolved.host.isEmpty ||
      resolved.userInfo.isNotEmpty) {
    throw const FormatException(
      'HLS resource URI is not an allowed HTTP(S) URI.',
    );
  }
  return resolved;
}
