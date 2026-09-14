import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_http_package_runtime.dart';
import 'package:wynime/src/application/source_live_http_request_coordinator.dart';
import 'package:wynime/src/application/source_live_http_request_executor.dart';
import 'package:wynime/src/domain/models/source_http_models.dart';
import 'package:wynime/src/domain/models/source_package_manager_models.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/services/source_http_transport.dart';
import 'package:wynime/src/domain/services/source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test(
    'live response is evaluated by the existing declarative runtime',
    () async {
      final package = _package();
      final request = _request();
      final transport = _RecordingTransport(
        result: _success(
          request,
          body: '<article class="item"><h2>Alpha</h2></article>',
        ),
      );
      final subject = _subject(
        transport: transport,
        fixtureRuntime: DeclarativeSourcePackageRuntime(
          wynimeVersion: Version.parse('1.0.0'),
        ),
      );

      final result = await subject.execute(
        _plan(package: package, request: request),
      );

      expect(result.status, SourceRuntimeStatus.available);
      expect(result.records.single.values['title'], 'Alpha');
      expect(result.diagnostics, isEmpty);
      expect(transport.requests, [same(request)]);
      expect(result.toString(), isNot(contains('<article')));
    },
  );

  test(
    'response is projected into one fixture with no raw response escape',
    () async {
      final package = _package();
      final request = _request();
      final redirect = Uri.parse('https://example.com/redirect');
      final response = SourceHttpResponse(
        statusCode: 200,
        finalUri: redirect,
        redirectChain: [redirect],
        body: 'raw-response-secret',
      );
      final runtime = _RecordingRuntime(_matchingResult(package));
      final transport = _RecordingTransport(
        result: SourceHttpTransportResult(
          status: SourceHttpTransportStatus.success,
          response: response,
        ),
      );

      final result = await _subject(
        transport: transport,
        fixtureRuntime: runtime,
      ).execute(_plan(package: package, request: request));

      expect(result.status, SourceRuntimeStatus.available);
      expect(runtime.calls, 1);
      expect(runtime.fixture, isNotNull);
      expect(runtime.fixture!.initialUri, request.uri);
      expect(runtime.fixture!.redirectChain, [redirect]);
      expect(runtime.fixture!.body, response.body);
      expect(result.toString(), isNot(contains(response.body)));
    },
  );

  test('admission failures short-circuit transport and runtime', () async {
    final package = _package();
    final transport = _RecordingTransport(
      result: _success(_request(), body: 'unused'),
    );
    final runtime = _RecordingRuntime(_matchingResult(package));

    final result = await _subject(transport: transport, fixtureRuntime: runtime)
        .execute(
          _plan(
            package: package,
            installedPackage: _installed(
              package,
              status: SourcePackageStatus.disabled,
            ),
          ),
        );

    expect(result.status, SourceRuntimeStatus.disabled);
    expect(result.diagnostics.single.code, 'package_disabled');
    expect(result.records, isEmpty);
    expect(transport.requests, isEmpty);
    expect(runtime.calls, 0);
  });

  test('transport failures become safe typed runtime failures', () async {
    final package = _package();
    final transport = _RecordingTransport(
      result: SourceHttpTransportResult(
        status: SourceHttpTransportStatus.httpError,
        reasonCode: 'http_status_429',
        httpStatus: 429,
      ),
    );
    final runtime = _RecordingRuntime(_matchingResult(package));

    final result = await _subject(
      transport: transport,
      fixtureRuntime: runtime,
    ).execute(_plan(package: package));

    expect(result.status, SourceRuntimeStatus.failed);
    expect(result.diagnostics.single.code, 'http_status_429');
    expect(
      result.diagnostics.single.message,
      'The live source returned an HTTP error.',
    );
    expect(result.records, isEmpty);
    expect(runtime.calls, 0);
    expect(result.toString(), isNot(contains('429 secret')));
  });

  test(
    'runtime exceptions are collapsed without exposing their details',
    () async {
      final package = _package();
      final runtime = _RecordingRuntime(
        _matchingResult(package),
        error: StateError('private-response-secret'),
      );

      final result = await _subject(
        transport: _RecordingTransport(
          result: _success(_request(), body: 'private-response-secret'),
        ),
        fixtureRuntime: runtime,
      ).execute(_plan(package: package));

      expect(result.status, SourceRuntimeStatus.failed);
      expect(result.diagnostics.single.code, 'source_live_runtime_failed');
      expect(result.records, isEmpty);
      expect(result.toString(), isNot(contains('private-response-secret')));
    },
  );

  test('forged evaluator identity fails closed and discards records', () async {
    final package = _package();
    final forged = SourceRuntimeResult(
      packageId: 'other.package',
      packageVersion: package.version,
      programId: 'search',
      status: SourceRuntimeStatus.available,
      records: [
        SourceRuntimeRecord({'title': 'forged'}),
      ],
      diagnostics: const [],
      consumedSteps: 1,
      selectorMatches: 1,
    );

    final result = await _subject(
      transport: _RecordingTransport(
        result: _success(_request(), body: 'ignored'),
      ),
      fixtureRuntime: _RecordingRuntime(forged),
    ).execute(_plan(package: package));

    expect(result.status, SourceRuntimeStatus.failed);
    expect(result.diagnostics.single.code, 'runtime_identity_mismatch');
    expect(result.records, isEmpty);
  });
}

SourceLiveHttpPackageRuntime _subject({
  required SourceHttpTransport transport,
  required SourcePackageRuntime fixtureRuntime,
}) {
  return SourceLiveHttpPackageRuntime(
    httpExecutor: SourceLiveHttpRequestExecutor(
      requestCoordinator: SourceLiveHttpRequestCoordinator(
        wynimeVersion: Version.parse('1.0.0'),
      ),
      transport: transport,
    ),
    fixtureRuntime: fixtureRuntime,
  );
}

SourceLiveHttpRequestPlan _plan({
  SourcePackageManifest? package,
  InstalledSourcePackage? installedPackage,
  SourceHttpRequest? request,
}) {
  final resolvedPackage = package ?? _package();
  return SourceLiveHttpRequestPlan(
    installedPackage: installedPackage ?? _installed(resolvedPackage),
    programId: 'search',
    request: request ?? _request(),
  );
}

SourceHttpRequest _request() => SourceHttpRequest(
  uri: Uri.parse('https://example.com/search?q=secret'),
  securityPolicy: testSourcePolicy(),
  headers: const {'accept': 'text/html'},
  timeout: const Duration(seconds: 2),
);

SourceHttpTransportResult _success(
  SourceHttpRequest request, {
  required String body,
}) => SourceHttpTransportResult(
  status: SourceHttpTransportStatus.success,
  response: SourceHttpResponse(
    statusCode: 200,
    finalUri: request.uri,
    redirectChain: const [],
    body: body,
  ),
);

InstalledSourcePackage _installed(
  SourcePackageManifest package, {
  SourcePackageStatus status = SourcePackageStatus.enabled,
}) => InstalledSourcePackage(
  package: package,
  status: status,
  requiresConsent: false,
  requiresReconsent: false,
);

SourcePackageManifest _package() => SourcePackageManifest(
  schemaVersion: 1,
  packageId: 'example.anime',
  displayName: 'Example Anime',
  version: Version.parse('1.0.0'),
  wynimeVersionConstraint: VersionConstraint.parse('^1.0.0'),
  securityPolicy: testSourcePolicy(),
  programs: [
    SourceRuleProgram(
      programId: 'search',
      documentKind: SourceDocumentKind.html,
      rootSelector: SourceSelector(
        kind: SourceSelectorKind.css,
        expression: '.item',
      ),
      fields: [
        SourceFieldRule(
          name: 'title',
          valueKind: SourceValueKind.text,
          required: true,
          selector: SourceSelector(
            kind: SourceSelectorKind.css,
            expression: 'h2',
          ),
        ),
      ],
      resultLimit: 10,
    ),
  ],
);

SourceRuntimeResult _matchingResult(SourcePackageManifest package) =>
    SourceRuntimeResult(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: 'search',
      status: SourceRuntimeStatus.available,
      records: [
        SourceRuntimeRecord({'title': 'Alpha'}),
      ],
      diagnostics: const [],
      consumedSteps: 1,
      selectorMatches: 1,
    );

final class _RecordingTransport implements SourceHttpTransport {
  _RecordingTransport({required this.result});

  final SourceHttpTransportResult result;
  final requests = <SourceHttpRequest>[];

  @override
  Future<SourceHttpTransportResult> send(SourceHttpRequest request) async {
    requests.add(request);
    return result;
  }

  @override
  Future<void> close() async {}
}

final class _RecordingRuntime implements SourcePackageRuntime {
  _RecordingRuntime(this.result, {this.error});

  final SourceRuntimeResult result;
  final Object? error;
  SourceFixture? fixture;
  var calls = 0;

  @override
  SourceRuntimeResult executeFixture({
    required InstalledSourcePackage installedPackage,
    required String programId,
    required SourceFixture fixture,
  }) {
    calls++;
    this.fixture = fixture;
    final failure = error;
    if (failure != null) {
      throw failure;
    }
    return result;
  }
}
