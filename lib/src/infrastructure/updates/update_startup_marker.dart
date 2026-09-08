import 'dart:io';

import 'package:path/path.dart' as path;

const windowsUpdateStartupMarker = 'update-startup-success.marker';

Future<void> markWindowsStartupSuccess() async {
  if (!Platform.isWindows) return;
  try {
    final marker = File(
      path.join(
        File(Platform.resolvedExecutable).parent.path,
        windowsUpdateStartupMarker,
      ),
    );
    await marker.writeAsString(DateTime.now().toUtc().toIso8601String());
  } on Object {
    // A read-only portable directory must not prevent the application from
    // starting. The helper treats a missing marker as a failed health check.
  }
}
