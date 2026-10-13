#!/usr/bin/env bash
# One-time setup for publishing to Maven Central.
# Creates a GPG signing key, uploads its public half, and writes the Sonatype token and the
# signing key to ~/.gradle/gradle.properties (outside this repo, readable only by you).
set -euo pipefail

PROPS="$HOME/.gradle/gradle.properties"
KEY_NAME="Fewchurs SDK Signing"

if [ -f "$PROPS" ] && grep -q '^mavenCentralUsername=' "$PROPS"; then
  echo "$PROPS already has Maven Central credentials. Remove them first to run this again."
  exit 1
fi

read -r -p "Sonatype token username: " CENTRAL_USER
read -r -s -p "Sonatype token password: " CENTRAL_PASS
echo

# A random passphrase protects the key; it's stored in gradle.properties so Gradle can sign.
PASS=$(openssl rand -base64 24 | tr -d '/+=')

echo "Creating signing key..."
gpg --batch --pinentry-mode loopback --passphrase "$PASS" \
  --quick-gen-key "$KEY_NAME" rsa4096 sign never
FPR=$(gpg --list-secret-keys --with-colons "$KEY_NAME" | awk -F: '/^fpr/ {print $10; exit}')
KEY_ID=${FPR: -8}

echo "Uploading the public key (Maven Central checks it there)..."
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys "$FPR"

SECRET=$(gpg --batch --pinentry-mode loopback --passphrase "$PASS" \
  --export-secret-keys --armor "$FPR" | grep -v '\-\-' | grep -v '^=.' | tr -d '\n')

mkdir -p "$HOME/.gradle"
umask 077
{
  echo ""
  echo "# Maven Central publishing for fewchurs-kotlin. Keep private; never commit."
  echo "mavenCentralUsername=$CENTRAL_USER"
  echo "mavenCentralPassword=$CENTRAL_PASS"
  echo "signingInMemoryKey=$SECRET"
  echo "signingInMemoryKeyId=$KEY_ID"
  echo "signingInMemoryKeyPassword=$PASS"
} >> "$PROPS"
chmod 600 "$PROPS"

echo
echo "Done. Signing key $KEY_ID created and uploaded."
echo "Credentials saved to $PROPS."
