import 'dart:async';
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:wynime/src/application/bangumi_session_controller.dart';
import 'package:wynime/src/application/source_installed_live_search_pipeline.dart';
import 'package:wynime/src/application/source_package_startup_controller.dart';
import 'package:wynime/src/application/source_registry_controller.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/application/source_live_operation_plan_factory.dart';
import 'package:wynime/src/application/source_live_search_coordinator.dart';
import 'package:wynime/src/application/updates/software_update_controller.dart';
import 'package:wynime/src/app/wynime_app.dart';
import 'package:wynime/src/infrastructure/bangumi/bangumi_api_client.dart';
import 'package:wynime/src/infrastructure/bangumi/bangumi_authentication.dart';
import 'package:wynime/src/platform/bangumi/bangumi_callback_ports.dart';
import 'package:wynime/src/platform/bangumi/bangumi_runtime_configuration.dart';
import 'package:wynime/src/infrastructure/database/wynime_database.dart';
import 'package:wynime/src/infrastructure/database/wynime_database_recovery.dart';
import 'package:wynime/src/infrastructure/repositories/drift_bangumi_local_store.dart';
import 'package:wynime/src/infrastructure/repositories/drift_source_package_repository.dart';
import 'package:wynime/src/infrastructure/repositories/drift_watch_history_repository.dart';
import 'package:wynime/src/platform/updates/software_update_installer.dart';
import 'package:wynime/src/infrastructure/updates/software_update_service.dart';
import 'package:wynime/src/infrastructure/updates/app_version_provider.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_search_normalizer.dart';
import 'package:wynime/src/infrastructure/source_rules/persistent_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_http/dart_io_source_http_transport.dart';
import 'package:wynime/src/infrastructure/source_registry/github_source_registry_repository.dart';
import 'package:wynime/src/infrastructure/source_registry/source_registry_runtime_configuration.dart';
import 'package:wynime/src/infrastructure/updates/update_startup_marker.dart';
import 'package:wynime/src/domain/services/bangumi_ports.dart';

const _bangumiClientId = 'bgm70916aa140634a4c8';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final appVersionProvider = PackageInfoAppVersionProvider();
  final appVersion = await appVersionProvider.load();
  final wynimeVersion = Version.parse(appVersion.version);
  final database = WynimeDatabase.defaults();
  final store = DriftBangumiLocalStore(database);
  final watchHistory = DriftWatchHistoryRepository(database);
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
          oauthStateStore: callbackPort is BangumiOAuthStateStore
              ? callbackPort as BangumiOAuthStateStore
              : null,
          refreshTokenStore: callbackPort is BangumiRefreshTokenStore
              ? callbackPort as BangumiRefreshTokenStore
              : null,
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
  final sourcePackages = SourcePackageStartupController(
    managerFactory: () async {
      return PersistentSourcePackageManager(
        manager: DeclarativeSourcePackageManager(wynimeVersion: wynimeVersion),
        repository: DriftSourcePackageRepository(database),
      );
    },
  );
  final sourceHttpTransport = DartIoSourceHttpTransport();
  final sourceSearchPipeline = SourceInstalledLiveSearchPipeline(
    planFactory: SourceLiveOperationPlanFactory(wynimeVersion: wynimeVersion),
    searchCoordinator: SourceLiveSearchCoordinator(
      runtime: SourceLiveHttpPackageRuntime(
        httpExecutor: SourceLiveHttpRequestExecutor(
          requestCoordinator: SourceLiveHttpRequestCoordinator(
            wynimeVersion: wynimeVersion,
          ),
          transport: sourceHttpTransport,
        ),
        fixtureRuntime: DeclarativeSourcePackageRuntime(
          wynimeVersion: wynimeVersion,
        ),
      ),
      normalizer: const DeclarativeSourceSearchNormalizer(),
    ),
  );
  final sourceRegistryConfiguration =
      SourceRegistryRuntimeConfiguration.fromEnvironment();
  final sourceRegistryRepository = sourceRegistryConfiguration == null
      ? null
      : GitHubSourceRegistryRepository(
          owner: sourceRegistryConfiguration.owner,
          repository: sourceRegistryConfiguration.repository,
          ref: sourceRegistryConfiguration.ref,
          indexPath: sourceRegistryConfiguration.indexPath,
        );
  final sourceRegistry = sourceRegistryRepository == null
      ? null
      : SourceRegistryController(
          loadCatalog: sourceRegistryRepository.loadCatalog,
          closeRepository: sourceRegistryRepository.close,
        );
  runApp(
    WynimeApp(
      bangumi: bangumi,
      sourcePackages: sourcePackages,
      sourceSearchPipeline: sourceSearchPipeline,
      sourceRegistry: sourceRegistry,
      watchHistory: watchHistory,
      softwareUpdates: softwareUpdates,
      onReady: markWindowsStartupSuccess,
      onDispose: () {
        sourceSearchPipeline.close();
        unawaited(sourceHttpTransport.close());
        unawaited(database.close());
      },
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
        '/oauth/app-callback',
      ),
    );
  }
  return null;
}
