#!/usr/bin/env python3
"""Replace selected ELF64 dynamic strings without changing section sizes."""

import argparse
import struct
from pathlib import Path

PT_LOAD = 1
PT_DYNAMIC = 2
DT_NULL = 0
DT_NEEDED = 1
DT_STRTAB = 5
DT_STRSZ = 10
DT_SONAME = 14
DT_RPATH = 15
DT_RUNPATH = 29
STRING_TAGS = {DT_NEEDED, DT_SONAME, DT_RPATH, DT_RUNPATH}


def c_string(data: bytearray, offset: int, limit: int) -> bytes:
    end = data.find(b"\0", offset, limit)
    if end < 0:
        raise ValueError(f"unterminated dynamic string at file offset {offset}")
    return bytes(data[offset:end])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("elf", type=Path)
    parser.add_argument("--replace", action="append", default=[], metavar="OLD=NEW")
    parser.add_argument("--require", action="append", default=[], metavar="OLD")
    args = parser.parse_args()

    replacements: dict[bytes, bytes] = {}
    for item in args.replace:
        if "=" not in item:
            parser.error(f"replacement lacks '=': {item}")
        old, new = item.split("=", 1)
        old_bytes, new_bytes = old.encode(), new.encode()
        if len(new_bytes) > len(old_bytes):
            parser.error(f"replacement is longer than original: {item}")
        replacements[old_bytes] = new_bytes

    data = bytearray(args.elf.read_bytes())
    if data[:6] != b"\x7fELF\x02\x01":
        raise SystemExit(f"{args.elf}: expected little-endian ELF64")

    phoff = struct.unpack_from("<Q", data, 32)[0]
    phentsize = struct.unpack_from("<H", data, 54)[0]
    phnum = struct.unpack_from("<H", data, 56)[0]
    loads: list[tuple[int, int, int]] = []
    dynamic = None
    for index in range(phnum):
        offset = phoff + index * phentsize
        p_type, _flags, p_offset, p_vaddr, _paddr, p_filesz, _memsz, _align = \
            struct.unpack_from("<IIQQQQQQ", data, offset)
        if p_type == PT_LOAD:
            loads.append((p_vaddr, p_offset, p_filesz))
        elif p_type == PT_DYNAMIC:
            dynamic = (p_offset, p_filesz)
    if dynamic is None:
        raise SystemExit(f"{args.elf}: no PT_DYNAMIC segment")

    entries: list[tuple[int, int]] = []
    strtab_vaddr = None
    strtab_size = None
    dynamic_offset, dynamic_size = dynamic
    for offset in range(dynamic_offset, dynamic_offset + dynamic_size, 16):
        tag, value = struct.unpack_from("<QQ", data, offset)
        if tag == DT_NULL:
            break
        entries.append((tag, value))
        if tag == DT_STRTAB:
            strtab_vaddr = value
        elif tag == DT_STRSZ:
            strtab_size = value
    if strtab_vaddr is None or strtab_size is None:
        raise SystemExit(f"{args.elf}: dynamic string table is missing")

    strtab_offset = None
    for vaddr, file_offset, file_size in loads:
        if vaddr <= strtab_vaddr < vaddr + file_size:
            strtab_offset = file_offset + strtab_vaddr - vaddr
            break
    if strtab_offset is None:
        raise SystemExit(f"{args.elf}: cannot map DT_STRTAB to the file")

    counts = {old: 0 for old in replacements}
    patched_offsets: set[int] = set()
    for tag, value in entries:
        if tag not in STRING_TAGS:
            continue
        offset = strtab_offset + value
        old = c_string(data, offset, strtab_offset + strtab_size)
        if old not in replacements:
            continue
        counts[old] += 1
        if offset in patched_offsets:
            continue
        new = replacements[old]
        data[offset:offset + len(old)] = new + b"\0" * (len(old) - len(new))
        patched_offsets.add(offset)

    for required in args.require:
        required_bytes = required.encode()
        if counts.get(required_bytes, 0) == 0:
            raise SystemExit(f"{args.elf}: required dynamic string was absent: {required}")

    args.elf.write_bytes(data)
    changed = ", ".join(
        f"{old.decode()}={count}" for old, count in counts.items() if count
    ) or "none"
    print(f"{args.elf.name}: patched dynamic strings: {changed}")


if __name__ == "__main__":
    main()
