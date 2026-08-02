import 'package:flutter/widgets.dart';
import 'package:media_kit_video/media_kit_video.dart';
import 'package:wynime/src/platform/playback/media_kit_facade.dart';

final class MpvPlayerSurface extends StatelessWidget {
  const MpvPlayerSurface({required this.player, super.key});

  final MediaKitFacadePlayer player;

  @override
  Widget build(BuildContext context) {
    final handle = player.videoControllerHandle;
    if (handle is! VideoController) {
      throw StateError('The media-kit video controller is unavailable.');
    }
    return Video(controller: handle, controls: NoVideoControls);
  }
}
