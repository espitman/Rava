#!/usr/bin/env python3
"""Replace an exact byte string in-place, preserving the binary's size."""

import argparse
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("file", type=Path)
    parser.add_argument("old")
    parser.add_argument("new")
    parser.add_argument("--count", type=int, required=True)
    parser.add_argument("--nul-pad", action="store_true")
    args = parser.parse_args()

    old, new = args.old.encode(), args.new.encode()
    if args.nul_pad:
        if len(new) > len(old):
            parser.error("NUL-padded replacement is longer than the original")
        new += b"\0" * (len(old) - len(new))
    elif len(new) != len(old):
        parser.error("non-padded replacement must have exactly the same length")

    data = args.file.read_bytes()
    found = data.count(old)
    if found != args.count:
        raise SystemExit(f"{args.file}: expected {args.count} occurrence(s), found {found}")
    replaced = data.replace(old, new)
    if len(replaced) != len(data):
        raise SystemExit(f"{args.file}: replacement changed file size")
    args.file.write_bytes(replaced)
    print(f"{args.file.name}: patched {found} occurrence(s) of {args.old}")


if __name__ == "__main__":
    main()
