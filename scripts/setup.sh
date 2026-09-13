#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if command -v java >/dev/null 2>&1; then
  java scripts/GenerateSecrets.java .
else
  docker run --rm -v "$PWD:/setup" -w /setup eclipse-temurin:21-jdk java scripts/GenerateSecrets.java /setup
fi
chmod 600 .env secrets/jwt-private.pem
