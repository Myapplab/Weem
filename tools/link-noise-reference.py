#!/usr/bin/env python3
"""Independent Noise reference for the 2-phone link (LINK.md 3.2, 3.14).

Written from the Noise specification (revision 34), not from the Kotlin code, on top of Python `cryptography`
(OpenSSL) and `hmac`/`hashlib`. It supports exactly what WEEM uses: patterns XX and XXpsk3, DH 25519 (official
vectors only) and P256 (WEEM: 65-byte SEC1 uncompressed public keys, 32-byte X as the DH output), cipher AESGCM,
hash SHA256.

  python3 -I tools/link-noise-reference.py --check logic/link/src/test/resources/noise/official-25519.json
  python3 -I tools/link-noise-reference.py --p256 logic/link/src/test/resources/noise/p256.json
  python3 -I tools/link-noise-reference.py --p256-verify logic/link/src/test/resources/noise/p256.json

--check verifies this implementation against the official vectors (cacophony, snow). --p256 writes WEEM's P-256
vectors (deterministic keys); the Kotlin test NoiseP256VectorsTest must reproduce them byte for byte. --p256-verify
regenerates them and compares with the file. Expected last line: "... OK".
"""
import hashlib
import hmac
import json
import sys

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec, x25519
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

HASHLEN = 32
MAX_NONCE = 2 ** 64 - 1
P256_ORDER = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551


class X25519Dh:
    name = '25519'
    publen = 32

    @staticmethod
    def keypair(private_hex):
        priv = x25519.X25519PrivateKey.from_private_bytes(bytes.fromhex(private_hex))
        pub = priv.public_key().public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        return priv, pub

    @staticmethod
    def dh(priv, pub):
        return priv.exchange(x25519.X25519PublicKey.from_public_bytes(pub))


class P256Dh:
    name = 'P256'
    publen = 65

    @staticmethod
    def keypair(private_hex):
        priv = ec.derive_private_key(int(private_hex, 16), ec.SECP256R1())
        pub = priv.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)
        return priv, pub

    @staticmethod
    def dh(priv, pub):
        if len(pub) != 65 or pub[0] != 4:
            raise ValueError('bad public key')
        peer = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), pub)  # rejects points off the curve
        out = priv.exchange(ec.ECDH(), peer)
        assert len(out) == 32
        return out


def hmac_sha256(key, data):
    return hmac.new(key, data, hashlib.sha256).digest()


def hkdf(ck, ikm, n):
    temp = hmac_sha256(ck, ikm)
    o1 = hmac_sha256(temp, b'\x01')
    o2 = hmac_sha256(temp, o1 + b'\x02')
    if n == 2:
        return o1, o2
    return o1, o2, hmac_sha256(temp, o2 + b'\x03')


class CipherState:
    def __init__(self):
        self.k = None
        self.n = 0

    def initialize_key(self, k):
        self.k = k
        self.n = 0

    def has_key(self):
        return self.k is not None

    def _nonce(self):
        if self.n >= MAX_NONCE:
            raise ValueError('nonce exhausted')
        return b'\x00\x00\x00\x00' + self.n.to_bytes(8, 'big')

    def encrypt_with_ad(self, ad, plaintext):
        if self.k is None:
            return plaintext
        out = AESGCM(self.k).encrypt(self._nonce(), plaintext, ad)
        self.n += 1
        return out

    def decrypt_with_ad(self, ad, ciphertext):
        if self.k is None:
            return ciphertext
        out = AESGCM(self.k).decrypt(self._nonce(), ciphertext, ad)
        self.n += 1
        return out


class SymmetricState:
    def __init__(self, protocol_name):
        name = protocol_name.encode('ascii')
        self.h = name.ljust(HASHLEN, b'\x00') if len(name) <= HASHLEN else hashlib.sha256(name).digest()
        self.ck = self.h
        self.cs = CipherState()

    def mix_key(self, ikm):
        self.ck, temp_k = hkdf(self.ck, ikm, 2)
        self.cs.initialize_key(temp_k)

    def mix_hash(self, data):
        self.h = hashlib.sha256(self.h + data).digest()

    def mix_key_and_hash(self, ikm):
        self.ck, temp_h, temp_k = hkdf(self.ck, ikm, 3)
        self.mix_hash(temp_h)
        self.cs.initialize_key(temp_k)

    def encrypt_and_hash(self, plaintext):
        ct = self.cs.encrypt_with_ad(self.h, plaintext)
        self.mix_hash(ct)
        return ct

    def decrypt_and_hash(self, ciphertext):
        pt = self.cs.decrypt_with_ad(self.h, ciphertext)
        self.mix_hash(ciphertext)
        return pt

    def split(self):
        k1, k2 = hkdf(self.ck, b'', 2)
        c1, c2 = CipherState(), CipherState()
        c1.initialize_key(k1)
        c2.initialize_key(k2)
        return c1, c2


PATTERNS = {
    'XX': [['e'], ['e', 'ee', 's', 'es'], ['s', 'se']],
    'XXpsk3': [['e'], ['e', 'ee', 's', 'es'], ['s', 'se', 'psk']],
}


class HandshakeState:
    def __init__(self, protocol_name, initiator, prologue, s, e, psk, dh):
        _, pattern, dh_name, cipher, hash_name = protocol_name.split('_')
        assert dh_name == dh.name and cipher == 'AESGCM' and hash_name == 'SHA256', protocol_name
        self.messages = [list(m) for m in PATTERNS[pattern]]
        self.psk_mode = 'psk' in pattern
        self.ss = SymmetricState(protocol_name)
        self.ss.mix_hash(prologue)
        self.initiator = initiator
        self.s, self.e = s, e  # (private, public); e is fixed for vectors
        self.rs = self.re = None
        self.psk = psk
        self.dh = dh

    def _dh_token(self, token):
        dh = self.dh
        if token == 'ee':
            self.ss.mix_key(dh.dh(self.e[0], self.re))
        elif token == 'es':
            self.ss.mix_key(dh.dh(self.e[0], self.rs) if self.initiator else dh.dh(self.s[0], self.re))
        elif token == 'se':
            self.ss.mix_key(dh.dh(self.s[0], self.re) if self.initiator else dh.dh(self.e[0], self.rs))
        elif token == 'ss':
            self.ss.mix_key(dh.dh(self.s[0], self.rs))
        elif token == 'psk':
            self.ss.mix_key_and_hash(self.psk)
        else:
            raise ValueError(token)

    def write_message(self, payload):
        out = b''
        for token in self.messages.pop(0):
            if token == 'e':
                out += self.e[1]
                self.ss.mix_hash(self.e[1])
                if self.psk_mode:
                    self.ss.mix_key(self.e[1])
            elif token == 's':
                out += self.ss.encrypt_and_hash(self.s[1])
            else:
                self._dh_token(token)
        out += self.ss.encrypt_and_hash(payload)
        return out, (self.ss.split() if not self.messages else None)

    def read_message(self, message):
        pos = 0
        for token in self.messages.pop(0):
            if token == 'e':
                self.re = message[pos:pos + self.dh.publen]
                pos += self.dh.publen
                self.ss.mix_hash(self.re)
                if self.psk_mode:
                    self.ss.mix_key(self.re)
            elif token == 's':
                n = self.dh.publen + (16 if self.ss.cs.has_key() else 0)
                self.rs = self.ss.decrypt_and_hash(message[pos:pos + n])
                pos += n
            else:
                self._dh_token(token)
        payload = self.ss.decrypt_and_hash(message[pos:])
        return payload, (self.ss.split() if not self.messages else None)


def run_vector(v, dh):
    """Plays a vector; returns (ciphertexts, handshake_hash). Messages alternate initiator/responder."""
    psks_i = [bytes.fromhex(p) for p in v.get('init_psks', [])]
    psks_r = [bytes.fromhex(p) for p in v.get('resp_psks', [])]
    init = HandshakeState(v['protocol_name'], True, bytes.fromhex(v['init_prologue']),
                          dh.keypair(v['init_static']), dh.keypair(v['init_ephemeral']), psks_i[0] if psks_i else None, dh)
    resp = HandshakeState(v['protocol_name'], False, bytes.fromhex(v['resp_prologue']),
                          dh.keypair(v['resp_static']), dh.keypair(v['resp_ephemeral']), psks_r[0] if psks_r else None, dh)
    out = []
    i_tx = i_rx = r_tx = r_rx = None
    hs_hash = None
    for idx, m in enumerate(v['messages']):
        payload = bytes.fromhex(m['payload'])
        from_init = idx % 2 == 0
        if i_tx is None:
            sender, receiver = (init, resp) if from_init else (resp, init)
            ct, split_w = sender.write_message(payload)
            got, split_r = receiver.read_message(ct)
            assert got == payload
            if split_w is not None:
                assert split_r is not None
                hs_hash = init.ss.h
                assert hs_hash == resp.ss.h
                i_tx, i_rx = (split_w if from_init else split_r)
                r_rx, r_tx = (split_r if from_init else split_w)
        else:
            if from_init:
                ct = i_tx.encrypt_with_ad(b'', payload)
                assert r_rx.decrypt_with_ad(b'', ct) == payload
            else:
                ct = r_tx.encrypt_with_ad(b'', payload)
                assert i_rx.decrypt_with_ad(b'', ct) == payload
        out.append(ct.hex())
    return out, hs_hash.hex() if hs_hash else None


def check_official(path):
    doc = json.load(open(path))
    n = 0
    for v in doc['vectors']:
        cts, hh = run_vector(v, X25519Dh)
        for idx, m in enumerate(v['messages']):
            if cts[idx] != m['ciphertext']:
                raise SystemExit(f"MISMATCH {v['source']} {v['protocol_name']} message {idx}")
        if 'handshake_hash' in v and v['handshake_hash'] != hh:
            raise SystemExit(f"MISMATCH {v['source']} {v['protocol_name']} handshake hash")
        n += 1
    print(f'official OK: {n} vectors')


def scalar(label):
    d = int.from_bytes(hashlib.sha256(('weem-link-vector/' + label).encode()).digest(), 'big')
    return '%064x' % (d % (P256_ORDER - 1) + 1)


def hello(device_hex, flags):
    return b'WEEM' + bytes([2]) + bytes.fromhex(device_hex) + bytes([flags])


def info(device_hex, major, minor, code, version, name):
    v, n = version.encode(), name.encode()
    return bytes.fromhex(device_hex) + bytes([major, minor]) + code.to_bytes(4, 'big') + bytes([len(v)]) + v + bytes([len(n)]) + n


def p256_vectors():
    a_id = '11111111111141118111111111111111'
    b_id = '22222222222242228222222222222222'
    out = []
    for name, pattern, flags in (('xx', 'XX', 0), ('pair', 'XXpsk3', 1), ('xx-swapped', 'XX', 0)):
        init_id, resp_id = (b_id, a_id) if name == 'xx-swapped' else (a_id, b_id)
        prologue = b'WEEM link 2' + hello(init_id, flags) + hello(resp_id, flags)
        v = {
            'name': name,
            'protocol_name': f'Noise_{pattern}_P256_AESGCM_SHA256',
            'init_prologue': prologue.hex(), 'resp_prologue': prologue.hex(),
        }
        for role in ('init_static', 'init_ephemeral', 'resp_static', 'resp_ephemeral'):
            v[role] = scalar(f'{name}/{role}')
            v[role + '_public'] = P256Dh.keypair(v[role])[1].hex()
        if pattern.endswith('psk3'):
            token = hashlib.sha256(b'weem-link-vector/pair/token').hexdigest()
            v['init_psks'] = [token]
            v['resp_psks'] = [token]
        # msg1 and msg2 carry no payload; msg3 carries the initiator's info; message 3 (the responder's first transport
        # message) is its key confirmation and carries the responder's info (LINK.md 3.4).
        payloads = [b'',
                    b'',
                    info(init_id, 1, 0, 34, '1.0.0', 'Pixel 10 Pro'),
                    info(resp_id, 1, 0, 34, '1.0.0', 'POCO X3'),
                    b'\x01' + b'zarf: ShiftState' * 3,
                    b'yanit' * 40,
                    bytes(range(256)) * 2]
        v['messages'] = [{'payload': p.hex()} for p in payloads]
        cts, hh = run_vector(v, P256Dh)
        for m, ct in zip(v['messages'], cts):
            m['ciphertext'] = ct
        v['handshake_hash'] = hh
        out.append(v)
    # ECDH known answers (static keys of the first vector, both directions) for P256Test.
    a = P256Dh.keypair(out[0]['init_static'])
    b = P256Dh.keypair(out[0]['resp_static'])
    ecdh = {'a_private': out[0]['init_static'], 'a_public': a[1].hex(), 'b_private': out[0]['resp_static'],
            'b_public': b[1].hex(), 'shared': P256Dh.dh(a[0], b[1]).hex()}
    assert P256Dh.dh(b[0], a[1]).hex() == ecdh['shared']
    return {'about': 'WEEM P-256 Noise vectors from tools/link-noise-reference.py (python cryptography/OpenSSL); '
                     'LINK.md 3.2, 3.14. Messages alternate initiator/responder starting with the initiator; message 3 '
                     'is the responder\'s key confirmation carrying its info (LINK.md 3.4).',
            'vectors': out, 'ecdh': ecdh}


def main(argv):
    if len(argv) == 3 and argv[1] == '--check':
        check_official(argv[2])
    elif len(argv) == 3 and argv[1] == '--p256':
        doc = p256_vectors()
        with open(argv[2], 'w') as f:
            json.dump(doc, f, indent=1, sort_keys=True)
            f.write('\n')
        print(f"p256 written: {len(doc['vectors'])} vectors OK")
    elif len(argv) == 3 and argv[1] == '--p256-verify':
        if json.load(open(argv[2])) != json.loads(json.dumps(p256_vectors())):
            raise SystemExit('MISMATCH p256 vectors')
        print('p256 verify OK')
    else:
        raise SystemExit(__doc__)


if __name__ == '__main__':
    main(sys.argv)
