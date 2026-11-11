#!/usr/bin/env bash
# Run a command inside the build container (JDK 17 + Android SDK 34 + Gradle 8.9).
# Usage: docker/run.sh ./gradlew :engine:test      (first time: docker build -t sharpcalc-build docker)
set -e
cd "$(dirname "$0")/.."
docker volume create sharpcalc-gradle >/dev/null
docker run --rm -v sharpcalc-gradle:/cache sharpcalc-build sh -c "mkdir -p /cache/gradle /cache/home && chown $(id -u):$(id -g) /cache /cache/gradle /cache/home"
exec docker run --rm --user "$(id -u):$(id -g)" -e HOME=/cache/home \
  -v sharpcalc-gradle:/cache -v "$PWD":/work -w /work sharpcalc-build "$@"
