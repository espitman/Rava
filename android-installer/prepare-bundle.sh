#!/bin/sh
set -eu

installer_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$installer_dir/.." && pwd)
asset_dir="$installer_dir/app/src/main/assets"

mkdir -p "$asset_dir"
tar -czf "$asset_dir/rava.tar.gz" \
  --exclude='.git' \
  --exclude='.venv' \
  --exclude='.rava' \
  --exclude='.pytest_cache' \
  --exclude='.ruff_cache' \
  --exclude='__pycache__' \
  --exclude='android-installer' \
  --exclude='.device-*' \
  --exclude='.*.log' \
  --exclude='.*.out' \
  --exclude='.*.status' \
  -C "$project_dir" \
  PLAN.md README.md pyproject.toml patches scripts src tests

echo "Prepared $asset_dir/rava.tar.gz"
