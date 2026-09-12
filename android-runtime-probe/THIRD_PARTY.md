# Third-party inputs

The generated artifacts are not committed. Their exact versions and integrity
values are recorded in `versions.env`.

| Input | Source | License | Local changes |
| --- | --- | --- | --- |
| Node.js 24.18.0-1 and runtime libraries | `https://packages.termux.dev/apt/termux-main/` | Node MIT; dependencies NCSA, Apache-2.0, MIT, Unicode, Public Domain, and zlib licenses | ELF dynamic names/RUNPATH and Android prefix fallbacks patched for app-local loading |
| ICU 78.3 license | `https://raw.githubusercontent.com/unicode-org/icu/release-78.3/LICENSE` | Unicode License v3 and bundled component notices | None |
| Node.js 24.21.0 source experiment | `https://nodejs.org/dist/v24.21.0/` | MIT; bundled dependencies carry their own notices | None |
| Gemini CLI 0.59.0 npm bundle | `https://registry.npmjs.org/@google/gemini-cli/-/gemini-cli-0.59.0.tgz` | Apache-2.0; bundle includes third-party notices | None |
| Codex app-server 0.154.0 Linux/musl ARM64 release | `https://github.com/openai/codex/releases/tag/rust-v0.154.0` | Apache-2.0 | None; upstream executable is Sigstore-verified before APK staging |
| Android NDK 27.2.12479018 | Local Android SDK installation | Android SDK terms and component licenses | None |

The exact Termux package versions, sizes, paths, and SHA-256 hashes are in
`termux-runtime-packages.tsv`. The preparation script copies the Node and runtime
library license texts into APK assets. Termux libicu 78.3 currently contains a
14-byte `404: Not Found` license file because its packaging recipe requests a
nonexistent hyphenated Git ref; the script instead downloads the exact upstream
`release-78.3` license and verifies its pinned SHA-256.

The Gemini preparation script copies the upstream `LICENSE`, package metadata,
and bundled third-party notices into APK assets.
