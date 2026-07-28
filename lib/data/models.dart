import 'dart:math';

String newId() {
  final stamp = DateTime.now().microsecondsSinceEpoch.toRadixString(36);
  final salt = Random().nextInt(1 << 32).toRadixString(36).padLeft(7, '0');
  return '$stamp$salt';
}

enum AuthKind {
  password,
  privateKey;

  static AuthKind parse(String? raw) => AuthKind.values.firstWhere(
    (kind) => kind.name == raw,
    orElse: () => AuthKind.password,
  );
}

class Identity {
  const Identity({
    required this.id,
    required this.label,
    required this.username,
    this.kind = AuthKind.password,
    this.password,
    this.privateKey,
    this.passphrase,
  });

  final String id;
  final String label;
  final String username;
  final AuthKind kind;
  final String? password;

  final String? privateKey;
  final String? passphrase;

  Identity copyWith({
    String? label,
    String? username,
    AuthKind? kind,
    String? password,
    String? privateKey,
    String? passphrase,
  }) => Identity(
    id: id,
    label: label ?? this.label,
    username: username ?? this.username,
    kind: kind ?? this.kind,
    password: password ?? this.password,
    privateKey: privateKey ?? this.privateKey,
    passphrase: passphrase ?? this.passphrase,
  );

  Map<String, dynamic> toJson() => {
    'id': id,
    'label': label,
    'username': username,
    'kind': kind.name,
    if (password != null) 'password': password,
    if (privateKey != null) 'privateKey': privateKey,
    if (passphrase != null) 'passphrase': passphrase,
  };

  factory Identity.fromJson(Map<String, dynamic> json) => Identity(
    id: json['id'] as String? ?? newId(),
    label: json['label'] as String? ?? '',
    username: json['username'] as String? ?? '',
    kind: AuthKind.parse(json['kind'] as String?),
    password: json['password'] as String?,
    privateKey: json['privateKey'] as String?,
    passphrase: json['passphrase'] as String?,
  );
}

class HostGroup {
  const HostGroup({required this.id, required this.name});

  final String id;
  final String name;

  HostGroup copyWith({String? name}) =>
      HostGroup(id: id, name: name ?? this.name);

  Map<String, dynamic> toJson() => {'id': id, 'name': name};

  factory HostGroup.fromJson(Map<String, dynamic> json) => HostGroup(
    id: json['id'] as String? ?? newId(),
    name: json['name'] as String? ?? 'Group',
  );
}

class Host {
  const Host({
    required this.id,
    required this.label,
    required this.hostname,
    this.port = 22,
    this.identityId,
    this.inlineIdentity,
    this.groupId,
    this.note,
    this.knownHostKey,
    this.osId,
    this.osPretty,
  });

  final String id;
  final String label;
  final String hostname;
  final int port;

  final String? identityId;

  /// Mutually exclusive with [identityId]; [resolvedIdentity] picks between
  /// them.
  final Identity? inlineIdentity;

  final String? groupId;
  final String? note;

  /// TOFU pin: base64 of the host key seen on first connect. A mismatch on a
  /// later connect is a hard stop, not a warning.
  final String? knownHostKey;

  final String? osId;
  final String? osPretty;

  bool get hasInlineIdentity => inlineIdentity != null;

  String get displayLabel => label.isNotEmpty ? label : hostname;

  String get endpoint => port == 22 ? hostname : '$hostname:$port';

  Host copyWith({
    String? label,
    String? hostname,
    int? port,
    String? identityId,
    Identity? inlineIdentity,
    String? groupId,
    String? note,
    String? knownHostKey,
    String? osId,
    String? osPretty,
    bool clearIdentity = false,
    bool clearInlineIdentity = false,
    bool clearGroup = false,
  }) => Host(
    id: id,
    label: label ?? this.label,
    hostname: hostname ?? this.hostname,
    port: port ?? this.port,
    identityId: clearIdentity ? null : (identityId ?? this.identityId),
    inlineIdentity: clearInlineIdentity
        ? null
        : (inlineIdentity ?? this.inlineIdentity),
    groupId: clearGroup ? null : (groupId ?? this.groupId),
    note: note ?? this.note,
    knownHostKey: knownHostKey ?? this.knownHostKey,
    osId: osId ?? this.osId,
    osPretty: osPretty ?? this.osPretty,
  );

  Map<String, dynamic> toJson() => {
    'id': id,
    'label': label,
    'hostname': hostname,
    'port': port,
    if (identityId != null) 'identityId': identityId,
    if (inlineIdentity != null) 'inlineIdentity': inlineIdentity!.toJson(),
    if (groupId != null) 'groupId': groupId,
    if (note != null) 'note': note,
    if (knownHostKey != null) 'knownHostKey': knownHostKey,
    if (osId != null) 'osId': osId,
    if (osPretty != null) 'osPretty': osPretty,
  };

  factory Host.fromJson(Map<String, dynamic> json) => Host(
    id: json['id'] as String? ?? newId(),
    label: json['label'] as String? ?? '',
    hostname: json['hostname'] as String? ?? '',
    port: (json['port'] as num?)?.toInt() ?? 22,
    identityId: json['identityId'] as String?,
    inlineIdentity: switch (json['inlineIdentity']) {
      final Map<String, dynamic> raw => Identity.fromJson(raw),
      _ => null,
    },
    groupId: json['groupId'] as String?,
    note: json['note'] as String?,
    knownHostKey: json['knownHostKey'] as String?,
    osId: json['osId'] as String?,
    osPretty: json['osPretty'] as String?,
  );
}

class VaultData {
  const VaultData({
    this.hosts = const [],
    this.identities = const [],
    this.groups = const [],
  });

  final List<Host> hosts;
  final List<Identity> identities;
  final List<HostGroup> groups;

  Map<String, dynamic> toJson() => {
    'hosts': hosts.map((h) => h.toJson()).toList(),
    'identities': identities.map((i) => i.toJson()).toList(),
    'groups': groups.map((g) => g.toJson()).toList(),
  };

  factory VaultData.fromJson(Map<String, dynamic> json) {
    List<T> parse<T>(String key, T Function(Map<String, dynamic>) build) =>
        (json[key] as List<dynamic>? ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(build)
            .toList();

    return VaultData(
      hosts: parse('hosts', Host.fromJson),
      identities: parse('identities', Identity.fromJson),
      groups: parse('groups', HostGroup.fromJson),
    );
  }
}
