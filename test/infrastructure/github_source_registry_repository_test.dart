import 'dart:async';
import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:pub_semver/pub_semver.dart';
import 'package:wynime/src/domain/models/source_package_manifest.dart';
import 'package:wynime/src/domain/models/source_registry_models.dart';
import 'package:wynime/src/domain/models/source_rule_program.dart';
import 'package:wynime/src/infrastructure/source_registry/github_source_registry_repository.dart';
import 'package:wynime/src/infrastructure/source_rules/source_package_encoder.dart';
import 'package:wynime/src/infrastructure/source_rules/source_registry_index_encoder.dart';

import '../helpers/source_rule_test_support.dart';

void main() {
  test('loads the index and exact artifacts from fixed GitHub raw paths', () async {
    final alpha = _artifact('alpha.anime');
    final zeta = _artifact('zeta.anime');
    final index = _index([zeta.entry, alpha.entry]);
    final transport = _FakeTransport({
      '/william12233/Wynime/main/registry/index.json': _response(
        utf8.encode(const SourceRegistryIndexEncoder().encode(index)),
      ),
      '/william12233/Wynime/main/sources/alpha.anime/package.json': _response(
        alpha.bytes,
      ),
      '/william12233/Wynime/main/sources/zeta.anime/package.json': _response(
        zeta.bytes,
      ),
    });
    final repository = GitHubSourceRegistryRepository(
      owner: 'william12233',
      repository: 'Wynime',
      ref: 'main',
      indexPath: 'registry/index.json',
      transport: transport,
    );

    final catalog = await repository.loadCatalog();

    expect(catalog.index.revision, 'main-1');
    expect(catalog.packages.map((item) => item.entry.packageId), [
      'alpha.anime',
      'zeta.anime',
    ]);
    expect(transport.uris.map((uri) => uri.toString()), [
      'https://raw.githubusercontent.com/william12233/Wynime/main/registry/index.json',
      'https://raw.githubusercontent.com/william12233/Wynime/main/sources/alpha.anime/package.json',
      'https://raw.githubusercontent.com/william12233/Wynime/main/sources/zeta.anime/package.json',
    ]);
    expect(transport.maxResponseBytes, [
      SourceRegistryIndex.maxIndexBytes,
      256 * 1024,
      256 * 1024,
    ]);
  });

  test(
    'shares one in-flight load and permits a later explicit refresh',
    () async {
      final artifact = _artifact('example.anime');
      final index = _index([artifact.entry]);
      final transport = _FakeTransport({
        '/owner/repository/main/sources/index.json': _response(
          utf8.encode(const SourceRegistryIndexEncoder().encode(index)),
        ),
        '/owner/repository/main/sources/example.anime/package.json': _response(
          artifact.bytes,
        ),
      })..blockFirstRequest = true;
      final repository = GitHubSourceRegistryRepository(
        owner: 'owner',
        repository: 'repository',
        ref: 'main',
        transport: transport,
      );

      final first = repository.loadCatalog();
      final second = repository.loadCatalog();
      expect(identical(first, second), isTrue);
      transport.releaseFirstRequest();
      await Future.wait([first, second]);
      expect(transport.uris, hasLength(2));

      await repository.loadCatalog();
      expect(transport.uris, hasLength(4));
    },
  );

  test('stops after a malformed index without fetching artifacts', () async {
    final transport = _FakeTransport({
      '/owner/repository/main/sources/index.json': _response(
        utf8.encode('{"schemaVersion":1,"packages":['),
      ),
    });
    final repository = GitHubSourceRegistryRepository(
      owner: 'owner',
      repository: 'repository',
      ref: 'main',
      transport: transport,
    );

    await expectLater(
      repository.loadCatalog(),
      throwsA(
        isA<SourceRegistryRepositoryException>().having(
          (error) => error.code,
          'code',
          'index_format_invalid',
        ),
      ),
    );
    expect(transport.uris, hasLength(1));
  });

  test(
    'maps remote status failures without exposing response bodies',
    () async {
      const secret = 'private-response-body';
      final transport = _FakeTransport({
        '/owner/repository/main/sources/index.json': _response(
          utf8.encode(secret),
          statusCode: 404,
        ),
      });
      final repository = GitHubSourceRegistryRepository(
        owner: 'owner',
        repository: 'repository',
        ref: 'main',
        transport: transport,
      );

      try {
        await repository.loadCatalog();
        fail('expected a repository exception');
      } on SourceRegistryRepositoryException catch (error) {
        expect(error.code, 'index_not_found');
        expect(error.toString(), isNot(contains(secret)));
      }
    },
  );

  test('maps transport errors and rejects oversized index responses', () async {
    final transport = _FakeTransport({
      '/owner/repository/main/sources/index.json': _response(
        List<int>.filled(SourceRegistryIndex.maxIndexBytes + 1, 0),
      ),
    });
    final repository = GitHubSourceRegistryRepository(
      owner: 'owner',
      repository: 'repository',
      ref: 'main',
      transport: transport,
    );

    await expectLater(
      repository.loadCatalog(),
      throwsA(
        isA<SourceRegistryRepositoryException>().having(
          (error) => error.code,
          'code',
          'index_too_large',
        ),
      ),
    );

    final failingTransport = _FakeTransport({}, errorCode: 'request_timeout');
    final failingRepository = GitHubSourceRegistryRepository(
      owner: 'owner',
      repository: 'repository',
      ref: 'main',
      transport: failingTransport,
    );
    await expectLater(
      failingRepository.loadCatalog(),
      throwsA(
        isA<SourceRegistryRepositoryException>().having(
          (error) => error.code,
          'code',
          'network_timeout',
        ),
      ),
    );
  });

  test(
    'forwards package integrity failure and never returns a partial catalog',
    () async {
      final artifact = _artifact('example.anime');
      final index = _index([artifact.entry]);
      final changed = List<int>.from(artifact.bytes)..[0] ^= 1;
      final transport = _FakeTransport({
        '/owner/repository/main/sources/index.json': _response(
          utf8.encode(const SourceRegistryIndexEncoder().encode(index)),
        ),
        '/owner/repository/main/sources/example.anime/package.json': _response(
          changed,
        ),
      });
      final repository = GitHubSourceRegistryRepository(
        owner: 'owner',
        repository: 'repository',
        ref: 'main',
        transport: transport,
      );

      await expectLater(
        repository.loadCatalog(),
        throwsA(
          isA<SourceRegistryRepositoryException>().having(
            (error) => error.code,
            'code',
            'package_integrity_mismatch',
          ),
        ),
      );
    },
  );

  test('enforces a total package budget before catalog composition', () async {
    final alpha = _artifact('alpha.anime');
    final zeta = _artifact('zeta.anime');
    final index = _index([alpha.entry, zeta.entry]);
    final transport = _FakeTransport({
      '/owner/repository/main/sources/index.json': _response(
        utf8.encode(const SourceRegistryIndexEncoder().encode(index)),
      ),
      '/owner/repository/main/sources/alpha.anime/package.json': _response(
        alpha.bytes,
      ),
      '/owner/repository/main/sources/zeta.anime/package.json': _response(
        zeta.bytes,
      ),
    });
    final repository = GitHubSourceRegistryRepository(
      owner: 'owner',
      repository: 'repository',
      ref: 'main',
      transport: transport,
      maxTotalPackageBytes: alpha.bytes.length + zeta.bytes.length - 1,
    );

    await expectLater(
      repository.loadCatalog(),
      throwsA(
        isA<SourceRegistryRepositoryException>().having(
          (error) => error.code,
          'code',
          'total_artifact_limit',
        ),
      ),
    );
    expect(transport.uris, hasLength(3));
  });

  test('closes safely during a pending load and rejects future work', () async {
    final artifact = _artifact('example.anime');
    final index = _index([artifact.entry]);
    final transport = _FakeTransport({
      '/owner/repository/main/sources/index.json': _response(
        utf8.encode(const SourceRegistryIndexEncoder().encode(index)),
      ),
      '/owner/repository/main/sources/example.anime/package.json': _response(
        artifact.bytes,
      ),
    })..blockFirstRequest = true;
    final repository = GitHubSourceRegistryRepository(
      owner: 'owner',
      repository: 'repository',
      ref: 'main',
      transport: transport,
    );

    final pending = repository.loadCatalog();
    repository.close();
    transport.releaseFirstRequest();
    await expectLater(
      pending,
      throwsA(
        isA<SourceRegistryRepositoryException>().having(
          (error) => error.code,
          'code',
          'repository_closed',
        ),
      ),
    );
    await expectLater(
      repository.loadCatalog(),
      throwsA(
        isA<SourceRegistryRepositoryException>().having(
          (error) => error.code,
          'code',
          'repository_closed',
        ),
      ),
    );
  });

  test('rejects unsafe GitHub repository configuration', () {
    final invalidConfigurations = <Map<String, String>>[
      {'owner': 'owner/name', 'repository': 'repository', 'ref': 'main'},
      {'owner': 'owner', 'repository': 'repository', 'ref': 'main/../x'},
      {'owner': 'owner', 'repository': 'repository', 'ref': '/main'},
      {
        'owner': 'owner',
        'repository': 'repository',
        'ref': 'main',
        'indexPath': '../index.json',
      },
      {
        'owner': 'owner',
        'repository': 'repository',
        'ref': 'main',
        'indexPath': 'sources/index.txt',
      },
    ];

    for (final configuration in invalidConfigurations) {
      expect(
        () => GitHubSourceRegistryRepository(
          owner: configuration['owner']!,
          repository: configuration['repository']!,
          ref: configuration['ref']!,
          indexPath: configuration['indexPath'] ?? 'sources/index.json',
          transport: _FakeTransport({}),
        ),
        throwsArgumentError,
      );
    }
  });

  test(
    'HTTP transport pins the host, disables redirects and sends no secrets',
    () async {
      final client = _RecordingHttpClient(const [1, 2, 3]);
      final transport = IoSourceRegistryHttpTransport(client: client);

      final response = await transport.get(
        Uri.https(
          'raw.githubusercontent.com',
          '/owner/repository/main/sources/index.json',
        ),
        maxResponseBytes: 3,
      );

      expect(response.statusCode, 200);
      expect(response.bodyBytes, [1, 2, 3]);
      final request = client.requests.single;
      expect(request.followRedirects, isFalse);
      expect(request.maxRedirects, 0);
      expect(request.headers['accept'], 'application/octet-stream');
      expect(request.headers['user-agent'], 'Wynime-source-registry');
      expect(request.headers.keys, isNot(contains('authorization')));
      expect(request.headers.keys, isNot(contains('cookie')));
    },
  );

  test(
    'HTTP transport rejects a non-GitHub URI before opening a client',
    () async {
      final client = _RecordingHttpClient(const [1]);
      final transport = IoSourceRegistryHttpTransport(client: client);

      await expectLater(
        transport.get(
          Uri.https('example.com', '/owner/repository/main/index.json'),
          maxResponseBytes: 10,
        ),
        throwsA(
          isA<SourceRegistryTransportException>().having(
            (error) => error.code,
            'code',
            'uri_not_allowed',
          ),
        ),
      );
      expect(client.requests, isEmpty);
    },
  );

  test('HTTP transport enforces the declared response limit', () async {
    final transport = IoSourceRegistryHttpTransport(
      client: _RecordingHttpClient(const [1, 2, 3]),
    );

    await expectLater(
      transport.get(
        Uri.https(
          'raw.githubusercontent.com',
          '/owner/repository/main/sources/index.json',
        ),
        maxResponseBytes: 2,
      ),
      throwsA(
        isA<SourceRegistryTransportException>().having(
          (error) => error.code,
          'code',
          'response_too_large',
        ),
      ),
    );
  });
}

SourceRegistryHttpResponse _response(List<int> bytes, {int statusCode = 200}) {
  return SourceRegistryHttpResponse(statusCode: statusCode, bodyBytes: bytes);
}

SourceRegistryIndex _index(List<SourceRegistryEntry> entries) {
  return SourceRegistryIndex(
    schemaVersion: 1,
    sourceRoot: 'sources',
    revision: 'main-1',
    packages: entries,
  );
}

_RegistryArtifact _artifact(String packageId) {
  final package = SourcePackageManifest(
    schemaVersion: 1,
    packageId: packageId,
    displayName: packageId,
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
        resultLimit: 10,
        fields: [
          SourceFieldRule(
            name: 'title',
            valueKind: SourceValueKind.text,
            required: true,
          ),
        ],
      ),
    ],
  );
  final bytes = utf8.encode(const SourcePackageEncoder().encode(package));
  return _RegistryArtifact(
    entry: SourceRegistryEntry(
      packageId: packageId,
      version: package.version,
      packagePath: '$packageId/package.json',
      sha256: sha256.convert(bytes).toString(),
    ),
    bytes: bytes,
  );
}

final class _RegistryArtifact {
  const _RegistryArtifact({required this.entry, required this.bytes});

  final SourceRegistryEntry entry;
  final List<int> bytes;
}

final class _FakeTransport implements SourceRegistryHttpTransport {
  _FakeTransport(this.responses, {this.errorCode});

  final Map<String, SourceRegistryHttpResponse> responses;
  final String? errorCode;
  final requests = <({Uri uri, int maxResponseBytes})>[];
  final firstRequestReady = Completer<void>();
  bool blockFirstRequest = false;
  bool _firstRequestBlocked = false;

  List<Uri> get uris => requests.map((request) => request.uri).toList();

  List<int> get maxResponseBytes =>
      requests.map((request) => request.maxResponseBytes).toList();

  @override
  Future<SourceRegistryHttpResponse> get(
    Uri uri, {
    required int maxResponseBytes,
  }) async {
    requests.add((uri: uri, maxResponseBytes: maxResponseBytes));
    if (blockFirstRequest && !_firstRequestBlocked) {
      _firstRequestBlocked = true;
      await firstRequestReady.future;
    }
    final code = errorCode;
    if (code != null) {
      throw SourceRegistryTransportException(code);
    }
    final response = responses[uri.path];
    if (response == null) {
      throw const SourceRegistryTransportException('request_failed');
    }
    return response;
  }

  void releaseFirstRequest() {
    if (!firstRequestReady.isCompleted) firstRequestReady.complete();
  }

  @override
  void close() {}
}

final class _RecordingHttpClient extends http.BaseClient {
  _RecordingHttpClient(this.body);

  final List<int> body;
  final requests = <http.BaseRequest>[];

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    requests.add(request);
    return http.StreamedResponse(
      Stream<List<int>>.fromIterable([body]),
      200,
      contentLength: body.length,
      request: request,
    );
  }
}
