#!/usr/bin/env bash
# Issues a signed subscription licence code for one facility.
# Run in Google Cloud Shell AFTER you have confirmed the payment in your own M-Pesa statement:
#   bash tools/issue-license.sh
set -euo pipefail

DIR="$HOME/.samillimed"
KEY="$DIR/license-private.pem"
LEDGER="$DIR/licenses.csv"
[ -f "$KEY" ] || { echo "No signing key yet. Run first: bash tools/make-license-key.sh"; exit 1; }
for tool in openssl jq base64 awk; do
  command -v "$tool" >/dev/null 2>&1 || { echo "$tool is not installed."; exit 1; }
done

b64url() { base64 -w0 | tr '+/' '-_' | tr -d '='; }
die() { echo; echo "STOPPED: $1"; exit 1; }

read -r -p "Facility project ID (shown on the app's Subscription screen): " PROJECT
read -r -p "Plan name (for example Standard): " PLAN
read -r -p "Users allowed (a number): " USERS
read -r -p "Months to add (1, 3, 12...): " MONTHS
read -r -p "M-Pesa transaction code (or TRIAL / GIFT): " TX

PROJECT=$(echo "$PROJECT" | tr -d ' ')
PLAN=$(echo "$PLAN" | tr -d ',')
TX=$(echo "$TX" | tr '[:lower:]' '[:upper:]' | tr -d ' ,')
[[ "$PROJECT" =~ ^[a-z][a-z0-9-]{3,}$ ]] || die "That is not a valid project ID."
[ -n "$PLAN" ] || die "A plan name is required."
{ [[ "$USERS" =~ ^[0-9]+$ ]] && [ "$USERS" -ge 1 ]; } || die "Users must be a number of 1 or more."
{ [[ "$MONTHS" =~ ^[0-9]+$ ]] && [ "$MONTHS" -ge 1 ] && [ "$MONTHS" -le 36 ]; } || die "Months must be between 1 and 36."
[ -n "$TX" ] || die "A transaction code (or TRIAL / GIFT) is required."

[ -f "$LEDGER" ] || echo "id,date,project,plan,users,from,until,tx" > "$LEDGER"

# One payment can only be used once.
if [ "$TX" != "TRIAL" ] && [ "$TX" != "GIFT" ]; then
  if awk -F, -v t="$TX" 'NR>1 && $8==t {f=1} END{exit !f}' "$LEDGER"; then
    die "Transaction $TX was already used for a licence. See $LEDGER"
  fi
fi

# A renewal starts when the current licence ends, so no paid days are lost.
TODAY=$(date +%F)
LAST=$(awk -F, -v p="$PROJECT" 'NR>1 && $3==p {print $7}' "$LEDGER" | sort | tail -1)
BASE="$TODAY"
if [ -n "$LAST" ] && [[ "$LAST" > "$TODAY" ]]; then BASE="$LAST"; fi
UNTIL=$(date -d "$BASE +$MONTHS months" +%F)

ID=$(openssl rand -hex 4)
PAYLOAD=$(jq -cn --arg id "$ID" --arg p "$PROJECT" --arg plan "$PLAN" --argjson u "$USERS" \
  --arg d1 "$TODAY" --arg d2 "$UNTIL" \
  '{"v":1,"id":$id,"project":$p,"plan":$plan,"users":$u,"from":$d1,"until":$d2}')

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
printf '%s' "$PAYLOAD" > "$TMP/payload"
openssl dgst -sha256 -sign "$KEY" -out "$TMP/sig" "$TMP/payload"

# Self-check: the signature must verify with the matching public key.
openssl ec -in "$KEY" -pubout -out "$TMP/pub.pem" 2>/dev/null
openssl dgst -sha256 -verify "$TMP/pub.pem" -signature "$TMP/sig" "$TMP/payload" >/dev/null \
  || die "The signature self-check failed. Nothing was recorded."

CODE="SM1.$(b64url < "$TMP/payload").$(b64url < "$TMP/sig")"
echo "$ID,$TODAY,$PROJECT,$PLAN,$USERS,$TODAY,$UNTIL,$TX" >> "$LEDGER"

echo
echo "=============================================================="
echo " LICENCE ISSUED  (signature self-check passed)"
echo "=============================================================="
echo " Facility: $PROJECT"
echo " Plan:     $PLAN, up to $USERS users"
echo " Valid to: $UNTIL"
echo
echo " Send this whole code to the facility. It works only for that facility:"
echo
echo "$CODE"
echo
echo "=============================================================="
echo " Recorded in $LEDGER"
