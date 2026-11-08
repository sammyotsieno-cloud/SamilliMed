#!/usr/bin/env bash
# Creates a complete facility on its own Firebase project and prints its setup code.
# Run in Google Cloud Shell (shell.cloud.google.com), inside the cloned repository:
#   bash tools/new-facility.sh
set -euo pipefail
cd "$(dirname "$0")/.."

PACKAGE="org.samillimed.app"
REGION="africa-south1"
FACILITY_ID="main"
PROJECT=""
trap 'echo; echo "The script stopped. Project so far: ${PROJECT:-none yet}"' ERR

die() { echo; echo "STOPPED: $1"; exit 1; }
for tool in gcloud jq curl openssl; do
  command -v "$tool" >/dev/null 2>&1 || die "$tool is not installed."
done
if ! command -v firebase >/dev/null 2>&1; then
  echo "Installing the Firebase CLI..."
  curl -sL firebase.tools | bash
fi

read -r -p "Facility name (for example Kilimani Pharmacy): " NAME
read -r -p "Facility administrator's full name: " ADMIN_NAME
read -r -p "Facility administrator's email: " EMAIL
EMAIL=$(echo "$EMAIL" | tr '[:upper:]' '[:lower:]' | tr -d ' ')
{ [ -n "$NAME" ] && [ -n "$ADMIN_NAME" ] && [[ "$EMAIL" == *@*.* ]]; } \
  || die "Name, administrator and a valid email are all required."

SLUG=$(echo "$NAME" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/-/g; s/--*/-/g; s/^-//; s/-$//' | cut -c1-14 | sed 's/-$//')
[ -n "$SLUG" ] || SLUG="facility"
PROJECT="sm-${SLUG}-$(openssl rand -hex 2)"
PASSWORD=$(openssl rand -base64 12 | tr -dc 'A-Za-z0-9' | cut -c1-10)
echo "Project ID will be: $PROJECT"

echo; echo "1/7  Sign in to Firebase (open the link, approve, paste the code). Use the SAME Google account as Cloud Shell."
firebase login --no-localhost

echo; echo "2/7  Creating the project..."
firebase projects:create "$PROJECT" --display-name "$NAME"

echo; echo "3/7  Registering the Android app..."
firebase apps:create ANDROID "SamilliMed" --package-name "$PACKAGE" --project "$PROJECT"
APP_ID=$(firebase apps:list ANDROID --project "$PROJECT" --json | jq -r '.result[0].appId')
{ [ -n "$APP_ID" ] && [ "$APP_ID" != "null" ]; } || die "Could not read the app ID."
CONFIG=$(firebase apps:sdkconfig ANDROID "$APP_ID" --project "$PROJECT" | sed -n '/^{/,$p')
API_KEY=$(echo "$CONFIG" | jq -r '.client[0].api_key[0].current_key')
{ [ -n "$API_KEY" ] && [ "$API_KEY" != "null" ]; } || die "Could not read the API key."

echo; echo "4/7  Creating the database in $REGION..."
gcloud services enable firestore.googleapis.com identitytoolkit.googleapis.com --project "$PROJECT"
gcloud firestore databases create --location="$REGION" --project "$PROJECT"

echo; echo "5/7  Publishing rules and indexes..."
firebase deploy --only firestore --project "$PROJECT"

echo; echo "6/7  Turning on email sign-in..."
TOKEN=$(gcloud auth print-access-token)
api() {
  curl -sS -H "Authorization: Bearer $TOKEN" -H "X-Goog-User-Project: $PROJECT" \
    -H "Content-Type: application/json" "$@"
}
RES=$(api -X PATCH \
  "https://identitytoolkit.googleapis.com/admin/v2/projects/$PROJECT/config?updateMask=signIn.email" \
  -d '{"signIn":{"email":{"enabled":true,"passwordRequired":true}}}')
if echo "$RES" | grep -q '"error"'; then
  echo "$RES" | head -c 400; echo
  echo "Email sign-in could not be switched on automatically. Do this one step by hand:"
  echo "Firebase console > $PROJECT > Authentication > Get started > Email/Password > Enable > Save."
  read -r -p "Press Enter when done... " _
fi

echo; echo "7/7  Creating the administrator and the facility records..."
USER_JSON=$(api -X POST "https://identitytoolkit.googleapis.com/v1/projects/$PROJECT/accounts" \
  -d "$(jq -n --arg e "$EMAIL" --arg p "$PASSWORD" --arg n "$ADMIN_NAME" \
        '{email:$e,password:$p,displayName:$n}')")
USER_ID=$(echo "$USER_JSON" | jq -r '.localId // empty')
[ -n "$USER_ID" ] || { echo "$USER_JSON" | head -c 400; die "Could not create the administrator account."; }

fs() {
  api -X PATCH "https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents/$1" -d "$2" >/dev/null
}
fs "facilities/$FACILITY_ID" "$(jq -n --arg n "$NAME" \
  '{fields:{name:{stringValue:$n},status:{stringValue:"active"}}}')"
fs "users/$USER_ID" "$(jq -n --arg e "$EMAIL" --arg n "$ADMIN_NAME" --arg f "$FACILITY_ID" \
  '{fields:{email:{stringValue:$e},name:{stringValue:$n},facilityId:{stringValue:$f},role:{stringValue:"admin"},active:{booleanValue:true}}}')"

echo
echo "=============================================================="
echo " FACILITY READY: $NAME"
echo "=============================================================="
echo " Setup code (paste it on the facility's phone, 'Set up a facility'):"
echo
echo "   $PROJECT|$APP_ID|$API_KEY"
echo
echo " Administrator sign-in:  $EMAIL"
echo " Temporary password:     $PASSWORD"
echo "=============================================================="
