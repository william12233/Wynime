const bangumiBrokerOriginEnvironmentKey = 'WYNIME_BANGUMI_BROKER_ORIGIN';

final class BangumiRuntimeConfiguration {
  const BangumiRuntimeConfiguration.unavailable()
    : brokerOrigin = null,
      verifiedAppLinkHost = null;

  const BangumiRuntimeConfiguration.configured({
    required this.brokerOrigin,
    required this.verifiedAppLinkHost,
  });

  final Uri? brokerOrigin;
  final String? verifiedAppLinkHost;

  bool get isConfigured => brokerOrigin != null && verifiedAppLinkHost != null;

  factory BangumiRuntimeConfiguration.fromEnvironment() {
    return BangumiRuntimeConfiguration.fromRawOrigin(
      const String.fromEnvironment(
        bangumiBrokerOriginEnvironmentKey,
        defaultValue: '',
      ),
    );
  }

  factory BangumiRuntimeConfiguration.fromRawOrigin(String rawOrigin) {
    final value = rawOrigin.trim();
    if (value.isEmpty) return const BangumiRuntimeConfiguration.unavailable();

    final parsed = Uri.tryParse(value);
    if (parsed == null || !_isValidBrokerOrigin(parsed)) {
      return const BangumiRuntimeConfiguration.unavailable();
    }

    final origin = parsed.replace(path: '');
    return BangumiRuntimeConfiguration.configured(
      brokerOrigin: origin,
      verifiedAppLinkHost: parsed.host.toLowerCase(),
    );
  }

  static bool _isValidBrokerOrigin(Uri value) {
    return value.scheme.toLowerCase() == 'https' &&
        value.host.isNotEmpty &&
        _isSafeHost(value.host) &&
        value.port == 443 &&
        value.userInfo.isEmpty &&
        !value.hasFragment &&
        !value.hasQuery &&
        (value.path.isEmpty || value.path == '/');
  }

  static bool _isSafeHost(String host) {
    if (host.length > 253) return false;
    final labels = host.split('.');
    if (labels.any((label) => label.isEmpty || label.length > 63)) {
      return false;
    }
    final labelPattern = RegExp(r'^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$');
    return labels.every(labelPattern.hasMatch);
  }
}
