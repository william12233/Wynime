import 'dart:collection';

enum SourcePermission {
  network,
  cookies,
  desktopUserAgent,
  insecureHttp,
  webView,
  mediaRequestInspection,
}

final class SourceResourceBudget {
  SourceResourceBudget({
    required this.maxDocumentBytes,
    required this.maxRecords,
    required this.maxSelectorMatches,
    required this.maxEvaluationSteps,
    required this.maxRegexPatternChars,
    required this.maxRegexInputChars,
    required this.maxRedirects,
  }) {
    _validateRange(maxDocumentBytes, 'maxDocumentBytes', 1, 8 * 1024 * 1024);
    _validateRange(maxRecords, 'maxRecords', 1, 1000);
    _validateRange(maxSelectorMatches, 'maxSelectorMatches', 1, 5000);
    _validateRange(maxEvaluationSteps, 'maxEvaluationSteps', 1, 50000);
    _validateRange(maxRegexPatternChars, 'maxRegexPatternChars', 1, 256);
    _validateRange(maxRegexInputChars, 'maxRegexInputChars', 1, 4096);
    _validateRange(maxRedirects, 'maxRedirects', 0, 10);
  }

  final int maxDocumentBytes;
  final int maxRecords;
  final int maxSelectorMatches;
  final int maxEvaluationSteps;
  final int maxRegexPatternChars;
  final int maxRegexInputChars;
  final int maxRedirects;

  bool isBroaderThan(SourceResourceBudget previous) {
    return maxDocumentBytes > previous.maxDocumentBytes ||
        maxRecords > previous.maxRecords ||
        maxSelectorMatches > previous.maxSelectorMatches ||
        maxEvaluationSteps > previous.maxEvaluationSteps ||
        maxRegexPatternChars > previous.maxRegexPatternChars ||
        maxRegexInputChars > previous.maxRegexInputChars ||
        maxRedirects > previous.maxRedirects;
  }

  static void _validateRange(int value, String name, int minimum, int maximum) {
    if (value < minimum || value > maximum) {
      throw ArgumentError.value(
        value,
        name,
        'Must be between $minimum and $maximum.',
      );
    }
  }
}

final class SourceDomainRule {
  SourceDomainRule({
    required String host,
    this.includeSubdomains = false,
    Set<String> schemes = const {'https'},
  }) : host = _normalizeHost(host),
       schemes = UnmodifiableSetView(_validateSchemes(schemes));

  final String host;
  final bool includeSubdomains;
  final UnmodifiableSetView<String> schemes;

  bool allows(Uri uri, Set<SourcePermission> permissions) {
    if (!uri.hasScheme || uri.host.isEmpty || uri.userInfo.isNotEmpty) {
      return false;
    }
    final normalizedScheme = uri.scheme.toLowerCase();
    if (!schemes.contains(normalizedScheme)) {
      return false;
    }
    if (normalizedScheme == 'http' &&
        !permissions.contains(SourcePermission.insecureHttp)) {
      return false;
    }

    final requestHost = uri.host.toLowerCase().replaceFirst(RegExp(r'\.$'), '');
    return requestHost == host ||
        (includeSubdomains && requestHost.endsWith('.$host'));
  }

  bool covers(SourceDomainRule candidate) {
    if (!schemes.containsAll(candidate.schemes)) {
      return false;
    }
    if (candidate.host == host) {
      return includeSubdomains || !candidate.includeSubdomains;
    }
    return includeSubdomains && candidate.host.endsWith('.$host');
  }

  static String _normalizeHost(String value) {
    var host = value.trim().toLowerCase();
    if (host.endsWith('.')) {
      host = host.substring(0, host.length - 1);
    }
    final valid = RegExp(
      r'^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)*'
      r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$',
    );
    if (host.isEmpty || host.length > 253 || !valid.hasMatch(host)) {
      throw ArgumentError.value(value, 'host', 'Must be a valid ASCII host.');
    }
    return host;
  }

  static Set<String> _validateSchemes(Set<String> values) {
    if (values.isEmpty) {
      throw ArgumentError.value(values, 'schemes', 'Must not be empty.');
    }
    final normalized = values.map((value) => value.trim().toLowerCase()).toSet();
    const supported = {'https', 'http'};
    if (!supported.containsAll(normalized)) {
      throw ArgumentError.value(
        values,
        'schemes',
        'Only https and explicitly permitted http are supported.',
      );
    }
    return Set<String>.unmodifiable(normalized);
  }
}

final class SourceSecurityPolicy {
  SourceSecurityPolicy({
    required Iterable<SourceDomainRule> allowedDomains,
    required Set<SourcePermission> permissions,
    required this.budget,
  }) : allowedDomains = UnmodifiableListView(
         List<SourceDomainRule>.unmodifiable(allowedDomains),
       ),
       permissions = UnmodifiableSetView(
         Set<SourcePermission>.unmodifiable(permissions),
       ) {
    if (this.allowedDomains.isEmpty) {
      throw ArgumentError.value(
        allowedDomains,
        'allowedDomains',
        'At least one domain rule is required.',
      );
    }
    final allowsHttp = this.allowedDomains.any(
      (rule) => rule.schemes.contains('http'),
    );
    if (allowsHttp &&
        !this.permissions.contains(SourcePermission.insecureHttp)) {
      throw ArgumentError(
        'The insecureHttp permission is required for an http domain rule.',
      );
    }
  }

  final UnmodifiableListView<SourceDomainRule> allowedDomains;
  final UnmodifiableSetView<SourcePermission> permissions;
  final SourceResourceBudget budget;

  bool allowsUri(Uri uri) {
    return permissions.contains(SourcePermission.network) &&
        allowedDomains.any((rule) => rule.allows(uri, permissions));
  }

  bool requiresReconsentComparedTo(SourceSecurityPolicy previous) {
    if (!previous.permissions.containsAll(permissions)) {
      return true;
    }
    if (budget.isBroaderThan(previous.budget)) {
      return true;
    }
    for (final candidate in allowedDomains) {
      if (!previous.allowedDomains.any((rule) => rule.covers(candidate))) {
        return true;
      }
    }
    return false;
  }
}
