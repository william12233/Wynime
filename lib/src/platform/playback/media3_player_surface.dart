import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Displays the existing native Media3 ExoPlayer through a PlatformView.
/// No Dart-side player is created and the surface receives only the already
/// opened session owned by the playback coordinator.
final class Media3PlayerSurface extends StatelessWidget {
  const Media3PlayerSurface({super.key});

  @override
  Widget build(BuildContext context) {
    return const AndroidView(
      viewType: 'wynime/media3-player',
      creationParams: <String, Object?>{},
      creationParamsCodec: StandardMessageCodec(),
    );
  }
}
