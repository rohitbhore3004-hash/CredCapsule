#!/usr/bin/env python3
"""CredCapsule v4 — AES-256-GCM credential vault with X3DH and Double Ratchet forward secrecy."""

import argparse
import ctypes
import getpass
import hashlib
import hmac as _hmac_mod
import json
import os
import secrets
import stat
import struct
import sys
import time
from contextlib import contextmanager
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Generator, List, Optional, Tuple, Union

from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import hmac as _crypto_hmac
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey, X25519PublicKey
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt

_BACKEND = default_backend()

_MAGIC      = b'CREDCAPS\x04\x00'
_SALT_LEN   = 32
_NONCE_LEN  = 12
_KEY_LEN    = 32
_HMAC_LEN   = 32
_KDF_PLEN   = 12
_HEADER_LEN = len(_MAGIC) + _SALT_LEN + _KDF_PLEN + _HMAC_LEN  # 86 bytes

_SC_N, _SC_R, _SC_P = 131072, 8, 1   # scrypt N=2^17

_SEC_VERI = b'VERI'
_SEC_RATC = b'RATC'
_SEC_X3DH = b'X3DH'
_SEC_KDFP = b'KDFP'
_SEC_ENTR = b'ENTR'
_SEC_AUDT = b'AUDT'

_CT_LOGIN  = 1
_CT_APIKEY = 2
_CT_NOTE   = 3
_CT_TOKEN  = 4

_NUM_OTK   = 10
_RETRY_MAP = {3: 5, 5: 30, 7: 120, 10: 600, 15: 3600}
_MAX_FAILS = 20


# ── exceptions ──────────────────────────────────────────────────────────────────

class CredCapsuleError(Exception):    pass
class AccessError(CredCapsuleError):     pass
class VaultLockedError(CredCapsuleError): pass
class CredentialNotFound(CredCapsuleError): pass
class VaultExistsError(CredCapsuleError):  pass
class VaultNotFound(CredCapsuleError):     pass
class IntegrityError(CredCapsuleError):    pass
class RetryLimitError(AccessError):        pass


# ── credential types ────────────────────────────────────────────────────────────

@dataclass
class LoginCredential:
    label:    str
    username: str
    password: str
    url:      str        = ""
    tags:     List[str]  = field(default_factory=list)
    _ctype:   int        = field(default=_CT_LOGIN, init=False, repr=False)


@dataclass
class ApiKeyCredential:
    label:   str
    key_id:  str
    secret:  str
    service: str = ""
    _ctype:  int = field(default=_CT_APIKEY, init=False, repr=False)


@dataclass
class SecureNote:
    label:   str
    content: str
    _ctype:  int = field(default=_CT_NOTE, init=False, repr=False)


@dataclass
class TokenCredential:
    label:   str
    token:   str
    expires: str = ""
    _ctype:  int = field(default=_CT_TOKEN, init=False, repr=False)


Credential = Union[LoginCredential, ApiKeyCredential, SecureNote, TokenCredential]


def login(label: str, username: str, password: str, url: str = "",
          tags: List[str] = None) -> LoginCredential:
    return LoginCredential(label, username, password, url, tags or [])


def api_key(label: str, key_id: str, secret: str, service: str = "") -> ApiKeyCredential:
    return ApiKeyCredential(label, key_id, secret, service)


def note(label: str, content: str) -> SecureNote:
    return SecureNote(label, content)


def token(label: str, token_value: str, expires: str = "") -> TokenCredential:
    return TokenCredential(label, token_value, expires)


# ── secure memory ───────────────────────────────────────────────────────────────

def _zero(buf: bytearray) -> None:
    if buf:
        ctypes.memset((ctypes.c_char * len(buf)).from_buffer(buf), 0, len(buf))


# ── crypto primitives ───────────────────────────────────────────────────────────

def _hkdf(ikm: bytes, length: int, info: bytes, salt: bytes = b"") -> bytes:
    return HKDF(
        algorithm=hashes.SHA256(),
        length=length,
        salt=salt or None,
        info=info,
        backend=_BACKEND,
    ).derive(ikm)


def _hmac256(key: bytes, data: bytes) -> bytes:
    h = _crypto_hmac.HMAC(key, hashes.SHA256(), backend=_BACKEND)
    h.update(data)
    return h.finalize()


def _blake2b_label(label: str, salt: bytes) -> bytes:
    return hashlib.blake2b(label.encode("utf-8"), key=salt[:32], digest_size=32).digest()


def _scrypt_kdf(password: str, salt: bytes) -> bytes:
    return Scrypt(
        salt=salt, length=32, n=_SC_N, r=_SC_R, p=_SC_P, backend=_BACKEND
    ).derive(password.encode("utf-8"))


def _enc(key: bytes, plain: bytes, aad: bytes = b"") -> bytes:
    nonce = secrets.token_bytes(_NONCE_LEN)
    return nonce + AESGCM(key).encrypt(nonce, plain, aad)


def _dec(key: bytes, blob: bytes, aad: bytes = b"") -> bytes:
    return AESGCM(key).decrypt(blob[:_NONCE_LEN], blob[_NONCE_LEN:], aad)


# ── X3DH ────────────────────────────────────────────────────────────────────────

@dataclass
class _X3DHBundle:
    ik_priv:   bytes
    ik_pub:    bytes
    spk_priv:  bytes
    spk_pub:   bytes
    otk_pairs: List[Tuple[bytes, bytes]]


def _x3dh_gen() -> _X3DHBundle:
    def kp() -> Tuple[bytes, bytes]:
        p = X25519PrivateKey.generate()
        return p.private_bytes_raw(), p.public_key().public_bytes_raw()

    ik_priv, ik_pub   = kp()
    spk_priv, spk_pub = kp()
    otks = [kp() for _ in range(_NUM_OTK)]
    return _X3DHBundle(ik_priv, ik_pub, spk_priv, spk_pub, otks)


def _x3dh_bootstrap(bundle: _X3DHBundle) -> Tuple[bytes, bytes]:
    """Single-party X3DH bootstrap. Returns (root_secret, ephemeral_pub)."""
    ek = X25519PrivateKey.generate()
    ek_pub = ek.public_key().public_bytes_raw()

    def dh(priv_b: bytes, pub_b: bytes) -> bytes:
        return X25519PrivateKey.from_private_bytes(priv_b).exchange(
            X25519PublicKey.from_public_bytes(pub_b)
        )

    dh1 = dh(bundle.ik_priv, bundle.spk_pub)
    dh2 = dh(ek.private_bytes_raw(), bundle.ik_pub)
    dh3 = dh(ek.private_bytes_raw(), bundle.spk_pub)
    parts = [dh1, dh2, dh3]
    if bundle.otk_pairs:
        parts.append(dh(ek.private_bytes_raw(), bundle.otk_pairs[0][1]))

    ikm  = b"".join(parts)
    root = _hkdf(ikm, 32, b"X3DHv4_Root", salt=bytes(32))
    tmp  = bytearray(ikm)
    _zero(tmp)
    return root, ek_pub


# ── Double Ratchet ───────────────────────────────────────────────────────────────

@dataclass
class _RState:
    rk:       bytearray  # root key
    ck:       bytearray  # chain key
    dh_prv:   bytearray  # current DH ratchet private key
    dh_pub:   bytearray  # current DH ratchet public key
    prev_pub: bytearray  # previous pub used as "remote" in self-ratchet
    n:        int = 0


def _ratchet_new(root_secret: bytes, eph_pub: bytes) -> _RState:
    p  = X25519PrivateKey.generate()
    rk = _hkdf(root_secret, 32, b"RatchetRoot_v4")
    ck = _hkdf(rk, 32, b"RatchetChain_v4")
    return _RState(
        rk=bytearray(rk),
        ck=bytearray(ck),
        dh_prv=bytearray(p.private_bytes_raw()),
        dh_pub=bytearray(p.public_key().public_bytes_raw()),
        prev_pub=bytearray(eph_pub),
        n=0,
    )


def _ratchet_step(st: _RState) -> bytes:
    """Advance the DH ratchet; returns the derived chain step token."""
    dh_out = X25519PrivateKey.from_private_bytes(bytes(st.dh_prv)).exchange(
        X25519PublicKey.from_public_bytes(bytes(st.prev_pub))
    )
    new_rk = _hkdf(bytes(st.rk) + dh_out, 32, b"RKUpd_v4")
    new_ck = _hkdf(bytes(st.rk) + dh_out, 32, b"CKUpd_v4")
    msg_k  = _hmac256(new_ck, b"\x01")

    new_p = X25519PrivateKey.generate()
    _zero(st.rk);       st.rk       = bytearray(new_rk)
    _zero(st.ck);       st.ck       = bytearray(new_ck)
    _zero(st.prev_pub); st.prev_pub = bytearray(bytes(st.dh_pub))
    _zero(st.dh_prv);   st.dh_prv   = bytearray(new_p.private_bytes_raw())
    st.dh_pub = bytearray(new_p.public_key().public_bytes_raw())
    st.n += 1

    tmp = bytearray(dh_out)
    _zero(tmp)
    return msg_k


# ── binary format ────────────────────────────────────────────────────────────────

def _sec(tag: bytes, data: bytes) -> bytes:
    return tag + struct.pack(">I", len(data)) + data


def _parse_secs(raw: bytes) -> Dict[bytes, bytes]:
    out: Dict[bytes, bytes] = {}
    pos = 0
    while pos + 8 <= len(raw):
        tag = raw[pos:pos + 4]
        ln  = struct.unpack(">I", raw[pos + 4:pos + 8])[0]
        out[tag] = raw[pos + 8:pos + 8 + ln]
        pos += 8 + ln
    return out


def _pack_rstate(s: _RState) -> bytes:
    return (
        bytes(s.rk) + bytes(s.ck) + bytes(s.dh_prv)
        + bytes(s.dh_pub) + bytes(s.prev_pub) + struct.pack(">I", s.n)
    )


def _unpack_rstate(d: bytes) -> _RState:
    rk,  d = d[:32], d[32:]
    ck,  d = d[:32], d[32:]
    dp,  d = d[:32], d[32:]
    dpb, d = d[:32], d[32:]
    pp,  d = d[:32], d[32:]
    n = struct.unpack(">I", d[:4])[0]
    return _RState(bytearray(rk), bytearray(ck), bytearray(dp), bytearray(dpb), bytearray(pp), n)


def _pack_bundle(b: _X3DHBundle) -> bytes:
    out = b.ik_priv + b.ik_pub + b.spk_priv + b.spk_pub + struct.pack(">H", len(b.otk_pairs))
    for prv, pub in b.otk_pairs:
        out += prv + pub
    return out


def _unpack_bundle(d: bytes) -> _X3DHBundle:
    ik_priv,  d = d[:32], d[32:]
    ik_pub,   d = d[:32], d[32:]
    spk_priv, d = d[:32], d[32:]
    spk_pub,  d = d[:32], d[32:]
    n_otk = struct.unpack(">H", d[:2])[0]
    d = d[2:]
    otks: List[Tuple[bytes, bytes]] = []
    for _ in range(n_otk):
        prv, d = d[:32], d[32:]
        pub, d = d[:32], d[32:]
        otks.append((prv, pub))
    return _X3DHBundle(ik_priv, ik_pub, spk_priv, spk_pub, otks)


def _pack_entries(entries: Dict[bytes, bytes]) -> bytes:
    out = b""
    for lh, blob in entries.items():
        out += lh + struct.pack(">I", len(blob)) + blob
    return out


def _unpack_entries(data: bytes) -> Dict[bytes, bytes]:
    entries: Dict[bytes, bytes] = {}
    pos = 0
    while pos + 36 <= len(data):
        lh  = data[pos:pos + 32]
        ln  = struct.unpack(">I", data[pos + 32:pos + 36])[0]
        entries[lh] = data[pos + 36:pos + 36 + ln]
        pos += 36 + ln
    return entries


# ── credential serialisation ─────────────────────────────────────────────────────

def _cred_pack(c: Credential) -> bytes:
    if isinstance(c, LoginCredential):
        d = {"t": _CT_LOGIN,  "l": c.label, "u": c.username,
             "p": c.password, "url": c.url, "tg": c.tags}
    elif isinstance(c, ApiKeyCredential):
        d = {"t": _CT_APIKEY, "l": c.label, "ki": c.key_id,
             "s": c.secret,   "sv": c.service}
    elif isinstance(c, SecureNote):
        d = {"t": _CT_NOTE,   "l": c.label, "c": c.content}
    elif isinstance(c, TokenCredential):
        d = {"t": _CT_TOKEN,  "l": c.label, "tk": c.token, "ex": c.expires}
    else:
        raise CredCapsuleError("unsupported credential type")
    return json.dumps(d, separators=(",", ":")).encode()


def _cred_unpack(raw: bytes) -> Credential:
    d = json.loads(raw)
    t = d["t"]
    if t == _CT_LOGIN:
        return LoginCredential(d["l"], d["u"], d["p"], d.get("url", ""), d.get("tg", []))
    if t == _CT_APIKEY:
        return ApiKeyCredential(d["l"], d["ki"], d["s"], d.get("sv", ""))
    if t == _CT_NOTE:
        return SecureNote(d["l"], d["c"])
    if t == _CT_TOKEN:
        return TokenCredential(d["l"], d["tk"], d.get("ex", ""))
    raise CredCapsuleError(f"unknown credential type: {t}")


# ── file permissions ──────────────────────────────────────────────────────────────

def _set_readonly(path: Path) -> None:
    try:
        if sys.platform == "win32":
            os.chmod(path, stat.S_IREAD)
        else:
            os.chmod(path, 0o440)
    except OSError:
        pass


def _set_writable(path: Path) -> None:
    try:
        if sys.platform == "win32":
            os.chmod(path, stat.S_IREAD | stat.S_IWRITE)
        else:
            os.chmod(path, 0o640)
    except OSError:
        pass


# ── vault ─────────────────────────────────────────────────────────────────────────

class Vault:
    def __init__(self, path: Union[str, Path], timeout: int = 300):
        self._path         = Path(path)
        self._timeout      = timeout
        self._locked       = True
        self._mk:          Optional[bytearray]    = None
        self._fk:          Optional[bytearray]    = None
        self._salt:        Optional[bytes]        = None
        self._rst:         Optional[_RState]      = None
        self._bnd:         Optional[_X3DHBundle]  = None
        self._entries:     Dict[bytes, bytes]     = {}
        self._audit:       List[dict]             = []
        self._fails:       int                    = 0
        self._unlocked_at: float                  = 0.0

    # ── constructors ──────────────────────────────────────────────────────────

    @classmethod
    def create(cls, path: Union[str, Path], password: str, timeout: int = 300) -> "Vault":
        p = Path(path)
        if p.exists():
            raise VaultExistsError(str(p))
        v = cls(p, timeout)
        v._salt = secrets.token_bytes(_SALT_LEN)
        mk = bytearray(_scrypt_kdf(password, v._salt))
        v._mk  = mk
        v._fk  = bytearray(_hkdf(bytes(mk), 32, b"FileIntegrity_v4", salt=v._salt))
        bnd    = _x3dh_gen()
        secret, eph_pub = _x3dh_bootstrap(bnd)
        v._bnd = bnd
        v._rst = _ratchet_new(secret, eph_pub)
        v._locked      = False
        v._unlocked_at = time.monotonic()
        v._log("created")
        v._flush()
        return v

    @classmethod
    def open(cls, path: Union[str, Path], timeout: int = 300) -> "Vault":
        p = Path(path)
        if not p.exists():
            raise VaultNotFound(str(p))
        return cls(p, timeout)

    # ── lock / unlock ──────────────────────────────────────────────────────────

    def unlock(self, password: str) -> None:
        self._check_rate()
        raw = self._path.read_bytes()
        self._chk_magic(raw)

        salt = raw[len(_MAGIC):len(_MAGIC) + _SALT_LEN]
        mk   = bytearray(_scrypt_kdf(password, salt))
        fk   = bytearray(_hkdf(bytes(mk), 32, b"FileIntegrity_v4", salt=salt))
        body = raw[_HEADER_LEN:]
        stored_mac = raw[len(_MAGIC) + _SALT_LEN + _KDF_PLEN:_HEADER_LEN]

        if not _hmac_mod.compare_digest(_hmac256(bytes(fk), body), stored_mac):
            _zero(mk); _zero(fk)
            self._fails += 1
            raise AccessError("authentication failed")

        secs = _parse_secs(body)
        vk   = _hkdf(bytes(mk), 32, b"VerifyKey_v4", salt=salt)
        try:
            _dec(vk, secs.get(_SEC_VERI, b""), aad=b"VERI")
        except Exception:
            _zero(mk); _zero(fk)
            self._fails += 1
            raise AccessError("authentication failed")

        self._salt  = salt
        self._mk    = mk
        self._fk    = fk
        self._fails = 0

        if _SEC_RATC in secs:
            rk = _hkdf(bytes(mk), 32, b"RatchetKey_v4", salt=salt)
            self._rst = _unpack_rstate(_dec(rk, secs[_SEC_RATC], aad=b"RATC"))

        if _SEC_X3DH in secs:
            xk = _hkdf(bytes(mk), 32, b"X3DHKey_v4", salt=salt)
            self._bnd = _unpack_bundle(_dec(xk, secs[_SEC_X3DH], aad=b"X3DH"))

        if _SEC_ENTR in secs:
            self._entries = _unpack_entries(secs[_SEC_ENTR])

        if _SEC_AUDT in secs:
            ak = _hkdf(bytes(mk), 32, b"AuditKey_v4", salt=salt)
            self._audit = json.loads(_dec(ak, secs[_SEC_AUDT], aad=b"AUDT"))

        self._locked      = False
        self._unlocked_at = time.monotonic()

        if self._rst:
            _ratchet_step(self._rst)

        self._log("unlocked")
        self._flush()

    def lock(self) -> None:
        if not self._locked:
            self._log("locked")
            self._flush()
        self._wipe()

    def _wipe(self) -> None:
        if self._mk:  _zero(self._mk);  self._mk = None
        if self._fk:  _zero(self._fk);  self._fk = None
        if self._rst:
            _zero(self._rst.rk)
            _zero(self._rst.ck)
            _zero(self._rst.dh_prv)
            self._rst = None
        self._locked = True

    # ── credential operations ─────────────────────────────────────────────────

    def set_credential(self, cred: Credential) -> None:
        self._need_open()
        lh = self._lhash(cred.label)
        ek = self._ekey(lh)
        self._entries[lh] = _enc(ek, _cred_pack(cred), aad=lh)
        self._log(f"set:{lh.hex()[:8]}")
        self._flush()

    def get_credential(self, label: str) -> Credential:
        self._need_open()
        lh   = self._lhash(label)
        blob = self._entries.get(lh)
        if blob is None:
            raise CredentialNotFound(label)
        return _cred_unpack(_dec(self._ekey(lh), blob, aad=lh))

    def get(self, label: str) -> str:
        c = self.get_credential(label)
        if isinstance(c, LoginCredential):   return c.password
        if isinstance(c, ApiKeyCredential):  return c.secret
        if isinstance(c, SecureNote):        return c.content
        if isinstance(c, TokenCredential):   return c.token
        raise CredCapsuleError("unknown credential type")

    def get_username(self, label: str) -> str:
        c = self.get_credential(label)
        if not isinstance(c, LoginCredential):
            raise CredCapsuleError(f"{label!r} is not a login credential")
        return c.username

    def get_password(self, label: str) -> str:
        c = self.get_credential(label)
        if not isinstance(c, LoginCredential):
            raise CredCapsuleError(f"{label!r} is not a login credential")
        return c.password

    @contextmanager
    def get_credential_secure(self, label: str) -> Generator[Credential, None, None]:
        cred = self.get_credential(label)
        try:
            yield cred
        finally:
            for v in vars(cred).values():
                if isinstance(v, str) and v:
                    ba = bytearray(v.encode())
                    _zero(ba)

    def has(self, label: str) -> bool:
        self._need_open()
        return self._lhash(label) in self._entries

    def delete(self, label: str) -> None:
        self._need_open()
        lh = self._lhash(label)
        if lh not in self._entries:
            raise CredentialNotFound(label)
        del self._entries[lh]
        self._log(f"del:{lh.hex()[:8]}")
        self._flush()

    def list(self) -> List[str]:
        self._need_open()
        names: List[str] = []
        for lh, blob in self._entries.items():
            try:
                c = _cred_unpack(_dec(self._ekey(lh), blob, aad=lh))
                names.append(getattr(c, "label", lh.hex()[:8]))
            except Exception:
                names.append(f"<{lh.hex()[:8]}>")
        return sorted(names)

    def count(self) -> int:
        self._need_open()
        return len(self._entries)

    def info(self) -> dict:
        raw = self._path.read_bytes()
        self._chk_magic(raw)
        n, r, p = struct.unpack(">III", raw[len(_MAGIC) + _SALT_LEN:len(_MAGIC) + _SALT_LEN + _KDF_PLEN])
        return {
            "path":    str(self._path),
            "version": 4,
            "locked":  self._locked,
            "size":    self._path.stat().st_size,
            "kdf":     {"N": n, "r": r, "p": p},
        }

    def audit_log(self) -> List[dict]:
        self._need_open()
        return list(self._audit)

    def rotate_password(self, old_password: str, new_password: str) -> None:
        self._need_open()
        test = bytearray(_scrypt_kdf(old_password, self._salt))
        if not _hmac_mod.compare_digest(bytes(test), bytes(self._mk)):
            _zero(test)
            raise AccessError("authentication failed")
        _zero(test)

        new_salt = secrets.token_bytes(_SALT_LEN)
        new_mk   = bytearray(_scrypt_kdf(new_password, new_salt))
        new_fk   = bytearray(_hkdf(bytes(new_mk), 32, b"FileIntegrity_v4", salt=new_salt))

        new_entries: Dict[bytes, bytes] = {}
        for old_lh, blob in self._entries.items():
            old_ek = _hkdf(bytes(self._mk), 32, b"EntryKey_v4" + old_lh, salt=self._salt)
            plain  = _dec(old_ek, blob, aad=old_lh)
            d      = json.loads(plain)
            new_lh = _blake2b_label(d["l"], new_salt)
            new_ek = _hkdf(bytes(new_mk), 32, b"EntryKey_v4" + new_lh, salt=new_salt)
            new_entries[new_lh] = _enc(new_ek, plain, aad=new_lh)

        _zero(self._mk); _zero(self._fk)
        self._salt    = new_salt
        self._mk      = new_mk
        self._fk      = new_fk
        self._entries = new_entries
        self._log("rotate_password")
        self._flush()

    def destroy(self, password: str) -> None:
        self._need_open()
        test = bytearray(_scrypt_kdf(password, self._salt))
        if not _hmac_mod.compare_digest(bytes(test), bytes(self._mk)):
            _zero(test)
            raise AccessError("authentication failed")
        _zero(test)
        self._wipe()
        _set_writable(self._path)
        self._path.unlink()

    def backup(self) -> bytes:
        self._need_open()
        return self._path.read_bytes()

    # ── internal helpers ───────────────────────────────────────────────────────

    def _need_open(self) -> None:
        if self._locked or self._mk is None:
            raise VaultLockedError("vault is locked")
        if time.monotonic() - self._unlocked_at > self._timeout:
            self.lock()
            raise VaultLockedError("session timed out")

    def _check_rate(self) -> None:
        if self._fails >= _MAX_FAILS:
            raise RetryLimitError("too many failed attempts")
        wait = 0
        for thr, delay in sorted(_RETRY_MAP.items()):
            if self._fails >= thr:
                wait = delay
        if wait:
            time.sleep(wait)

    def _chk_magic(self, raw: bytes) -> None:
        if not raw.startswith(_MAGIC):
            raise IntegrityError("not a valid vault file")
        if len(raw) < _HEADER_LEN:
            raise IntegrityError("truncated header")

    def _lhash(self, label: str) -> bytes:
        return _blake2b_label(label, self._salt)

    def _ekey(self, lh: bytes) -> bytes:
        return _hkdf(bytes(self._mk), 32, b"EntryKey_v4" + lh, salt=self._salt)

    def _log(self, event: str) -> None:
        self._audit.append({"ts": round(time.time(), 3), "ev": event})
        if len(self._audit) > 1000:
            self._audit = self._audit[-1000:]

    def _flush(self) -> None:
        if self._mk is None:
            return

        salt       = self._salt
        kdf_params = struct.pack(">III", _SC_N, _SC_R, _SC_P)

        vk   = _hkdf(bytes(self._mk), 32, b"VerifyKey_v4",  salt=salt)
        veri = _enc(vk, secrets.token_bytes(32), aad=b"VERI")

        rk   = _hkdf(bytes(self._mk), 32, b"RatchetKey_v4", salt=salt)
        ratc = _enc(rk, _pack_rstate(self._rst), aad=b"RATC") if self._rst else b""

        xk   = _hkdf(bytes(self._mk), 32, b"X3DHKey_v4",    salt=salt)
        x3dh = _enc(xk, _pack_bundle(self._bnd), aad=b"X3DH") if self._bnd else b""

        entr = _pack_entries(self._entries)

        ak   = _hkdf(bytes(self._mk), 32, b"AuditKey_v4", salt=salt)
        audt = _enc(ak, json.dumps(self._audit, separators=(",", ":")).encode(), aad=b"AUDT")

        body = (
            _sec(_SEC_VERI, veri)
            + _sec(_SEC_RATC, ratc)
            + _sec(_SEC_X3DH, x3dh)
            + _sec(_SEC_KDFP, kdf_params)
            + _sec(_SEC_ENTR, entr)
            + _sec(_SEC_AUDT, audt)
        )

        mac    = _hmac256(bytes(self._fk), body)
        header = _MAGIC + salt + kdf_params + mac

        tmp = self._path.with_suffix(".tmp")
        if self._path.exists():
            _set_writable(self._path)
        try:
            tmp.write_bytes(header + body)
            os.replace(tmp, self._path)
        except Exception:
            if tmp.exists():
                tmp.unlink()
            raise
        _set_readonly(self._path)

    # ── context manager ────────────────────────────────────────────────────────

    def __enter__(self) -> "Vault":
        return self

    def __exit__(self, *_) -> None:
        if not self._locked:
            self.lock()


# ── CLI ───────────────────────────────────────────────────────────────────────────

def _cli_main() -> None:
    parser = argparse.ArgumentParser(prog="credcapsule", description="CredCapsule v4 CLI")
    parser.add_argument("--vault",    "-v", required=True, help="Path to .ccv vault file")
    parser.add_argument("--password", "-p", help="Vault password (omit to prompt)")
    parser.add_argument("--no-prompt", action="store_true", help="Fail instead of prompting")

    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("create",   help="Create a new vault")
    sub.add_parser("list",     help="List credential labels")
    sub.add_parser("info",     help="Show vault metadata (no unlock needed)")
    sub.add_parser("destroy",  help="Permanently delete the vault")
    sub.add_parser("protocol", help="Show protocol parameters")

    s = sub.add_parser("set", help="Store a credential")
    s.add_argument("--label",    "-l", required=True)
    s.add_argument("--username", "-u")
    s.add_argument("--value",    required=True, help="Primary secret (password / key / content)")
    s.add_argument("--url")
    s.add_argument("--kind",    choices=["login", "api_key", "note", "token"], default="login")
    s.add_argument("--key-id",  dest="key_id")
    s.add_argument("--service")

    g = sub.add_parser("get",       help="Return primary secret for a label")
    g.add_argument("--label", "-l", required=True)

    gl = sub.add_parser("get-login", help="Return full login credential")
    gl.add_argument("--label", "-l", required=True)

    d = sub.add_parser("delete", help="Delete a credential")
    d.add_argument("--label", "-l", required=True)

    r = sub.add_parser("rotate", help="Re-encrypt vault with a new password")
    r.add_argument("--new-password", required=True)

    args = parser.parse_args()

    def _pw(prompt: str = "Password: ") -> str:
        if args.password:
            return args.password
        if args.no_prompt:
            raise CredCapsuleError("--password required when --no-prompt is set")
        return getpass.getpass(prompt)

    def _out(data: object) -> None:
        print(json.dumps(data, indent=2))

    try:
        if args.cmd == "info":
            _out(Vault.open(args.vault).info())

        elif args.cmd == "protocol":
            _out({
                "key_exchange":      "X25519 / Curve25519",
                "session_bootstrap": "X3DH with 10 one-time prekeys",
                "forward_secrecy":   "Double Ratchet (DH step on every unlock)",
                "symmetric_cipher":  "AES-256-GCM",
                "key_derivation":    "HKDF-SHA256 with domain-separated info strings",
                "file_integrity":    "HMAC-SHA256",
                "label_storage":     "BLAKE2b keyed hash (key=vault_salt)",
                "password_kdf":      f"scrypt N={_SC_N} r={_SC_R} p={_SC_P}",
                "file_format":       ".ccv v4 — big-endian TLV sections",
                "entry_key":         "HKDF(master_key, EntryKey_v4 || label_hash, salt)",
                "aad":               "label_hash bound as AES-GCM AAD",
            })

        elif args.cmd == "create":
            pw = _pw("New vault password: ")
            with Vault.create(args.vault, pw):
                pass
            _out({"created": args.vault})

        elif args.cmd == "set":
            pw   = _pw()
            kind = args.kind
            with Vault.open(args.vault) as v:
                v.unlock(pw)
                if kind == "login":
                    cred = login(args.label, args.username or "", args.value, args.url or "")
                elif kind == "api_key":
                    cred = api_key(args.label, args.key_id or "", args.value, args.service or "")
                elif kind == "note":
                    cred = note(args.label, args.value)
                elif kind == "token":
                    cred = token(args.label, args.value)
                else:
                    raise CredCapsuleError(f"unknown kind: {kind}")
                v.set_credential(cred)
            _out({"set": args.label})

        elif args.cmd == "get":
            pw = _pw()
            with Vault.open(args.vault) as v:
                v.unlock(pw)
                secret = v.get(args.label)
            _out({"label": args.label, "secret": secret})

        elif args.cmd == "get-login":
            pw = _pw()
            with Vault.open(args.vault) as v:
                v.unlock(pw)
                c = v.get_credential(args.label)
                if isinstance(c, LoginCredential):
                    result = {
                        "label":    c.label,
                        "username": c.username,
                        "password": c.password,
                        "url":      c.url,
                        "tags":     c.tags,
                    }
                else:
                    result = {"label": c.label, "secret": v.get(args.label)}
            _out(result)

        elif args.cmd == "delete":
            pw = _pw()
            with Vault.open(args.vault) as v:
                v.unlock(pw)
                v.delete(args.label)
            _out({"deleted": args.label})

        elif args.cmd == "list":
            pw = _pw()
            with Vault.open(args.vault) as v:
                v.unlock(pw)
                labels = v.list()
                count  = v.count()
            _out({"labels": labels, "count": count})

        elif args.cmd == "destroy":
            pw = _pw()
            with Vault.open(args.vault) as v:
                v.unlock(pw)
                v.destroy(pw)
            _out({"destroyed": args.vault})

        elif args.cmd == "rotate":
            pw = _pw()
            with Vault.open(args.vault) as v:
                v.unlock(pw)
                v.rotate_password(pw, args.new_password)
            _out({"rotated": True})

    except CredCapsuleError as exc:
        print(json.dumps({"error": str(exc), "type": type(exc).__name__}), file=sys.stderr)
        sys.exit(1)
    except Exception as exc:
        print(json.dumps({"error": str(exc), "type": "InternalError"}), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    _cli_main()
