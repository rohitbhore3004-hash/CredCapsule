# CredCapsule

Secure credential vault — AES-256-GCM encryption with X3DH session bootstrap
and Double Ratchet forward secrecy. Single Python file, no database.

## Requirements

```
pip install cryptography
```

Python 3.9+. Works on Linux, macOS, Windows.

## Quick start

```python
from credcapsule_single import Vault, login, api_key, note, token

# Create
with Vault.create("my.ccv", "strong-passphrase") as v:
    v.set_credential(login("github", "alice", "s3cr3t", "https://github.com"))
    v.set_credential(api_key("aws", "AKIAIOSFODNN7", "wJalrXUtnFEMI", "AWS"))
    v.set_credential(note("seed phrase", "word1 word2 word3 ..."))

# Open later
with Vault.open("my.ccv") as v:
    v.unlock("strong-passphrase")
    print(v.get("github"))          # s3cr3t
    print(v.get_username("github")) # alice
    print(v.list())                 # ['aws', 'github', 'seed phrase']
```

## CLI

```bash
python credcapsule_single.py --vault my.ccv create
python credcapsule_single.py --vault my.ccv set --label github --username alice --value s3cr3t --kind login
python credcapsule_single.py --vault my.ccv get --label github
python credcapsule_single.py --vault my.ccv list
python credcapsule_single.py --vault my.ccv rotate --new-password "new-pass"
python credcapsule_single.py --vault my.ccv protocol
```

All commands return JSON. Errors go to stderr with exit code 1.

## PowerShell

```powershell
Import-Module .\CredCapsule.psm1
Open-CredVault -Path my.ccv -Create
Set-CredVaultLogin -Label github -Username alice -Password s3cr3t
Get-CredVaultLogin -Label github
Get-CredVaultEntries
Close-CredVault
```

## Crypto

| Layer | Algorithm |
|---|---|
| Key exchange | X25519 / Curve25519 |
| Session bootstrap | X3DH with 10 one-time prekeys |
| Forward secrecy | Double Ratchet (DH step on every unlock) |
| Symmetric cipher | AES-256-GCM |
| Key derivation | HKDF-SHA256 (domain-separated) |
| File integrity | HMAC-SHA256 |
| Label storage | BLAKE2b keyed hash — labels never stored in plaintext |
| Password KDF | scrypt N=2¹⁷, r=8, p=1 (~1s, ~128MB RAM) |

## File format

Custom `.ccv` binary. No SQLite, no JSON on disk.

```
[86-byte header]  Magic(10) + Salt(32) + KDF-params(12) + HMAC(32)
[body sections]   VERI · RATC · X3DH · KDFP · ENTR · AUDT
```

Each section is big-endian TLV. Each credential is encrypted with its own
isolated key; label hashes serve as AES-GCM AAD to prevent swap attacks.
Atomic writes via `.tmp` + `os.replace()`.

## Tests

```bash
pip install pytest
pytest tests/
```

61 tests covering all credential types, persistence, ratchet advancement,
tamper detection, password rotation, and destroy.

## Rebuild

`CREDCAPSULE_REBUILD_PROMPT.txt` contains the complete specification to
regenerate this entire project from scratch in any Claude session.
