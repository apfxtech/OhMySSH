import 'dart:io';

import 'package:file_saver/file_saver.dart';
import 'package:flutter/foundation.dart';

import 'models.dart';
import 'vault.dart';

class ImportSummary {
  const ImportSummary({
    required this.hostsAdded,
    required this.hostsUpdated,
    required this.identitiesAdded,
    required this.identitiesUpdated,
    required this.groupsAdded,
  });

  final int hostsAdded;
  final int hostsUpdated;
  final int identitiesAdded;
  final int identitiesUpdated;
  final int groupsAdded;

  int get total =>
      hostsAdded +
      hostsUpdated +
      identitiesAdded +
      identitiesUpdated +
      groupsAdded;
}

class VaultStore extends ChangeNotifier {
  VaultStore._();

  static final VaultStore instance = VaultStore._();

  Vault? _vault;
  VaultData _data = const VaultData();

  bool get isUnlocked => _vault != null;

  List<Host> get hosts => List.unmodifiable(_data.hosts);
  List<Identity> get identities => List.unmodifiable(_data.identities);
  List<HostGroup> get groups => List.unmodifiable(_data.groups);

  static Future<bool> vaultExists() => Vault.exists();

  Future<void> create(String password) async {
    _vault = await Vault.create(password: password);
    _data = const VaultData();
    notifyListeners();
  }

  Future<void> unlock(String password) async {
    final vault = await Vault.open(password: password);
    _data = await vault.read();
    _vault = vault;
    notifyListeners();
  }

  void lock() {
    _vault = null;
    _data = const VaultData();
    notifyListeners();
  }

  Future<void> changeMasterPassword(String newPassword) async {
    await _requireVault().changePassword(newPassword);
  }

  Identity? identityFor(Host host) {
    final inline = host.inlineIdentity;
    if (inline != null) return inline;
    final id = host.identityId;
    if (id == null) return null;
    for (final identity in _data.identities) {
      if (identity.id == id) return identity;
    }
    return null;
  }

  Identity? identityById(String? id) {
    if (id == null) return null;
    for (final identity in _data.identities) {
      if (identity.id == id) return identity;
    }
    return null;
  }

  HostGroup? groupById(String? id) {
    if (id == null) return null;
    for (final group in _data.groups) {
      if (group.id == id) return group;
    }
    return null;
  }

  /// Ungrouped hosts come last under a null key.
  Map<HostGroup?, List<Host>> hostsByGroup() {
    final buckets = <HostGroup?, List<Host>>{};
    for (final group in _data.groups) {
      buckets[group] = <Host>[];
    }
    final ungrouped = <Host>[];
    for (final host in _data.hosts) {
      final group = groupById(host.groupId);
      if (group == null) {
        ungrouped.add(host);
      } else {
        buckets.putIfAbsent(group, () => <Host>[]).add(host);
      }
    }
    buckets.removeWhere((_, hosts) => hosts.isEmpty);
    if (ungrouped.isNotEmpty) buckets[null] = ungrouped;
    return buckets;
  }

  Future<void> saveHost(Host host) => _mutate(() {
    final hosts = [..._data.hosts];
    final index = hosts.indexWhere((h) => h.id == host.id);
    if (index >= 0) {
      hosts[index] = host;
    } else {
      hosts.add(host);
    }
    _data = VaultData(
      hosts: hosts,
      identities: _data.identities,
      groups: _data.groups,
    );
  });

  Future<void> deleteHost(String id) => _mutate(() {
    _data = VaultData(
      hosts: _data.hosts.where((h) => h.id != id).toList(),
      identities: _data.identities,
      groups: _data.groups,
    );
  });

  Future<void> saveIdentity(Identity identity) => _mutate(() {
    final identities = [..._data.identities];
    final index = identities.indexWhere((i) => i.id == identity.id);
    if (index >= 0) {
      identities[index] = identity;
    } else {
      identities.add(identity);
    }
    _data = VaultData(
      hosts: _data.hosts,
      identities: identities,
      groups: _data.groups,
    );
  });

  /// Detaches the identity from every host that used it, so no host is left
  /// pointing at an id that no longer resolves.
  Future<void> deleteIdentity(String id) => _mutate(() {
    _data = VaultData(
      hosts: _data.hosts
          .map((h) => h.identityId == id ? h.copyWith(clearIdentity: true) : h)
          .toList(),
      identities: _data.identities.where((i) => i.id != id).toList(),
      groups: _data.groups,
    );
  });

  Future<void> saveGroup(HostGroup group) => _mutate(() {
    final groups = [..._data.groups];
    final index = groups.indexWhere((g) => g.id == group.id);
    if (index >= 0) {
      groups[index] = group;
    } else {
      groups.add(group);
    }
    _data = VaultData(
      hosts: _data.hosts,
      identities: _data.identities,
      groups: groups,
    );
  });

  /// Hosts in the group survive; they just become ungrouped.
  Future<void> deleteGroup(String id) => _mutate(() {
    _data = VaultData(
      hosts: _data.hosts
          .map((h) => h.groupId == id ? h.copyWith(clearGroup: true) : h)
          .toList(),
      identities: _data.identities,
      groups: _data.groups.where((g) => g.id != id).toList(),
    );
  });

  Future<String?> exportVault({String fileName = 'ohmyssh'}) async {
    final bytes = await _requireVault().file.readAsBytes();
    return FileSaver.instance.saveAs(
      name: fileName,
      bytes: Uint8List.fromList(bytes),
      fileExtension: 'vault',
      mimeType: MimeType.json,
    );
  }

  /// Merges another vault into this one: matching ids are overwritten, new ids
  /// appended.
  Future<ImportSummary> importVault({
    required File file,
    required String password,
  }) async {
    final incoming = await (await Vault.open(
      password: password,
      file: file,
    )).read();

    var hostsAdded = 0, hostsUpdated = 0;
    var identitiesAdded = 0, identitiesUpdated = 0;
    var groupsAdded = 0;

    final hosts = [..._data.hosts];
    for (final host in incoming.hosts) {
      final index = hosts.indexWhere((h) => h.id == host.id);
      if (index >= 0) {
        hosts[index] = host;
        hostsUpdated++;
      } else {
        hosts.add(host);
        hostsAdded++;
      }
    }

    final identities = [..._data.identities];
    for (final identity in incoming.identities) {
      final index = identities.indexWhere((i) => i.id == identity.id);
      if (index >= 0) {
        identities[index] = identity;
        identitiesUpdated++;
      } else {
        identities.add(identity);
        identitiesAdded++;
      }
    }

    final groups = [..._data.groups];
    for (final group in incoming.groups) {
      final index = groups.indexWhere((g) => g.id == group.id);
      if (index >= 0) {
        groups[index] = group;
      } else {
        groups.add(group);
        groupsAdded++;
      }
    }

    await _mutate(() {
      _data = VaultData(hosts: hosts, identities: identities, groups: groups);
    });

    return ImportSummary(
      hostsAdded: hostsAdded,
      hostsUpdated: hostsUpdated,
      identitiesAdded: identitiesAdded,
      identitiesUpdated: identitiesUpdated,
      groupsAdded: groupsAdded,
    );
  }

  Vault _requireVault() {
    final vault = _vault;
    if (vault == null) throw const VaultException('Vault is locked');
    return vault;
  }

  Future<void> _mutate(void Function() change) async {
    final vault = _requireVault();
    change();
    await vault.save(_data);
    notifyListeners();
  }
}
