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

- **Key (mode 1):** `PBKDF2-HMAC-SHA256(UTF-8(passphrase), salt, iterations, 32 bytes)`.
- **Key (mode 2):** a random 256-bit key held by the device (Android: wrapped by the hardware keystore; travels only inside an end-to-end encrypted system backup).
- **Associated data:** the 40 header bytes. Any change to magic, mode, salt, iterations or nonce makes decryption fail.
- **Compression:** raw DEFLATE (RFC 1951, no zlib/gzip header) of the UTF-8 JSON payload. Readers should cap the inflated size (Cyclus: 32 MB).

## Payload (JSON, schema 1)
See `cyclus-export.schema.json`. Summary:

```json
{
  "schema": 1,
  "exportedAt": "2026-11-22T12:00:00Z",
  "app": "Cyclus Android 0.1.0",
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
- Unknown fields must be ignored by readers.

## Test vectors
`shared/golden/export/`: `vector.json` (payload), `vector-mode1.cyclus` (passphrase `Cyclus-Testvektor 1`, salt
`000102…0f`, 1000 iterations — below the normal minimum, test only), `vector-mode2.cyclus` (key `101112…2f`), both with
nonce `a0a1…ab`. Encoders must reproduce them byte for byte.
