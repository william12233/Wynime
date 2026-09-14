import 'dart:async';

import 'package:pub_semver/pub_semver.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:wynime/src/application/source_live_capture_package_coordinator.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_live_capture_package_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_security_policy.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/domain/services/source_live_capture.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test('ready admission binds exact package provenance and policy', () {
    final plan = _plan();
    final result = _coordinator().prepare(plan);

    expect(result.status, SourceLiveCapturePackageStatus.ready);
    expect(result.reasonCode, isNull);
    expect(result.request, isNotNull);
    expect(result.request!.packageId, plan.installedPackage.package.packageId);
    expect(
      result.request!.packageVersion,
      plan.installedPackage.package.version,
    );
    expect(result.request!.programId, plan.programId);
    expect(
      result.request!.webCaptureRequest.securityPolicy,
      same(plan.installedPackage.package.securityPolicy),
    );
  });

  test('consent is checked before disabled package state', () {
    final plan = _plan(
      installedPackage: _installed(
        status: SourcePackageStatus.disabled,
        requiresConsent: true,
      ),
    );

    final result = _coordinator().prepare(plan);

    expect(result.status, SourceLiveCapturePackageStatus.consentRequired);
    expect(result.reasonCode, 'consent_required');
  });

  test('disabled package cannot contact the capture port', () async {
    final port = _RecordingPort();
    final coordinator = _coordinator(port: port);

    final result = await coordinator.capture(
      _plan(installedPackage: _installed(status: SourcePackageStatus.disabled)),
    );

    expect(result.status, SourceLiveCaptureStatus.failed);
    expect(result.reasonCode, 'package_disabled');
    expect(port.requests, isEmpty);
  });

  test('incompatible package cannot contact the capture port', () async {
    final port = _RecordingPort();
    final coordinator = _coordinator(port: port);
    final package = _package(constraint: VersionConstraint.parse('^2.0.0'));

    final result = await coordinator.capture(
      _plan(installedPackage: _installed(package: package)),
    );

    expect(result.reasonCode, 'incompatible_wynime_version');
    expect(port.requests, isEmpty);
  });

  test('unknown program cannot contact the capture port', () async {
    final port = _RecordingPort();
    final coordinator = _coordinator(port: port);

    final result = await coordinator.capture(_plan(programId: 'episodes'));

    expect(result.reasonCode, 'program_not_found');
    expect(port.requests, isEmpty);
  });

  test('a broader or different WebView policy is rejected', () {
    final packagePolicy = testSourcePolicy(
      permissions: {
        SourcePermission.network,
        SourcePermission.webView,
        SourcePermission.mediaRequestInspection,
      },
    );
    final broaderPolicy = testSourcePolicy(
      permissions: {
        SourcePermission.network,
        SourcePermission.webView,
        SourcePermission.mediaRequestInspection,
        SourcePermission.cookies,
      },
    );
    final plan = _plan(
      installedPackage: _installed(package: _package(policy: packagePolicy)),
      webCaptureRequest: _webRequest(policy: broaderPolicy),
    );

    final result = _coordinator().prepare(plan);

    expect(result.status, SourceLiveCapturePackageStatus.invalidRequest);
    expect(result.reasonCode, 'capture_policy_mismatch');
  });

  test(
    'duplicate package domains cannot hide an extra caller domain',
    () async {
      final packageDomain = SourceDomainRule(
        host: 'example.com',
        includeSubdomains: true,
      );
      final callerDomain = SourceDomainRule(
        host: 'other.example',
        includeSubdomains: true,
      );
      const permissions = {
        SourcePermission.network,
        SourcePermission.webView,
        SourcePermission.mediaRequestInspection,
      };
      final packagePolicy = testSourcePolicy(
        domains: [packageDomain, packageDomain],
        permissions: permissions,
      );
      final callerPolicy = testSourcePolicy(
        domains: [packageDomain, callerDomain],
        permissions: permissions,
      );
      final port = _RecordingPort();

      final result = await _coordinator(port: port).capture(
        _plan(
          installedPackage: _installed(
            package: _package(policy: packagePolicy),
          ),
          webCaptureRequest: _webRequest(policy: callerPolicy),
        ),
      );

      expect(result.status, SourceLiveCaptureStatus.failed);
      expect(result.reasonCode, 'capture_policy_mismatch');
      expect(port.requests, isEmpty);
    },
  );

  test('ready capture delegates to the existing snapshot admission', () async {
    final port = _RecordingPort(snapshot: _snapshot(_plan().webCaptureRequest));
    final result = await _coordinator(port: port).capture(_plan());

    expect(result.status, SourceLiveCaptureStatus.captured);
    expect(result.snapshot, isNotNull);
    expect(port.requests, hasLength(1));
    expect(port.requests.single.packageId, 'example.anime');
    expect(port.requests.single.programId, 'search');
  });

  test('capture keeps platform failures typed and redacted', () async {
    final port = _RecordingPort(error: StateError('secret platform detail'));
    final result = await _coordinator(port: port).capture(_plan());

    expect(result.status, SourceLiveCaptureStatus.failed);
    expect(result.reasonCode, 'capture_failed');
    expect(result.toString(), isNot(contains('secret')));
  });

  test('close delegates invalidation and closed capture is truthful', () async {
    final pending = Completer<WebCaptureSnapshot>();
    final port = _RecordingPort(pending: pending);
    final coordinator = _coordinator(port: port);
    final future = coordinator.capture(_plan());
    coordinator.close();
    pending.complete(_snapshot(_plan().webCaptureRequest));

    final result = await future;

    expect(result.status, SourceLiveCaptureStatus.closed);
    expect(result.reasonCode, 'capture_closed');
  });
}

SourceLiveCapturePackageCoordinator _coordinator({
  SourceLiveCapturePort? port,
}) => SourceLiveCapturePackageCoordinator(
  wynimeVersion: Version.parse('1.0.0'),
  port: port ?? _RecordingPort(snapshot: _snapshot(_plan().webCaptureRequest)),
);

SourceLiveCapturePackagePlan _plan({
  String programId = 'search',
  InstalledSourcePackage? installedPackage,
  WebCaptureRequest? webCaptureRequest,
}) {
  final policy = testSourcePolicy(
    permissions: {
      SourcePermission.network,
      SourcePermission.webView,
      SourcePermission.mediaRequestInspection,
    },
  );
  return SourceLiveCapturePackagePlan(
    installedPackage: installedPackage ?? _installed(policy: policy),
    programId: programId,
    webCaptureRequest: webCaptureRequest ?? _webRequest(policy: policy),
  );
}

InstalledSourcePackage _installed({
  SourcePackageManifest? package,
  SourceSecurityPolicy? policy,
  SourcePackageStatus status = SourcePackageStatus.enabled,
  bool requiresConsent = false,
  bool requiresReconsent = false,
}) => InstalledSourcePackage(
  package: package ?? _package(policy: policy),
  status: status,
  requiresConsent: requiresConsent,
  requiresReconsent: requiresReconsent,
);

SourcePackageManifest _package({
  SourceSecurityPolicy? policy,
  VersionConstraint? constraint,
}) => SourcePackageManifest(
  schemaVersion: 1,
  packageId: 'example.anime',
  displayName: 'Example Anime',
  version: Version.parse('1.0.0'),
  wynimeVersionConstraint: constraint ?? VersionConstraint.parse('^1.0.0'),
  securityPolicy: policy ?? testSourcePolicy(),
  programs: [_program()],
);

SourceRuleProgram _program() => SourceRuleProgram(
  programId: 'search',
  documentKind: SourceDocumentKind.html,
  rootSelector: SourceSelector(
    kind: SourceSelectorKind.css,
    expression: '.item',
  ),
  fields: [SourceFieldRule(name: 'title', valueKind: SourceValueKind.raw)],
  resultLimit: 1,
);

WebCaptureRequest _webRequest({required SourceSecurityPolicy policy}) =>
    WebCaptureRequest(
      initialUri: Uri.parse('https://example.com/search'),
      securityPolicy: policy,
      budget: WebCaptureBudget(
        maxEvents: 20,
        maxCandidates: 5,
        maxHeaderBytes: 4096,
        maxCookieBytes: 0,
      ),
      userAgentPolicy: WebUserAgentPolicy(
        mode: WebUserAgentMode.platformDefault,
      ),
      captureMediaRequests: true,
    );

WebCaptureSnapshot _snapshot(WebCaptureRequest request) => WebCaptureSnapshot(
  events: [
    WebCaptureEvent(
      sequence: 0,
      kind: WebRequestKind.navigation,
      uri: request.initialUri,
      isMainFrame: true,
    ),
  ],
  candidates: const [],
  cookies: const [],
  stopReason: WebCaptureStopReason.completed,
  finalUri: request.initialUri,
);

final class _RecordingPort implements SourceLiveCapturePort {
  _RecordingPort({this.snapshot, this.error, this.pending});

  final WebCaptureSnapshot? snapshot;
  final Object? error;
  final Completer<WebCaptureSnapshot>? pending;
  final requests = <SourceLiveCaptureRequest>[];

  @override
  Future<WebCaptureSnapshot> capture(SourceLiveCaptureRequest request) {
    requests.add(request);
    final failure = error;
    if (failure != null) return Future<WebCaptureSnapshot>.error(failure);
    final waiting = pending;
    if (waiting != null) return waiting.future;
    final value = snapshot;
    if (value == null) {
      return Future<WebCaptureSnapshot>.error(
        StateError('test port has no snapshot'),
      );
    }
    return Future<WebCaptureSnapshot>.value(value);
  }
}
