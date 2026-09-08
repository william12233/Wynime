import 'dart:async';
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:wynime/src/application/bangumi_session_controller.dart';
import 'package:wynime/src/application/updates/software_update_controller.dart';
import 'package:wynime/src/app/wynime_app.dart';
import 'package:wynime/src/infrastructure/bangumi/bangumi_api_client.dart';
import 'package:wynime/src/infrastructure/bangumi/bangumi_authentication.dart';
import 'package:wynime/src/platform/bangumi/bangumi_callback_ports.dart';
import 'package:wynime/src/infrastructure/database/wynime_database.dart';
import 'package:wynime/src/infrastructure/database/wynime_database_recovery.dart';
import 'package:wynime/src/infrastructure/repositories/drift_bangumi_local_store.dart';
import 'package:wynime/src/platform/playback/media_kit_facade.dart';
import 'package:wynime/src/platform/updates/software_update_installer.dart';
import 'package:wynime/src/infrastructure/updates/software_update_service.dart';
import 'package:wynime/src/infrastructure/updates/update_startup_marker.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  ProductionMediaKitFacade.ensureInitialized();
  final database = WynimeDatabase.defaults();
  final store = DriftBangumiLocalStore(database);
  final databaseRecovery = DriftDatabaseRecoveryPort(database);
  final callbackPort = Platform.isWindows
      ? WindowsLoopbackBangumiCallbackPort()
      : Platform.isAndroid
      ? AndroidAppLinkBangumiCallbackPort()
      : null;
  final authentication = BangumiBrokerAuthentication(
    workerOrigin: Uri.parse('https://auth.wynime.app'),
    clientId: 'wynime',
    callbackPort: callbackPort,
    redirectUri: callbackPort == null
        ? Uri.parse('https://auth.wynime.app/oauth/callback')
        : null,
  );
  final bangumi = BangumiSessionController(
    authentication: authentication,
    store: store,
    callbackPort: callbackPort,
    openAuthorizationUri: (uri) =>
        launchUrl(uri, mode: LaunchMode.externalApplication),
    clientFactory: (session) =>
        BangumiApiClient(sessionProvider: () => session),
  );
  final softwareUpdates = SoftwareUpdateController(
    service: SoftwareUpdateService(),
    installer: SoftwareUpdateInstaller.forCurrentPlatform(
      databaseRecovery: databaseRecovery,
    ),
  );
  runApp(
    WynimeApp(
      bangumi: bangumi,
      softwareUpdates: softwareUpdates,
      onReady: markWindowsStartupSuccess,
      onDispose: () => unawaited(database.close()),
    ),
  );
}
