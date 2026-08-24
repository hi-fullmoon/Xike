#!/usr/bin/env bash

set -euo pipefail

if ! command -v java >/dev/null 2>&1; then
  echo "JDK 17 not found: install JDK 17 or set JAVA_HOME to Android Studio's bundled JDK." >&2
  exit 1
fi

if ! java_settings="$(java -XshowSettings:properties -version 2>&1)"; then
  echo "Java launcher found, but no usable JDK is configured." >&2
  echo "Install JDK 17 or set JAVA_HOME to Android Studio's bundled JDK." >&2
  exit 1
fi

java_version="$(awk -F'= ' '/java.specification.version/ { print $2; exit }' <<<"$java_settings")"

if [[ "$java_version" != "17" ]]; then
  echo "JDK 17 required, but the active Java specification version is ${java_version:-unknown}." >&2
  echo "Set JAVA_HOME to a JDK 17 installation and try again." >&2
  exit 1
fi

echo "JDK 17 is active."
java -version
