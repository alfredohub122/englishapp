#!/usr/bin/env sh
# Lightweight Gradle bootstrap for this project; Gradle itself runs the Kotlin DSL.
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION="8.11.1"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL_ROOT="$GRADLE_USER_HOME/english-course-gradle-$GRADLE_VERSION"
GRADLE_BIN="$INSTALL_ROOT/gradle-$GRADLE_VERSION/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$INSTALL_ROOT"
  ARCHIVE="$INSTALL_ROOT/gradle-$GRADLE_VERSION-bin.zip"
  curl --fail --location --retry 3 --retry-all-errors \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
    --output "$ARCHIVE"
  unzip -q -o "$ARCHIVE" -d "$INSTALL_ROOT"
  rm -f "$ARCHIVE"
fi

exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
