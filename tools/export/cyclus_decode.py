#!/usr/bin/env python3
"""Reference decoder for `.cyclus` v1 files (docs/export/FORMAT.md).

Independent of the app code, so the export format stays readable even without Cyclus (end-of-life pledge, spec §11.9).

    python3 cyclus_decode.py <file.cyclus>                 # asks for the passphrase
    python3 cyclus_decode.py <file.cyclus> --key <hex>     # mode 2 (raw key)

Prints the JSON payload to stdout. Needs the `cryptography` package.
"""
import argparse
import getpass
import hashlib
import struct
import sys
import zlib

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"CYCLUS1"
HEADER = 7 + 1 + 16 + 4 + 12
MAX_JSON = 32 * 1024 * 1024


def decode(data: bytes, passphrase: str | None = None, key: bytes | None = None, min_iterations: int = 100_000) -> bytes:
    if len(data) < HEADER + 16 or data[:7] != MAGIC:
        raise ValueError("not a .cyclus v1 file")
    mode = data[7]
    salt = data[8:24]
    (iterations,) = struct.unpack(">I", data[24:28])
    nonce = data[28:40]
    header = data[:HEADER]
    if mode == 1:
        if passphrase is None:
            raise ValueError("passphrase required (mode 1)")
        if not (min_iterations <= iterations <= 10_000_000):
            raise ValueError(f"iterations out of range: {iterations}")
        key = hashlib.pbkdf2_hmac("sha256", passphrase.encode("utf-8"), salt, iterations, 32)
    elif mode == 2:
        if key is None or len(key) != 32 or iterations != 0:
            raise ValueError("raw 256-bit key required (mode 2)")
    else:
        raise ValueError(f"unknown mode {mode}")
    deflated = AESGCM(key).decrypt(nonce, data[HEADER:], header)  # header = AAD; raises on tampering
    d = zlib.decompressobj(-15)  # raw DEFLATE
    out = d.decompress(deflated, MAX_JSON)
    if d.unconsumed_tail:
        raise ValueError("payload too large")
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("file")
    ap.add_argument("--key", help="hex raw key (mode 2)")
    ap.add_argument("--passphrase", help="passphrase (mode 1); prompted if omitted")
    ap.add_argument("--min-iterations", type=int, default=100_000, help=argparse.SUPPRESS)
    a = ap.parse_args()
    data = open(a.file, "rb").read()
    if data[7:8] == b"\x02":
        out = decode(data, key=bytes.fromhex(a.key or ""), min_iterations=a.min_iterations)
    else:
        out = decode(data, passphrase=a.passphrase or getpass.getpass("Passphrase: "), min_iterations=a.min_iterations)
    sys.stdout.write(out.decode("utf-8"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
