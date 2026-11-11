#!/usr/bin/env bash
# Boots a headless 1080x2400 420dpi phone (Pixel 7 profile, the same screen class as a Pixel 8; API 34) emulator in a container, installs the debug APK and runs a script against it.
# Usage: docker/emulator.sh [script-inside-repo]   (default: docker/shots.sh; needs /dev/kvm)
set -e
cd "$(dirname "$0")/.."
docker image inspect sharpcalc-emulator >/dev/null 2>&1 || docker build -t sharpcalc-emulator -f docker/Dockerfile.emulator docker
docker volume create sharpcalc-gradle >/dev/null
exec docker run --rm --device /dev/kvm --user "$(id -u):$(id -g)" -e HOME=/cache/home \
  -v sharpcalc-gradle:/cache -v "$PWD":/work -w /work sharpcalc-emulator bash docker/in-emulator.sh "${1:-docker/shots.sh}"
