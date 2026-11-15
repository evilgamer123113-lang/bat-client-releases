#!/usr/bin/env python3
"""Pack the 30 built Bat Client companion mod JARs into an opaque bat-mods.dat bundle."""

import argparse
import pathlib
import struct
import zipfile

SUPPORTED_VERSIONS = [
    "1.8", "1.8.9", "1.16.5", "1.17.1", "1.18.2", "1.19.2", "1.19.4",
    "1.20.1", "1.20.4", "1.20.6", "1.21.1", "1.21.4", "1.21.5", "1.21.8", "1.21.11",
]
SUPPORTED_ENGINES = ["fabric", "forge"]
MAGIC = b"BATMODS1"
MASK = bytes([0x42, 0x41, 0x54, 0x21])


def mask_bytes(data: bytes) -> bytes:
    out = bytearray(data)
    mlen = len(MASK)
    for i in range(len(out)):
        out[i] ^= MASK[i % mlen]
    return bytes(out)


def find_jar(mod_root: pathlib.Path, engine: str, version: str) -> pathlib.Path:
    candidates = []
    if engine == "fabric" and version == "1.21.1":
        candidates.append(mod_root / "build" / "libs" / "bat-client-mod-0.1.0.jar")
    candidates.append(
        mod_root
        / "ports"
        / f"{engine}-{version}"
        / "build"
        / "libs"
        / f"bat-client-{engine}-{version}-INTERNAL-UNVERIFIED-0.1.0-{engine}-internal.jar"
    )
    for c in candidates:
        if c.is_file():
            with zipfile.ZipFile(c, "r") as zf:
                names = set(zf.namelist())
                if engine == "fabric" and "fabric.mod.json" in names:
                    return c
                if engine == "forge" and ("META-INF/mods.toml" in names or "mcmod.info" in names):
                    return c
    raise FileNotFoundError(f"Missing valid {engine} {version} JAR in {mod_root}")


def main() -> None:
    repo = pathlib.Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser()
    parser.add_argument("--mod-root", type=pathlib.Path, default=repo / "bat-client-mod")
    parser.add_argument(
        "--out",
        type=pathlib.Path,
        default=pathlib.Path("D:/BatClientBuildCache/bat-mods.dat"),
    )
    args = parser.parse_args()

    entries = []
    for version in SUPPORTED_VERSIONS:
        for engine in SUPPORTED_ENGINES:
            jar = find_jar(args.mod_root, engine, version)
            entries.append((f"{engine}-{version}", jar))

    args.out.parent.mkdir(parents=True, exist_ok=True)
    header_size = 8 + 4 + sum(2 + len(k.encode("utf-8")) + 8 + 8 for k, _ in entries)
    cursor = header_size
    index_records = []
    for key, jar in entries:
        size = jar.stat().st_size
        index_records.append((key, cursor, size, jar))
        cursor += size

    tmp = args.out.with_suffix(".tmp")
    with open(tmp, "wb") as out:
        out.write(MAGIC)
        out.write(struct.pack("<I", len(index_records)))
        for key, offset, size, _ in index_records:
            kbytes = mask_bytes(key.encode("utf-8"))
            out.write(struct.pack("<H", len(kbytes)))
            out.write(kbytes)
            out.write(struct.pack("<QQ", offset, size))
        for _, _, _, jar in index_records:
            with open(jar, "rb") as src:
                pos = 0
                while True:
                    chunk = src.read(256 * 1024)
                    if not chunk:
                        break
                    arr = bytearray(chunk)
                    for i in range(len(arr)):
                        arr[i] ^= MASK[(pos + i) % 4]
                    out.write(arr)
                    pos += len(chunk)

    tmp.replace(args.out)
    print(f"Packed {len(index_records)} mods into {args.out} ({args.out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
