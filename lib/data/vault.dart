import 'dart:convert';
import 'dart:io';
import 'dart:isolate';
import 'dart:math';
import 'dart:typed_data';

import 'package:cryptography/cryptography.dart';
import 'package:path_provider/path_provider.dart';

import 'models.dart';

const String kVaultFormat = 'ohmyssh.vault';
const int kVaultVersion = 1;
const String kVaultFileName = 'ohmyssh.vault';

const String _fieldFormat = 'format';
const String _fieldVersion = 'version';
const String _fieldKdf = 'kdf';
const String _fieldCipher = 'cipher';
const String _fieldNonce = 'nonce';
const String _fieldMac = 'mac';
const String _fieldData = 'data';

/// Written into the envelope, so raising it does not orphan existing vaults.
const int kKdfIterations = 120000;

class VaultException implements Exception {
  const VaultException(this.message);
  final String message;
  @override
  String toString() => message;
}

class WrongPasswordException extends VaultException {
  const WrongPasswordException() : super('Wrong master password');
}

/// Runs off the UI isolate: 120k HMAC rounds would otherwise drop frames.
Future<Uint8List> _deriveKey({
  required String password,
  required Uint8List salt,
  required int iterations,
}) {
  return Isolate.run(() async {
    final pbkdf2 = Pbkdf2(
      macAlgorithm: Hmac.sha256(),
      iterations: iterations,
      bits: 256,
    );
    final key = await pbkdf2.deriveKey(
      secretKey: SecretKey(utf8.encode(password)),
      nonce: salt,
    );
    return Uint8List.fromList(await key.extractBytes());
  });
}

Uint8List _randomBytes(int length) {
  final random = Random.secure();
  return Uint8List.fromList(
    List<int>.generate(length, (_) => random.nextInt(256)),
  );
}

class Vault {
  Vault._(this._file, this._key, this._salt, this._iterations);

  final File _file;
  Uint8List _key;
  Uint8List _salt;
  final int _iterations;

  File get file => _file;

  static final _aes = AesGcm.with256bits();

  static Future<File> defaultFile() async {
    final dir = await getApplicationSupportDirectory();
    await dir.create(recursive: true);
    return File('${dir.path}${Platform.pathSeparator}$kVaultFileName');
  }

  static Future<bool> exists() async => (await defaultFile()).exists();

  /// Creates a fresh vault, overwriting whatever is at [file].
  static Future<Vault> create({
    required String password,
    File? file,
    VaultData initial = const VaultData(),
  }) async {
    final target = file ?? await defaultFile();
    final salt = _randomBytes(16);
    final key = await _deriveKey(
      password: password,
      salt: salt,
      iterations: kKdfIterations,
    );
    final vault = Vault._(target, key, salt, kKdfIterations);
    await vault.save(initial);
    return vault;
  }

  static Future<Vault> open({required String password, File? file}) async {
    final target = file ?? await defaultFile();
    if (!await target.exists()) {
      throw const VaultException('Vault file not found');
    }

    final envelope = _readEnvelope(await target.readAsString());
    final kdf = envelope[_fieldKdf] as Map<String, dynamic>;
    final salt = base64Decode(kdf['salt'] as String);
    final iterations = (kdf['iterations'] as num).toInt();

    final key = await _deriveKey(
      password: password,
      salt: Uint8List.fromList(salt),
      iterations: iterations,
    );

    // Force a decrypt now so a bad password fails here and not on first read.
    await _decrypt(envelope, key);

    return Vault._(target, key, Uint8List.fromList(salt), iterations);
  }

  Future<VaultData> read() async {
    final envelope = _readEnvelope(await _file.readAsString());
    final clear = await _decrypt(envelope, _key);
    return VaultData.fromJson(jsonDecode(utf8.decode(clear)) as Map<String, dynamic>);
  }

  Future<void> save(VaultData data) async {
    final plain = utf8.encode(jsonEncode(data.toJson()));
    final nonce = _aes.newNonce();
    final box = await _aes.encrypt(
      plain,
      secretKey: SecretKey(_key),
      nonce: nonce,
    );

    final envelope = <String, dynamic>{
      _fieldFormat: kVaultFormat,
      _fieldVersion: kVaultVersion,
      _fieldKdf: {
        'algo': 'pbkdf2-hmac-sha256',
        'iterations': _iterations,
        'salt': base64Encode(_salt),
      },
      _fieldCipher: 'aes-256-gcm',
      _fieldNonce: base64Encode(nonce),
      _fieldMac: base64Encode(box.mac.bytes),
      _fieldData: base64Encode(box.cipherText),
    };

    // Write-then-rename so a crash mid-write cannot leave a truncated vault.
    final temp = File('${_file.path}.tmp');
    await temp.writeAsString(jsonEncode(envelope), flush: true);
    await temp.rename(_file.path);
  }

  /// Reads with the old key first, so this cannot reset a forgotten password.
  Future<void> changePassword(String newPassword) async {
    final data = await read();
    final salt = _randomBytes(16);
    _key = await _deriveKey(
      password: newPassword,
      salt: salt,
      iterations: _iterations,
    );
    _salt = salt;
    await save(data);
  }

  static Map<String, dynamic> _readEnvelope(String raw) {
    final Object? decoded;
    try {
      decoded = jsonDecode(raw);
    } catch (_) {
      throw const VaultException('Vault file is not valid JSON');
    }
    if (decoded is! Map<String, dynamic>) {
      throw const VaultException('Vault file has an unexpected shape');
    }
    if (decoded[_fieldFormat] != kVaultFormat) {
      throw const VaultException('Not an ohmyssh vault file');
    }
    final version = (decoded[_fieldVersion] as num?)?.toInt() ?? 0;
    if (version > kVaultVersion) {
      throw VaultException(
        'Vault was written by a newer version of the app (v$version)',
      );
    }
    return decoded;
  }

  static Future<List<int>> _decrypt(
    Map<String, dynamic> envelope,
    Uint8List key,
  ) async {
    try {
      return await _aes.decrypt(
        SecretBox(
          base64Decode(envelope[_fieldData] as String),
          nonce: base64Decode(envelope[_fieldNonce] as String),
          mac: Mac(base64Decode(envelope[_fieldMac] as String)),
        ),
        secretKey: SecretKey(key),
      );
    } on SecretBoxAuthenticationError {
      throw const WrongPasswordException();
    }
  }
}
