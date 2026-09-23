import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/application/source_live_document_package_runtime.dart';
import 'package:wynime/src/domain/models/source_live_capture_models.dart';
import 'package:wynime/src/domain/models/source_runtime_models.dart';
import 'package:wynime/src/domain/models/web_capture_models.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_manager.dart';
import 'package:wynime/src/infrastructure/source_rules/declarative_source_package_runtime.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_decoder.dart';

void main() {
  test(
    'evaluates a browser-hydrated xifan document through the package rule',
    () {
      final package = const SourcePackageDecoder().decode(
        File('sources/xifan.wynsrc.json').readAsStringSync(),
      );
      final manager = DeclarativeSourcePackageManager(
        wynimeVersion: Version.parse('1.0.15'),
      );
      final pending = manager.install(package);
      final installed = manager.enable(
        packageId: package.packageId,
        version: pending.package.version,
        userApproved: true,
        reconsentGranted: true,
      );
      final initialUri = Uri.parse(
        'https://next.xifanacg.com/search?q=live-query',
      );
      final request = SourceLiveCaptureRequest(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: 'search',
        webCaptureRequest: WebCaptureRequest(
          initialUri: initialUri,
          securityPolicy: package.securityPolicy,
          budget: WebCaptureBudget(
            maxEvents: 32,
            maxCandidates: 1,
            maxHeaderBytes: 32 * 1024,
            maxCookieBytes: 0,
          ),
          userAgentPolicy: WebUserAgentPolicy(
            mode: WebUserAgentMode.platformDefault,
          ),
          captureMediaRequests: false,
          captureDocument: true,
          completionPolicy: WebCaptureCompletionPolicy.documentAfterLoad,
        ),
      );
      final snapshot = WebCaptureSnapshot(
        events: [
          WebCaptureEvent(
            sequence: 0,
            kind: WebRequestKind.navigation,
            uri: initialUri,
            isMainFrame: true,
          ),
        ],
        candidates: const [],
        cookies: const [],
        stopReason: WebCaptureStopReason.completed,
        finalUri: initialUri,
        documentBody:
            '<main><a href="/anime/3408">'
            '地獄模式～喜歡挑戰特殊成就的玩家在廢設定的異世界成為無雙～第二季'
            '</a></main>',
      );
      final capture = SourceLiveCaptureResult(
        packageId: package.packageId,
        packageVersion: package.version,
        programId: 'search',
        status: SourceLiveCaptureStatus.captured,
        snapshot: snapshot,
      );

      final result =
          SourceLiveDocumentPackageRuntime(
            fixtureRuntime: DeclarativeSourcePackageRuntime(
              wynimeVersion: Version.parse('1.0.15'),
            ),
          ).execute(
            installedPackage: installed,
            request: request,
            captureResult: capture,
          );

      expect(result.status, SourceRuntimeStatus.available);
      expect(result.records.single.values['subjectId'], '3408');
      expect(result.records.single.values['title'], contains('地獄模式'));
      expect(result.toString(), isNot(contains('3408')));
    },
  );

  test('document runtime rejects a capture without an accepted snapshot', () {
    final package = const SourcePackageDecoder().decode(
      File('sources/xifan.wynsrc.json').readAsStringSync(),
    );
    final manager = DeclarativeSourcePackageManager(
      wynimeVersion: Version.parse('1.0.15'),
    );
    final pending = manager.install(package);
    final installed = manager.enable(
      packageId: package.packageId,
      version: pending.package.version,
      userApproved: true,
      reconsentGranted: true,
    );
    final request = SourceLiveCaptureRequest(
      packageId: package.packageId,
      packageVersion: package.version,
      programId: 'search',
      webCaptureRequest: WebCaptureRequest(
        initialUri: Uri.parse('https://next.xifanacg.com/search?q=live-query'),
        securityPolicy: package.securityPolicy,
        budget: WebCaptureBudget(
          maxEvents: 1,
          maxCandidates: 1,
          maxHeaderBytes: 1024,
          maxCookieBytes: 0,
        ),
        userAgentPolicy: WebUserAgentPolicy(
          mode: WebUserAgentMode.platformDefault,
        ),
        captureMediaRequests: false,
        captureDocument: true,
        completionPolicy: WebCaptureCompletionPolicy.documentAfterLoad,
      ),
    );

    final result =
        SourceLiveDocumentPackageRuntime(
          fixtureRuntime: DeclarativeSourcePackageRuntime(
            wynimeVersion: Version.parse('1.0.15'),
          ),
        ).execute(
          installedPackage: installed,
          request: request,
          captureResult: SourceLiveCaptureResult(
            packageId: package.packageId,
            packageVersion: package.version,
            programId: 'search',
            status: SourceLiveCaptureStatus.failed,
            reasonCode: 'capture_failed',
          ),
        );

    expect(result.status, SourceRuntimeStatus.failed);
    expect(result.diagnostics.single.code, 'capture_failed');
  });
}
