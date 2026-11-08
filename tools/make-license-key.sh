#!/usr/bin/env bash
# Creates the key pair that signs subscription licences. Run ONCE, in Google Cloud Shell:
#   bash tools/make-license-key.sh
set -euo pipefail

DIR="$HOME/.samillimed"
KEY="$DIR/license-private.pem"
mkdir -p "$DIR"
chmod 700 "$DIR"

if [ -f "$KEY" ]; then
  echo "A private key already exists, so nothing was changed."
else
  openssl ecparam -name prime256v1 -genkey -noout -out "$KEY"
  chmod 600 "$KEY"
  echo "Private key created at $KEY"
fi

echo
echo "PUBLIC KEY (not secret; send this to Claude so it goes into the app):"
echo
openssl ec -in "$KEY" -pubout -outform DER 2>/dev/null | base64 -w0
echo
echo
echo "IMPORTANT: back up the PRIVATE key now. Without it you can never issue licences that this app accepts."
echo "Run:   cat $KEY"
echo "Copy the whole output, including the BEGIN and END lines, into a password manager."
echo "Never put it in GitHub and never send it to anyone."
