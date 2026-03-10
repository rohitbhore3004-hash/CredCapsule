"""CredCapsule v4 — pytest suite."""

import os
import struct
import time
import pytest
from pathlib import Path

import sys
sys.path.insert(0, str(Path(__file__).parent.parent))

from credcapsule_single import (
    Vault,
    LoginCredential, ApiKeyCredential, SecureNote, TokenCredential,
    login, api_key, note, token,
    AccessError, CredentialNotFound, IntegrityError,
    RetryLimitError, VaultExistsError, VaultLockedError, VaultNotFound,
    _MAGIC, _HEADER_LEN,
)

PW = "correct-horse-battery"
PW2 = "new-super-secret-pw"


# ── fixtures ──────────────────────────────────────────────────────────────────

@pytest.fixture
def vpath(tmp_path) -> Path:
    return tmp_path / "test.ccv"


@pytest.fixture
def vault(vpath) -> Vault:
    v = Vault.create(vpath, PW)
    yield v
    if not v._locked:
        v.lock()


@pytest.fixture
def locked_vault(vpath) -> Path:
    with Vault.create(vpath, PW):
        pass
    return vpath


# ── creation ──────────────────────────────────────────────────────────────────

class TestCreate:
    def test_file_created(self, vpath):
        Vault.create(vpath, PW).lock()
        assert vpath.exists()

    def test_magic_header(self, vpath):
        Vault.create(vpath, PW).lock()
        raw = vpath.read_bytes()
        assert raw[:10] == _MAGIC

    def test_minimum_size(self, vpath):
        Vault.create(vpath, PW).lock()
        assert vpath.stat().st_size >= _HEADER_LEN

    def test_empty_vault_size(self, vpath):
        Vault.create(vpath, PW).lock()
        assert vpath.stat().st_size < 4096, "empty vault should be under 4 KB"

    def test_duplicate_raises(self, vpath):
        Vault.create(vpath, PW).lock()
        with pytest.raises(VaultExistsError):
            Vault.create(vpath, PW)

    def test_open_nonexistent_raises(self, tmp_path):
        with pytest.raises(VaultNotFound):
            Vault.open(tmp_path / "ghost.ccv")


# ── unlock / lock ─────────────────────────────────────────────────────────────

class TestUnlock:
    def test_wrong_password_raises(self, locked_vault):
        v = Vault.open(locked_vault)
        with pytest.raises(AccessError):
            v.unlock("wrong-password")

    def test_correct_password_succeeds(self, locked_vault):
        v = Vault.open(locked_vault)
        v.unlock(PW)
        assert not v._locked
        v.lock()

    def test_locked_after_lock(self, locked_vault):
        v = Vault.open(locked_vault)
        v.unlock(PW)
        v.lock()
        assert v._locked

    def test_context_manager_auto_locks(self, locked_vault):
        with Vault.open(locked_vault) as v:
            v.unlock(PW)
        assert v._locked

    def test_key_material_zeroed_after_lock(self, locked_vault):
        v = Vault.open(locked_vault)
        v.unlock(PW)
        v.lock()
        assert v._mk is None
        assert v._fk is None
        assert v._rst is None

    def test_operations_on_locked_vault_raise(self, locked_vault):
        v = Vault.open(locked_vault)
        with pytest.raises(VaultLockedError):
            v.list()
        with pytest.raises(VaultLockedError):
            v.count()
        with pytest.raises(VaultLockedError):
            v.set_credential(login("x", "u", "p"))


# ── login credentials ─────────────────────────────────────────────────────────

class TestLoginCredential:
    def test_roundtrip(self, vault):
        vault.set_credential(login("github", "alice", "s3cr3t", "https://github.com", ["work"]))
        c = vault.get_credential("github")
        assert isinstance(c, LoginCredential)
        assert c.label    == "github"
        assert c.username == "alice"
        assert c.password == "s3cr3t"
        assert c.url      == "https://github.com"
        assert c.tags     == ["work"]

    def test_get_returns_password(self, vault):
        vault.set_credential(login("site", "bob", "mypass"))
        assert vault.get("site") == "mypass"

    def test_get_username(self, vault):
        vault.set_credential(login("portal", "carol", "pw123"))
        assert vault.get_username("portal") == "carol"

    def test_get_password(self, vault):
        vault.set_credential(login("portal2", "dave", "pw456"))
        assert vault.get_password("portal2") == "pw456"

    def test_overwrite(self, vault):
        vault.set_credential(login("srv", "u1", "old"))
        vault.set_credential(login("srv", "u2", "new"))
        c = vault.get_credential("srv")
        assert c.username == "u2"
        assert c.password == "new"

    def test_unicode_fields(self, vault):
        vault.set_credential(login("site-jp", "ユーザー", "パスワード🔑"))
        c = vault.get_credential("site-jp")
        assert c.username == "ユーザー"
        assert c.password == "パスワード🔑"


# ── api key credentials ────────────────────────────────────────────────────────

class TestApiKeyCredential:
    def test_roundtrip(self, vault):
        vault.set_credential(api_key("aws", "AKIAIOSFODNN7", "wJalrXUtnFEMI", "AWS"))
        c = vault.get_credential("aws")
        assert isinstance(c, ApiKeyCredential)
        assert c.key_id  == "AKIAIOSFODNN7"
        assert c.secret  == "wJalrXUtnFEMI"
        assert c.service == "AWS"

    def test_get_returns_secret(self, vault):
        vault.set_credential(api_key("stripe", "pk_test_abc", "sk_test_xyz"))
        assert vault.get("stripe") == "sk_test_xyz"


# ── secure notes ───────────────────────────────────────────────────────────────

class TestSecureNote:
    def test_roundtrip(self, vault):
        vault.set_credential(note("seed", "word1 word2 word3 word4 word5 word6 word7 word8"))
        c = vault.get_credential("seed")
        assert isinstance(c, SecureNote)
        assert c.content.startswith("word1")

    def test_large_note(self, vault):
        content = "A" * 10_000
        vault.set_credential(note("big", content))
        assert vault.get("big") == content


# ── token credentials ─────────────────────────────────────────────────────────

class TestTokenCredential:
    def test_roundtrip(self, vault):
        vault.set_credential(token("gh-token", "ghp_abc123", "2026-12-31"))
        c = vault.get_credential("gh-token")
        assert isinstance(c, TokenCredential)
        assert c.token   == "ghp_abc123"
        assert c.expires == "2026-12-31"

    def test_get_returns_token(self, vault):
        vault.set_credential(token("jwt", "eyJhbGci..."))
        assert vault.get("jwt") == "eyJhbGci..."


# ── vault operations ──────────────────────────────────────────────────────────

class TestVaultOps:
    def test_has_true(self, vault):
        vault.set_credential(login("x", "u", "p"))
        assert vault.has("x") is True

    def test_has_false(self, vault):
        assert vault.has("nonexistent") is False

    def test_delete(self, vault):
        vault.set_credential(login("tmp", "u", "p"))
        vault.delete("tmp")
        with pytest.raises(CredentialNotFound):
            vault.get_credential("tmp")

    def test_delete_missing_raises(self, vault):
        with pytest.raises(CredentialNotFound):
            vault.delete("ghost")

    def test_not_found_raises(self, vault):
        with pytest.raises(CredentialNotFound):
            vault.get_credential("nope")

    def test_list_empty(self, vault):
        assert vault.list() == []

    def test_list_multiple(self, vault):
        vault.set_credential(login("b", "u", "p"))
        vault.set_credential(note("a", "text"))
        assert vault.list() == ["a", "b"]

    def test_count(self, vault):
        assert vault.count() == 0
        vault.set_credential(login("c1", "u", "p"))
        vault.set_credential(login("c2", "u", "p"))
        assert vault.count() == 2

    def test_info_no_unlock(self, locked_vault):
        info = Vault.open(locked_vault).info()
        assert info["version"] == 4
        assert info["locked"]  is True
        assert info["size"]    > 0

    def test_backup_returns_bytes(self, vault):
        raw = vault.backup()
        assert isinstance(raw, bytes)
        assert raw[:10] == _MAGIC

    def test_get_credential_secure(self, vault):
        vault.set_credential(login("sec", "alice", "topSecret"))
        with vault.get_credential_secure("sec") as c:
            assert c.password == "topSecret"


# ── persistence ────────────────────────────────────────────────────────────────

class TestPersistence:
    def test_survives_lock_unlock(self, vpath):
        with Vault.create(vpath, PW) as v:
            v.set_credential(login("persist", "user", "pass123"))

        with Vault.open(vpath) as v:
            v.unlock(PW)
            c = v.get_credential("persist")
            assert c.password == "pass123"

    def test_multiple_credentials_persist(self, vpath):
        with Vault.create(vpath, PW) as v:
            v.set_credential(login("site1", "u1", "p1"))
            v.set_credential(api_key("svc",  "kid", "secret"))
            v.set_credential(note("memo",    "hello"))

        with Vault.open(vpath) as v:
            v.unlock(PW)
            assert v.count() == 3
            assert v.get("site1") == "p1"
            assert v.get("svc")   == "secret"
            assert v.get("memo")  == "hello"

    def test_delete_persists(self, vpath):
        with Vault.create(vpath, PW) as v:
            v.set_credential(login("gone", "u", "p"))
            v.delete("gone")

        with Vault.open(vpath) as v:
            v.unlock(PW)
            assert v.has("gone") is False


# ── ratchet forward secrecy ───────────────────────────────────────────────────

class TestRatchet:
    def test_ratchet_advances_on_each_unlock(self, vpath):
        with Vault.create(vpath, PW) as v:
            n0 = v._rst.n

        with Vault.open(vpath) as v:
            v.unlock(PW)
            n1 = v._rst.n

        with Vault.open(vpath) as v:
            v.unlock(PW)
            n2 = v._rst.n

        assert n1 > n0
        assert n2 > n1

    def test_ratchet_state_changes_each_unlock(self, vpath):
        with Vault.create(vpath, PW) as v:
            pass

        states = []
        for _ in range(3):
            with Vault.open(vpath) as v:
                v.unlock(PW)
                states.append(bytes(v._rst.rk))

        assert len(set(states)) == 3, "root key must differ after each ratchet advance"


# ── integrity checks ──────────────────────────────────────────────────────────

class TestIntegrity:
    def test_tampered_body_raises(self, vpath):
        Vault.create(vpath, PW).lock()
        raw = bytearray(vpath.read_bytes())
        raw[-20] ^= 0xFF
        vpath.write_bytes(bytes(raw))
        v = Vault.open(vpath)
        with pytest.raises(AccessError):
            v.unlock(PW)

    def test_tampered_header_raises(self, vpath):
        Vault.create(vpath, PW).lock()
        raw = bytearray(vpath.read_bytes())
        raw[_HEADER_LEN + 4] ^= 0xAB
        vpath.write_bytes(bytes(raw))
        v = Vault.open(vpath)
        with pytest.raises(AccessError):
            v.unlock(PW)

    def test_wrong_magic_raises(self, vpath):
        Vault.create(vpath, PW).lock()
        raw = bytearray(vpath.read_bytes())
        raw[0:4] = b"JUNK"
        vpath.write_bytes(bytes(raw))
        v = Vault.open(vpath)
        with pytest.raises((IntegrityError, AccessError)):
            v.unlock(PW)

    def test_truncated_file_raises(self, vpath):
        Vault.create(vpath, PW).lock()
        raw = vpath.read_bytes()
        vpath.write_bytes(raw[:20])
        v = Vault.open(vpath)
        with pytest.raises((IntegrityError, AccessError, Exception)):
            v.unlock(PW)


# ── rotate password ────────────────────────────────────────────────────────────

class TestRotate:
    def test_rotate_and_unlock_new(self, vpath):
        with Vault.create(vpath, PW) as v:
            v.set_credential(login("entry", "u", "secret"))
            v.rotate_password(PW, PW2)

        with Vault.open(vpath) as v:
            v.unlock(PW2)
            assert v.get("entry") == "secret"

    def test_old_password_fails_after_rotate(self, vpath):
        with Vault.create(vpath, PW) as v:
            v.rotate_password(PW, PW2)

        v = Vault.open(vpath)
        with pytest.raises(AccessError):
            v.unlock(PW)

    def test_wrong_old_password_in_rotate_raises(self, vpath):
        with Vault.create(vpath, PW) as v:
            with pytest.raises(AccessError):
                v.rotate_password("bad-old-pw", PW2)

    def test_all_entries_survive_rotate(self, vpath):
        with Vault.create(vpath, PW) as v:
            v.set_credential(login("a", "u1", "p1"))
            v.set_credential(note("b", "content"))
            v.set_credential(api_key("c", "kid", "sec"))
            v.rotate_password(PW, PW2)

        with Vault.open(vpath) as v:
            v.unlock(PW2)
            assert v.count() == 3
            assert v.get("a") == "p1"
            assert v.get("b") == "content"
            assert v.get("c") == "sec"


# ── destroy ────────────────────────────────────────────────────────────────────

class TestDestroy:
    def test_destroy_removes_file(self, vpath):
        with Vault.create(vpath, PW) as v:
            v.destroy(PW)
        assert not vpath.exists()

    def test_wrong_password_destroy_raises(self, vault, vpath):
        with pytest.raises(AccessError):
            vault.destroy("wrong-pw")
        assert vpath.exists()

    def test_vault_locked_after_destroy(self, vpath):
        v = Vault.create(vpath, PW)
        v.destroy(PW)
        assert v._locked


# ── audit log ──────────────────────────────────────────────────────────────────

class TestAudit:
    def test_audit_contains_created(self, vault):
        log = vault.audit_log()
        events = [e["ev"] for e in log]
        assert "created" in events

    def test_audit_contains_set(self, vault):
        vault.set_credential(login("x", "u", "p"))
        log = vault.audit_log()
        assert any(e["ev"].startswith("set:") for e in log)

    def test_audit_contains_unlocked(self, vpath):
        with Vault.create(vpath, PW):
            pass
        with Vault.open(vpath) as v:
            v.unlock(PW)
            log = v.audit_log()
        assert any(e["ev"] == "unlocked" for e in log)

    def test_audit_timestamps_monotonic(self, vault):
        vault.set_credential(login("a", "u", "p"))
        vault.set_credential(login("b", "u", "p"))
        log = vault.audit_log()
        times = [e["ts"] for e in log]
        assert times == sorted(times)


# ── per-credential key isolation ───────────────────────────────────────────────

class TestKeyIsolation:
    def test_different_labels_different_keys(self, vault):
        lh1 = vault._lhash("label-one")
        lh2 = vault._lhash("label-two")
        ek1 = vault._ekey(lh1)
        ek2 = vault._ekey(lh2)
        assert ek1 != ek2

    def test_entry_key_uses_label_hash(self, vault):
        lh = vault._lhash("myservice")
        ek = vault._ekey(lh)
        assert len(ek) == 32


# ── convenience constructors ───────────────────────────────────────────────────

class TestConstructors:
    def test_login(self):
        c = login("x", "u", "p", "https://x.com", ["t1"])
        assert c.label == "x" and c.tags == ["t1"]

    def test_api_key(self):
        c = api_key("s", "kid", "secret", "AWS")
        assert c.service == "AWS"

    def test_note(self):
        c = note("n", "text")
        assert c.content == "text"

    def test_token(self):
        c = token("t", "tok123", "2099-01-01")
        assert c.expires == "2099-01-01"
