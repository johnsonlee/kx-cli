#!/bin/sh

JAVA_BIN=${JAVA_HOME:-""}/bin/java
if [ ! -x "$JAVA_BIN" ]; then
  JAVA_BIN=$(which java)
fi

if [ ! -x "$JAVA_BIN" ]; then
  echo "Java not found!" >&2
  exit 1
fi

exec "$JAVA_BIN" -Dfile.encoding=UTF-8 -jar "$0" "$@"
