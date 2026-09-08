import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:wynime/src/domain/models/software_update_models.dart';
import 'package:wynime/src/infrastructure/updates/software_update_service.dart';

void main() {
  test(
    'selects only the ZIP asset and verifies/extracts its contents',
    () async {
      final archiveBytes = _zip(<String, String>{
        'wynime.exe': 'portable-executable',
        'wynime_update.exe': 'portable-updater',
        'flutter_windows.dll': 'flutter-runtime',
        'version.txt': '1.0.2\n',
      });
      final downloaded = <String>[];
      final client = MockClient((request) async {
        if (request.url.path.endsWith('/releases/latest')) {
          return _releaseResponse(
            version: '1.0.2',
            zipBytes: archiveBytes,
            includeInstaller: true,
          );
        }
        downloaded.add(request.url.path);
        if (request.url.path.endsWith('.sha256')) {
          return http.Response(
            '${sha256.convert(archiveBytes)}  wynime-1.0.2.zip\n',
            200,
          );
        }
        return http.Response.bytes(archiveBytes, 200);
      });
      final service = SoftwareUpdateService(
        client: client,
        versionProvider: const TestVersionProvider(
          AppVersionInfo(
            version: '1.0.1',
            buildNumber: '2',
            platform: SoftwareUpdatePlatform.windows,
            architecture: 'x64',
          ),
        ),
        platformOverride: SoftwareUpdatePlatform.windows,
        architectureOverride: 'x64',
        temporaryDirectoryProvider: () async => Directory.systemTemp,
      );
      addTearDown(service.dispose);

      final result = await service.checkForUpdates();
      final update = await service.download(result);

      expect(result.status, UpdateStatus.updateAvailable);
      expect(result.asset?.name, 'wynime-1.0.2.zip');
      expect(downloaded, contains(contains('.sha256')));
      expect(update.extractedDirectoryPath, isNotNull);
      expect(
        await File(
          '${update.extractedDirectoryPath}${Platform.pathSeparator}version.txt',
        ).readAsString(),
        '1.0.2\n',
      );
      await Directory(update.stagingDirectoryPath).delete(recursive: true);
    },
  );

  test('rejects a digest mismatch and cleans the staging directory', () async {
    final bytes = _zip(<String, String>{
      'wynime.exe': 'exe',
      'wynime_update.exe': 'updater',
      'version.txt': '1.0.2',
    });
    final service = _serviceFor(
      bytes,
      checksum: '${List.filled(64, '0').join()}  wynime-1.0.2.zip\n',
    );
    addTearDown(service.dispose);
    final result = await service.checkForUpdates();

    await expectLater(
      service.download(result),
      throwsA(
        isA<SoftwareUpdateException>().having(
          (error) => error.code,
          'code',
          'sha256_mismatch',
        ),
      ),
    );
  });

  test('rejects ZIP slip and case-collision archive entries', () async {
    for (final entries in <Map<String, String>>[
      {
        '../outside.txt': 'bad',
        'wynime.exe': 'exe',
        'wynime_update.exe': 'updater',
        'version.txt': '1.0.2',
      },
      {
        'wynime.exe': 'one',
        'WYNIME.EXE': 'two',
        'wynime_update.exe': 'updater',
        'version.txt': '1.0.2',
      },
      {
        'foo.': 'bad',
        'wynime.exe': 'exe',
        'wynime_update.exe': 'updater',
        'version.txt': '1.0.2',
      },
      {
        'foo ': 'bad',
        'wynime.exe': 'exe',
        'wynime_update.exe': 'updater',
        'version.txt': '1.0.2',
      },
    ]) {
      final bytes = _zip(entries);
      final service = _serviceFor(bytes);
      addTearDown(service.dispose);
      final result = await service.checkForUpdates();
      await expectLater(
        service.download(result),
        throwsA(
          isA<SoftwareUpdateException>().having(
            (error) => error.code,
            'code',
            anyOf('unsafe_path', 'case_collision'),
          ),
        ),
      );
    }
  });

  test('does not follow a redirect outside the allowlist', () async {
    final client = MockClient((request) async {
      expect(request.url.host, 'api.github.com');
      return http.Response(
        '',
        302,
        headers: {'location': 'https://evil.example/releases/latest'},
      );
    });
    final service = SoftwareUpdateService(
      client: client,
      versionProvider: const TestVersionProvider(
        AppVersionInfo(
          version: '1.0.1',
          buildNumber: '',
          platform: SoftwareUpdatePlatform.windows,
          architecture: 'x64',
        ),
      ),
      platformOverride: SoftwareUpdatePlatform.windows,
      architectureOverride: 'x64',
    );
    addTearDown(service.dispose);

    await expectLater(
      service.checkForUpdates(),
      throwsA(
        isA<SoftwareUpdateException>().having(
          (error) => error.code,
          'code',
          'redirect_host_not_allowed',
        ),
      ),
    );
  });
}

SoftwareUpdateService _serviceFor(Uint8List bytes, {String? checksum}) {
  final digest = sha256.convert(bytes).toString();
  final client = MockClient((request) async {
    if (request.url.path.endsWith('/releases/latest')) {
      return _releaseResponse(version: '1.0.2', zipBytes: bytes);
    }
    if (request.url.path.endsWith('.sha256')) {
      return http.Response(checksum ?? '$digest  wynime-1.0.2.zip\n', 200);
    }
    return http.Response.bytes(bytes, 200);
  });
  return SoftwareUpdateService(
    client: client,
    versionProvider: const TestVersionProvider(
      AppVersionInfo(
        version: '1.0.1',
        buildNumber: '',
        platform: SoftwareUpdatePlatform.windows,
        architecture: 'x64',
      ),
    ),
    platformOverride: SoftwareUpdatePlatform.windows,
    architectureOverride: 'x64',
    temporaryDirectoryProvider: () async => Directory.systemTemp,
  );
}

http.Response _releaseResponse({
  required String version,
  required Uint8List zipBytes,
  bool includeInstaller = false,
}) {
  final assets = <Map<String, Object>>[
    {
      'name': 'wynime-$version.zip',
      'browser_download_url':
          'https://github.com/william12233/Wynime/releases/download/v$version/wynime-$version.zip',
      'size': zipBytes.length,
    },
    {
      'name': 'wynime-$version.zip.sha256',
      'browser_download_url':
          'https://github.com/william12233/Wynime/releases/download/v$version/wynime-$version.zip.sha256',
      'size': 100,
    },
  ];
  if (includeInstaller) {
    assets.add({
      'name': 'wynime-$version-setup.exe',
      'browser_download_url':
          'https://github.com/william12233/Wynime/releases/download/v$version/wynime-$version-setup.exe',
      'size': 100,
    });
  }
  return http.Response(
    jsonEncode({
      'tag_name': 'v$version',
      'draft': false,
      'prerelease': false,
      'assets': assets,
    }),
    200,
  );
}

Uint8List _zip(Map<String, String> entries) {
  final archive = Archive();
  for (final entry in entries.entries) {
    final data = utf8.encode(entry.value);
    archive.addFile(ArchiveFile(entry.key, data.length, data));
  }
  return ZipEncoder().encodeBytes(archive);
}

final class TestVersionProvider implements AppVersionProvider {
  const TestVersionProvider(this.value);

  final AppVersionInfo value;

  @override
  Future<AppVersionInfo> load() async => value;
}
