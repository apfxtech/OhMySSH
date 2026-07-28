import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:ohmyssh/data/models.dart';
import 'package:ohmyssh/data/vault.dart';

void main() {
  late Directory temp;

  setUp(() async {
    temp = await Directory.systemTemp.createTemp('ohmyssh_vault_test');
  });

  tearDown(() async {
    if (await temp.exists()) await temp.delete(recursive: true);
  });

  File vaultFile() => File('${temp.path}/test.vault');

  VaultData sample() => VaultData(
    hosts: [
      const Host(
        id: 'h1',
        label: 'web-01',
        hostname: '10.0.0.5',
        port: 2222,
        identityId: 'i1',
        knownHostKey: 'SHA256:abc',
        osId: 'ubuntu',
      ),
    ],
    identities: [
      const Identity(
        id: 'i1',
        label: 'root',
        username: 'root',
        kind: AuthKind.password,
        password: 'hunter2',
      ),
    ],
    groups: [const HostGroup(id: 'g1', name: 'Production')],
  );

  test('round-trips data through create + open', () async {
    final vault = await Vault.create(
      password: 'correct horse battery',
      file: vaultFile(),
      initial: sample(),
    );
    expect(await vaultFile().exists(), isTrue);

    final reopened = await Vault.open(
      password: 'correct horse battery',
      file: vaultFile(),
    );
    final data = await reopened.read();

    expect(data.hosts, hasLength(1));
    expect(data.hosts.single.hostname, '10.0.0.5');
    expect(data.hosts.single.port, 2222);
    expect(data.hosts.single.knownHostKey, 'SHA256:abc');
    expect(data.identities.single.password, 'hunter2');
    expect(data.groups.single.name, 'Production');

    await vault.save(data);
  });

  test('rejects the wrong password', () async {
    await Vault.create(
      password: 'right',
      file: vaultFile(),
      initial: sample(),
    );

    expect(
      () => Vault.open(password: 'wrong', file: vaultFile()),
      throwsA(isA<WrongPasswordException>()),
    );
  });

  test('secrets are not readable in the file on disk', () async {
    await Vault.create(
      password: 'right',
      file: vaultFile(),
      initial: sample(),
    );

    final raw = await vaultFile().readAsString();
    expect(raw, isNot(contains('hunter2')));
    expect(raw, isNot(contains('10.0.0.5')));
    expect(raw, isNot(contains('web-01')));
    expect(raw, contains(kVaultFormat));
  });

  test('changePassword re-encrypts without losing data', () async {
    final vault = await Vault.create(
      password: 'old-password',
      file: vaultFile(),
      initial: sample(),
    );

    await vault.changePassword('new-password');

    expect(
      () => Vault.open(password: 'old-password', file: vaultFile()),
      throwsA(isA<WrongPasswordException>()),
    );

    final reopened = await Vault.open(
      password: 'new-password',
      file: vaultFile(),
    );
    expect((await reopened.read()).hosts.single.label, 'web-01');
  });

  test('refuses a file that is not an ohmyssh vault', () async {
    await vaultFile().writeAsString('{"format":"something-else"}');
    expect(
      () => Vault.open(password: 'x', file: vaultFile()),
      throwsA(isA<VaultException>()),
    );
  });
}
