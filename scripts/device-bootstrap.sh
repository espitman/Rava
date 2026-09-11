#!/data/data/com.termux/files/usr/bin/bash
set -u

project_dir=${RAVA_PROJECT_DIR:-"$HOME/Rava"}
log_file="$project_dir/.device-bootstrap.log"
status_file="$project_dir/.device-bootstrap.status"

exec >"$log_file" 2>&1
rm -f "$status_file"

finish() {
  result=$?
  printf '%s\n' "$result" >"$status_file"
  echo "Rava device bootstrap finished with status $result"
}
trap finish EXIT

set -e
cd "$project_dir"

echo "Python: $(python --version 2>&1)"
echo "Machine: $(uname -m)"

# maturin cannot infer the ABI baseline from a Termux Python executable. Use
# Python's platform tag (android-24 on current Termux), not the device SDK level.
if [ -z "${ANDROID_API_LEVEL:-}" ]; then
  ANDROID_API_LEVEL=$(python -c 'import sysconfig; print(sysconfig.get_platform().split("-")[1])')
  export ANDROID_API_LEVEL
fi
echo "Termux Android ABI level: ${ANDROID_API_LEVEL:-unknown}"

if ! command -v rustc >/dev/null 2>&1; then
  echo "Installing the Termux Rust toolchain required to build orjson..."
  pkg install -y rust
fi

if [ ! -x .venv/bin/python ]; then
  python -m venv .venv
fi

# A failed aggregate pip transaction can still leave successfully built Android
# wheels in the cache. Install those first so retries do not rebuild Rust packages.
for wheel_pattern in \
  'orjson-3.11.9-*.whl' \
  'pydantic_core-2.41.5-*.whl' \
  'cffi-2.1.1-*.whl'; do
  cached_wheel=$(find "$HOME/.cache/pip/wheels" -type f -name "$wheel_pattern" \
    -name "*android_${ANDROID_API_LEVEL}_*.whl" -print -quit 2>/dev/null)
  if [ -n "$cached_wheel" ]; then
    echo "Installing cached Android wheel: $(basename "$cached_wheel")"
    .venv/bin/pip install --no-deps "$cached_wheel"
  fi
done

.venv/bin/pip install -e .
.venv/bin/pip install 'pytest>=8' 'pytest-asyncio>=0.23'
.venv/bin/pytest -q
