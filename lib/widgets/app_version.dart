import 'dart:io';

import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../theme/theme.dart';

/// The release scripts pass the git tag through
/// `--dart-define=OHMYSSH_RELEASE_TAG`.
class AppVersionLabel extends StatelessWidget {
  const AppVersionLabel({super.key});

  static const _releaseTag = String.fromEnvironment('OHMYSSH_RELEASE_TAG');

  static final Future<PackageInfo> _packageInfo = PackageInfo.fromPlatform();

  static String get _platformName {
    if (Platform.isAndroid) return 'Android';
    if (Platform.isIOS) return 'iOS';
    if (Platform.isMacOS) return 'macOS';
    if (Platform.isWindows) return 'Windows';
    if (Platform.isLinux) return 'Linux';
    return Platform.operatingSystem;
  }

  static String? get _releaseType {
    if (_releaseTag.isEmpty) return 'local';
    // Only a prefixed tag carries a channel: "alpha-0.2.0" is alpha; a bare
    // "0.2.0" or "v0.2.0" is a plain release.
    final match = RegExp(
      r'^([A-Za-z][A-Za-z0-9]*)-v?[0-9]',
    ).firstMatch(_releaseTag);
    return match?.group(1);
  }

  static String _versionText(String version) {
    final type = _releaseType;
    final suffix = type == null ? '' : ' ($type)';
    return 'ohmyssh for $_platformName v$version$suffix';
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.appColors;
    return FutureBuilder<PackageInfo>(
      future: _packageInfo,
      builder: (context, snapshot) {
        final info = snapshot.data;
        if (info == null) return const SizedBox.shrink();
        return Padding(
          padding: const EdgeInsets.only(top: 12),
          child: Text(
            _versionText(info.version),
            textAlign: TextAlign.center,
            style: TextStyle(
              color: colors.textMuted,
              fontSize: 12,
              height: 1.2,
            ),
          ),
        );
      },
    );
  }
}
