#!/data/data/com.termux/files/usr/bin/bash
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
data_dir=${RAVA_DATA_DIR:-"$project_dir/.rava"}
source_dir="$data_dir/chatgpt-web2api"
venv_dir="$data_dir/chatgpt-web2api-venv"
upstream_url="https://github.com/Octo-Lex/ChatGPT-Web2API.git"
upstream_commit="497527dceabfa3f95961e23c291e618c5570f1ac"
patch_file="$project_dir/patches/chatgpt-web2api-strict-model.patch"
projects_patch_file="$project_dir/patches/chatgpt-web2api-projects.patch"
delete_patch_file="$project_dir/patches/chatgpt-web2api-delete.patch"
fast_navigation_patch_file="$project_dir/patches/chatgpt-web2api-fast-navigation.patch"

mkdir -p "$data_dir"

if [ ! -d "$source_dir/.git" ]; then
  git clone "$upstream_url" "$source_dir"
  git -C "$source_dir" checkout --detach "$upstream_commit"
else
  current_commit=$(git -C "$source_dir" rev-parse HEAD)
  if [ "$current_commit" != "$upstream_commit" ]; then
    echo "Existing sidecar is at unexpected commit: $current_commit" >&2
    exit 1
  fi
fi

# This checkout is a managed dependency. Reset it before patching so repeated
# installs remain deterministic even when adjacent patches change the same file.
git -C "$source_dir" reset --hard "$upstream_commit"
git -C "$source_dir" clean -fd
git -C "$source_dir" apply "$patch_file"
git -C "$source_dir" apply "$projects_patch_file"
git -C "$source_dir" apply "$delete_patch_file"
git -C "$source_dir" apply "$fast_navigation_patch_file"

# maturin cannot infer this from a Termux Python executable.  The Python
# platform tag carries the correct Android ABI baseline (currently android-24).
if [ -z "${ANDROID_API_LEVEL:-}" ]; then
  ANDROID_API_LEVEL=$(python -c 'import sysconfig; print(sysconfig.get_platform().split("-")[1])')
  export ANDROID_API_LEVEL
fi
echo "Termux Android ABI level: $ANDROID_API_LEVEL"

if [ ! -x "$venv_dir/bin/python" ]; then
  python -m venv "$venv_dir"
fi
"$venv_dir/bin/pip" install -e "$source_dir"

echo "Patched ChatGPT-Web2API installed."
echo "Run: $venv_dir/bin/chatgpt-web2api"
