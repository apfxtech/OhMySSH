import 'dart:convert';

import 'package:dartssh2/dartssh2.dart';

class HostMetrics {
  const HostMetrics({
    this.load1,
    this.load5,
    this.load15,
    this.cpuPercent,
    this.cpuCount,
    this.memTotalKb,
    this.memAvailableKb,
    this.diskTotalKb,
    this.diskFreeKb,
    this.uptime,
  });

  final double? load1;
  final double? load5;
  final double? load15;

  /// Windows reports a percentage rather than a load average.
  final double? cpuPercent;

  final int? cpuCount;
  final int? memTotalKb;
  final int? memAvailableKb;
  final int? diskTotalKb;
  final int? diskFreeKb;
  final Duration? uptime;

  double? get memUsedRatio {
    final total = memTotalKb;
    final available = memAvailableKb;
    if (total == null || available == null || total <= 0) return null;
    return ((total - available) / total).clamp(0.0, 1.0);
  }

  double? get diskUsedRatio {
    final total = diskTotalKb;
    final free = diskFreeKb;
    if (total == null || free == null || total <= 0) return null;
    return ((total - free) / total).clamp(0.0, 1.0);
  }
}

class HostProfile {
  const HostProfile({
    required this.osId,
    required this.osPretty,
    this.kernel,
    this.arch,
    this.hostname,
    this.metrics = const HostMetrics(),
  });

  /// Matches an asset basename in assets/ic/os/ when [kKnownOsIds] contains it.
  final String osId;
  final String osPretty;
  final String? kernel;
  final String? arch;
  final String? hostname;
  final HostMetrics metrics;
}

const Set<String> kKnownOsIds = {
  'linux', 'ubuntu', 'debian', 'arch', 'fedora', 'centos', 'rhel', 'alpine',
  'opensuse', 'gentoo', 'manjaro', 'mint', 'rocky', 'almalinux', 'kali',
  'raspbian', 'freebsd', 'openbsd', 'netbsd', 'macos', 'windows', 'unknown',
};

String osIconAsset(String? osId) {
  final id = osId ?? 'unknown';
  if (kKnownOsIds.contains(id)) return 'assets/ic/os/$id.svg';
  return 'assets/ic/os/linux.svg';
}

int osColorValue(String? osId) {
  switch (osId) {
    case 'ubuntu':
      return 0xFFE95420;
    case 'debian':
    case 'raspbian':
      return 0xFFA80030;
    case 'arch':
    case 'manjaro':
      return 0xFF1793D1;
    case 'fedora':
      return 0xFF294172;
    case 'centos':
    case 'rocky':
    case 'almalinux':
    case 'rhel':
      return 0xFFEE0000;
    case 'alpine':
      return 0xFF0D597F;
    case 'opensuse':
      return 0xFF73BA25;
    case 'mint':
      return 0xFF87CF3E;
    case 'kali':
      return 0xFF557C94;
    case 'gentoo':
      return 0xFF54487A;
    case 'freebsd':
    case 'openbsd':
    case 'netbsd':
      return 0xFFAB2B28;
    case 'macos':
      return 0xFF9E9E9E;
    case 'windows':
      return 0xFF0078D4;
    default:
      return 0xFF6F6F6F;
  }
}

Future<HostProfile> probeHost(SSHClient client) async {
  final kernel = (await _tryRun(client, 'uname -s')).trim();

  if (kernel.isEmpty) {
    // No `uname` — almost certainly Windows OpenSSH on cmd.exe or PowerShell.
    return _probeWindows(client);
  }
  if (kernel == 'Darwin') return _probeMacos(client);
  if (kernel.endsWith('BSD')) return _probeBsd(client, kernel);
  return _probeLinux(client);
}

Future<String> _tryRun(SSHClient client, String command) async {
  try {
    // stderr off: a missing command must read as empty output, not as noise the
    // parsers then have to filter.
    return utf8.decode(
      await client.run(command, stderr: false),
      allowMalformed: true,
    );
  } catch (_) {
    return '';
  }
}

Future<HostProfile> _probeLinux(SSHClient client) async {
  const script = '''
echo "@@osrelease"; cat /etc/os-release 2>/dev/null
echo "@@kernel"; uname -r 2>/dev/null; uname -m 2>/dev/null; hostname 2>/dev/null
echo "@@load"; cat /proc/loadavg 2>/dev/null
echo "@@cpus"; nproc 2>/dev/null
echo "@@mem"; grep -E '^(MemTotal|MemAvailable):' /proc/meminfo 2>/dev/null
echo "@@uptime"; cut -d. -f1 /proc/uptime 2>/dev/null
echo "@@disk"; df -Pk / 2>/dev/null | tail -1
''';

  final sections = _sections(await _tryRun(client, script));

  final release = _parseOsRelease(sections['osrelease'] ?? const []);
  final kernelLines = sections['kernel'] ?? const [];
  final load = _parseLoadAvg(_first(sections['load']));
  final mem = _parseMeminfo(sections['mem'] ?? const []);
  final disk = _parseDf(_first(sections['disk']));

  return HostProfile(
    osId: release.id,
    osPretty: release.pretty,
    kernel: kernelLines.isNotEmpty ? kernelLines[0] : null,
    arch: kernelLines.length > 1 ? kernelLines[1] : null,
    hostname: kernelLines.length > 2 ? kernelLines[2] : null,
    metrics: HostMetrics(
      load1: load?[0],
      load5: load?[1],
      load15: load?[2],
      cpuCount: int.tryParse(_first(sections['cpus'])?.trim() ?? ''),
      memTotalKb: mem.$1,
      memAvailableKb: mem.$2,
      diskTotalKb: disk?.$1,
      diskFreeKb: disk?.$2,
      uptime: _durationFromSeconds(_first(sections['uptime'])),
    ),
  );
}

Future<HostProfile> _probeMacos(SSHClient client) async {
  const script = '''
echo "@@sw"; sw_vers -productName 2>/dev/null; sw_vers -productVersion 2>/dev/null
echo "@@kernel"; uname -r 2>/dev/null; uname -m 2>/dev/null; hostname 2>/dev/null
echo "@@load"; sysctl -n vm.loadavg 2>/dev/null
echo "@@cpus"; sysctl -n hw.ncpu 2>/dev/null
echo "@@mem"; sysctl -n hw.memsize 2>/dev/null
echo "@@boot"; sysctl -n kern.boottime 2>/dev/null
echo "@@now"; date +%s 2>/dev/null
echo "@@disk"; df -Pk / 2>/dev/null | tail -1
''';

  final sections = _sections(await _tryRun(client, script));
  final sw = sections['sw'] ?? const [];
  final kernelLines = sections['kernel'] ?? const [];
  // `sysctl -n vm.loadavg` prints "{ 1.23 1.45 1.67 }".
  final load = _parseLoadAvg(
    _first(sections['load'])?.replaceAll(RegExp(r'[{}]'), ''),
  );
  final memBytes = int.tryParse(_first(sections['mem'])?.trim() ?? '');
  final disk = _parseDf(_first(sections['disk']));

  final product = sw.isNotEmpty ? sw[0] : 'macOS';
  final version = sw.length > 1 ? sw[1] : '';

  return HostProfile(
    osId: 'macos',
    osPretty: version.isEmpty ? product : '$product $version',
    kernel: kernelLines.isNotEmpty ? kernelLines[0] : null,
    arch: kernelLines.length > 1 ? kernelLines[1] : null,
    hostname: kernelLines.length > 2 ? kernelLines[2] : null,
    metrics: HostMetrics(
      load1: load?[0],
      load5: load?[1],
      load15: load?[2],
      cpuCount: int.tryParse(_first(sections['cpus'])?.trim() ?? ''),
      memTotalKb: memBytes == null ? null : memBytes ~/ 1024,
      diskTotalKb: disk?.$1,
      diskFreeKb: disk?.$2,
      uptime: _macUptime(_first(sections['boot']), _first(sections['now'])),
    ),
  );
}

Future<HostProfile> _probeBsd(SSHClient client, String kernelName) async {
  const script = '''
echo "@@kernel"; uname -r 2>/dev/null; uname -m 2>/dev/null; hostname 2>/dev/null
echo "@@load"; sysctl -n vm.loadavg 2>/dev/null
echo "@@cpus"; sysctl -n hw.ncpu 2>/dev/null
echo "@@mem"; sysctl -n hw.physmem 2>/dev/null
echo "@@disk"; df -Pk / 2>/dev/null | tail -1
''';

  final sections = _sections(await _tryRun(client, script));
  final kernelLines = sections['kernel'] ?? const [];
  final load = _parseLoadAvg(
    _first(sections['load'])?.replaceAll(RegExp(r'[{}]'), ''),
  );
  final memBytes = int.tryParse(_first(sections['mem'])?.trim() ?? '');
  final disk = _parseDf(_first(sections['disk']));
  final id = kernelName.toLowerCase();

  return HostProfile(
    osId: kKnownOsIds.contains(id) ? id : 'unknown',
    osPretty: kernelLines.isEmpty ? kernelName : '$kernelName ${kernelLines[0]}',
    kernel: kernelLines.isNotEmpty ? kernelLines[0] : null,
    arch: kernelLines.length > 1 ? kernelLines[1] : null,
    hostname: kernelLines.length > 2 ? kernelLines[2] : null,
    metrics: HostMetrics(
      load1: load?[0],
      load5: load?[1],
      load15: load?[2],
      cpuCount: int.tryParse(_first(sections['cpus'])?.trim() ?? ''),
      memTotalKb: memBytes == null ? null : memBytes ~/ 1024,
      diskTotalKb: disk?.$1,
      diskFreeKb: disk?.$2,
    ),
  );
}

Future<HostProfile> _probeWindows(SSHClient client) async {
  // Single quotes only inside the -Command string: the outer double quotes have
  // to survive cmd.exe, the default OpenSSH shell on Windows.
  const command =
      'powershell -NoProfile -NonInteractive -Command '
      '"\$o=Get-CimInstance Win32_OperatingSystem;'
      '\$c=Get-CimInstance Win32_ComputerSystem;'
      '\$d=Get-CimInstance Win32_LogicalDisk|Where-Object {\$_.DeviceID -eq \'C:\'};'
      '\$p=(Get-CimInstance Win32_Processor|Measure-Object -Property LoadPercentage -Average).Average;'
      '@{caption=\$o.Caption;version=\$o.Version;arch=\$o.OSArchitecture;'
      'host=\$c.Name;cpus=\$c.NumberOfLogicalProcessors;cpuPercent=\$p;'
      'memTotalKb=\$o.TotalVisibleMemorySize;memFreeKb=\$o.FreePhysicalMemory;'
      'uptimeSec=[int]((Get-Date)-\$o.LastBootUpTime).TotalSeconds;'
      'diskTotalB=\$d.Size;diskFreeB=\$d.FreeSpace}'
      '|ConvertTo-Json -Compress"';

  final raw = (await _tryRun(client, command)).trim();
  Map<String, dynamic>? json;
  if (raw.startsWith('{')) {
    try {
      json = jsonDecode(raw) as Map<String, dynamic>;
    } catch (_) {
      json = null;
    }
  }

  if (json == null) {
    return const HostProfile(osId: 'windows', osPretty: 'Windows');
  }

  int? kb(String key) {
    final bytes = (json![key] as num?)?.toInt();
    return bytes == null ? null : bytes ~/ 1024;
  }

  final caption = (json['caption'] as String?)?.trim();
  return HostProfile(
    osId: 'windows',
    osPretty: caption == null || caption.isEmpty ? 'Windows' : caption,
    kernel: json['version'] as String?,
    arch: json['arch'] as String?,
    hostname: json['host'] as String?,
    metrics: HostMetrics(
      cpuPercent: (json['cpuPercent'] as num?)?.toDouble(),
      cpuCount: (json['cpus'] as num?)?.toInt(),
      memTotalKb: (json['memTotalKb'] as num?)?.toInt(),
      memAvailableKb: (json['memFreeKb'] as num?)?.toInt(),
      diskTotalKb: kb('diskTotalB'),
      diskFreeKb: kb('diskFreeB'),
      uptime: switch ((json['uptimeSec'] as num?)?.toInt()) {
        final int seconds => Duration(seconds: seconds),
        null => null,
      },
    ),
  );
}

/// Splits output on `@@name` marker lines into name -> non-empty lines.
Map<String, List<String>> _sections(String output) {
  final result = <String, List<String>>{};
  String? current;
  for (final rawLine in const LineSplitter().convert(output)) {
    final line = rawLine.trim();
    if (line.startsWith('@@')) {
      current = line.substring(2);
      result[current] = <String>[];
      continue;
    }
    if (current == null || line.isEmpty) continue;
    result[current]!.add(line);
  }
  return result;
}

String? _first(List<String>? lines) =>
    (lines == null || lines.isEmpty) ? null : lines.first;

class _OsRelease {
  const _OsRelease(this.id, this.pretty);
  final String id;
  final String pretty;
}

_OsRelease _parseOsRelease(List<String> lines) {
  final values = <String, String>{};
  for (final line in lines) {
    final split = line.indexOf('=');
    if (split <= 0) continue;
    var value = line.substring(split + 1).trim();
    if (value.length >= 2 &&
        ((value.startsWith('"') && value.endsWith('"')) ||
            (value.startsWith("'") && value.endsWith("'")))) {
      value = value.substring(1, value.length - 1);
    }
    values[line.substring(0, split).trim()] = value;
  }

  final id = values['ID']?.toLowerCase() ?? '';
  final pretty = values['PRETTY_NAME'] ?? values['NAME'] ?? 'Linux';

  if (kKnownOsIds.contains(id)) return _OsRelease(id, pretty);

  for (final like in (values['ID_LIKE'] ?? '').toLowerCase().split(
    RegExp(r'\s+'),
  )) {
    if (kKnownOsIds.contains(like)) return _OsRelease(like, pretty);
  }
  return _OsRelease('linux', pretty);
}

List<double>? _parseLoadAvg(String? line) {
  if (line == null) return null;
  final parts = line.trim().split(RegExp(r'\s+'));
  if (parts.length < 3) return null;
  final values = parts.take(3).map(double.tryParse).toList();
  if (values.any((v) => v == null)) return null;
  return values.cast<double>();
}

(int?, int?) _parseMeminfo(List<String> lines) {
  int? total;
  int? available;
  for (final line in lines) {
    final match = RegExp(r'^(\w+):\s+(\d+)').firstMatch(line);
    if (match == null) continue;
    final value = int.tryParse(match.group(2)!);
    if (match.group(1) == 'MemTotal') total = value;
    if (match.group(1) == 'MemAvailable') available = value;
  }
  return (total, available);
}

/// `df -Pk /` tail line: filesystem, 1K-blocks, used, available, capacity,
/// mount.
(int?, int?)? _parseDf(String? line) {
  if (line == null) return null;
  final parts = line.trim().split(RegExp(r'\s+'));
  if (parts.length < 4) return null;
  return (int.tryParse(parts[1]), int.tryParse(parts[3]));
}

Duration? _durationFromSeconds(String? raw) {
  final seconds = int.tryParse(raw?.trim() ?? '');
  return seconds == null ? null : Duration(seconds: seconds);
}

/// `sysctl -n kern.boottime` prints "{ sec = 1690000000, usec = 0 } ...".
Duration? _macUptime(String? boottime, String? now) {
  if (boottime == null || now == null) return null;
  final boot = int.tryParse(
    RegExp(r'sec\s*=\s*(\d+)').firstMatch(boottime)?.group(1) ?? '',
  );
  final current = int.tryParse(now.trim());
  if (boot == null || current == null || current < boot) return null;
  return Duration(seconds: current - boot);
}
