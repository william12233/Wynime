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
import 'package:wynime/src/platform/bangumi/bangumi_runtime_configuration.dart';
import 'package:wynime/src/infrastructure/database/wynime_database.dart';
import 'package:wynime/src/infrastructure/database/wynime_database_recovery.dart';
import 'package:wynime/src/infrastructure/repositories/drift_bangumi_local_store.dart';
import 'package:wynime/src/platform/playback/media_kit_facade.dart';
import 'package:wynime/src/platform/updates/software_update_installer.dart';
import 'package:wynime/src/infrastructure/updates/software_update_service.dart';
import 'package:wynime/src/infrastructure/updates/update_startup_marker.dart';
import 'package:wynime/src/domain/services/bangumi_ports.dart';

const _bangumiClientId = 'bgm70916aa140634a4c8';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  ProductionMediaKitFacade.ensureInitialized();
  final database = WynimeDatabase.defaults();
  final store = DriftBangumiLocalStore(database);
  final databaseRecovery = DriftDatabaseRecoveryPort(database);
  final runtimeConfiguration = BangumiRuntimeConfiguration.fromEnvironment();
  final callbackPort = _createBangumiCallbackPort(runtimeConfiguration);
  final configured = runtimeConfiguration.isConfigured;
  final authentication = configured
      ? BangumiBrokerAuthentication(
          workerOrigin: runtimeConfiguration.brokerOrigin!,
          verifiedAppLinkHost: runtimeConfiguration.verifiedAppLinkHost!,
          clientId: _bangumiClientId,
          callbackPort: callbackPort,
        )
      : const UnavailableBangumiAuthentication();
  final bangumi = BangumiSessionController(
    authentication: authentication,
    store: store,
    availability: configured
        ? BangumiAvailability.available
        : BangumiAvailability.unavailable,
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

BangumiCallbackPort? _createBangumiCallbackPort(
  BangumiRuntimeConfiguration configuration,
) {
  if (!configuration.isConfigured) return null;
  if (Platform.isWindows) return WindowsLoopbackBangumiCallbackPort();
  if (Platform.isAndroid) {
    return AndroidAppLinkBangumiCallbackPort(
      redirectUri: Uri.https(
        configuration.verifiedAppLinkHost!,
        '/oauth/callback',
      ),
    );
  }
  return null;
}
