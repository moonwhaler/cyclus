# `.cyclus` file format, version 1

Public specification of Cyclus backup and export files (DESIGN_SPEC §11.9, end-of-life pledge). Anyone may write a
decoder; a reference decoder is in `tools/export/cyclus_decode.py`. Licensed CC-BY 4.0.

## Container
All integers big-endian. The first 40 bytes are the **header**.

| Offset | Size | Field | Notes |
|---|---|---|---|
| 0 | 7 | magic | ASCII `CYCLUS1` |
| 7 | 1 | mode | `1` = passphrase, `2` = raw key (device backups only) |
| 8 | 16 | salt | random in mode 1, all zero in mode 2 |
| 24 | 4 | iterations | PBKDF2 iterations in mode 1 (Cyclus writes 600 000; readers accept 100 000 – 10 000 000), `0` in mode 2 |
| 28 | 12 | nonce | random, never reused with the same key |
| 40 | n | ciphertext | AES-256-GCM of the compressed payload |
| 40+n | 16 | tag | GCM authentication tag |

- **Key (mode 1):** `PBKDF2-HMAC-SHA256(UTF-8(NFC(passphrase)), salt, iterations, 32 bytes)`. The passphrase is Unicode-normalized to **NFC** first, so the same visible passphrase gives the same key on every platform and keyboard („ä“ precomposed or as „a“ + combining mark). Readers must also try the passphrase exactly as typed when the NFC form fails: files written before 2026-11-23 used it un-normalized.
- **Key (mode 2):** a random 256-bit key held by the device (Android: wrapped by the hardware keystore; travels only inside an end-to-end encrypted system backup).
- **Associated data:** the 40 header bytes. Any change to magic, mode, salt, iterations or nonce makes decryption fail.
- **Compression:** raw DEFLATE (RFC 1951, no zlib/gzip header) of the UTF-8 JSON payload. Readers should cap the inflated size (Cyclus: 32 MB).

## Payload (JSON, schema 1)
See `cyclus-export.schema.json`. Summary:

```json
{
  "schema": 1,
  "exportedAt": "2026-11-22T12:00:00Z",
  "app": "Cyclus Android 1.0.0",
  "logs": [{ "day": "2026-11-10", "flow": "heavy", "symptoms": ["Cramps"], "pain": {"lowC": 7},
             "mood": 2, "mucus": "eggwhite", "lh": "pos", "bbt": 36.38, "sex": true,
             "note": "…", "nothingElse": false, "updatedAt": 1795000000000 }],
  "overrides": [{ "day": "2026-11-10", "kind": "start" }],
  "episodes": [{ "id": "…", "kind": "hbc", "start": "2025-01-01", "end": "2025-06-30" }],
  "tags": [{ "id": "…", "name": "Yoga" }],
  "prefs": { "mode": "conceive" }
}
```

- Dates are civil dates `YYYY-MM-DD` (no time zone). `updatedAt` is epoch milliseconds (merge: newer wins).
- Item keys (symptoms, emotions, tags, pain regions, flow, mucus, LH) are stable English keys, not display text.
- `prefs` holds user preferences only. App-lock settings are **never** in a user export; a device backup (mode 2) may carry them in `security`.
- Known `prefs` keys (all strings; unknown keys are ignored, unknown values fall back to the default):
  - `mode` (`conceive`), `showFertTrack` (`1`), `mutedPatterns` (comma list of pattern ids);
  - `today.layout`: Today's card order, comma list of card keys, `!` before a hidden card (e.g. `log,upcoming,!forecast,…,!week`); cards a newer app adds are placed at their default position;
  - `today.ring`: `compact`, or absent for the large ring;
  - `quick.picks`: comma list of up to 8 symptom, feeling or tag keys; `""` means none, absent means the default (Cramps, Headache, Tired, Bloating).
- Unknown fields must be ignored by readers.

## Test vectors
`shared/golden/export/`: `vector.json` (payload), `vector-mode1.cyclus` (passphrase `Cyclus-Testvektor 1`, salt
`000102…0f`, 1000 iterations — below the normal minimum, test only), `vector-mode2.cyclus` (key `101112…2f`), both with
nonce `a0a1…ab`. Encoders must reproduce them byte for byte. `vector-mode1-umlaut.cyclus`: same salt/nonce/iterations,
passphrase `Zyklus-Prüfung ä`; encoders must produce these exact bytes from both the NFC and the NFD form of it.

## Cross-platform guarantee (owner requirement 2026-11-23)
A `.cyclus` file written by Cyclus on **any** platform opens in Cyclus on **any** other platform (Android → iPhone, iPhone → Android, and the reference decoder), with merge or replace. Both apps use the one shared Kotlin implementation of this format; the vectors above run on JVM, Android and iOS. File name `Cyclus-YYYY-MM-DD.cyclus`; media type `application/octet-stream`; iOS declares the exported type `com.nordinary.cyclus.backup` (extension `cyclus`). Device backups (mode 2) use the same container, but their key never leaves the platform's backup system, so they restore only on the same platform; moving between Android and iPhone always goes through a passphrase file.
