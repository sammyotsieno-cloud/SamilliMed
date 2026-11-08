#!/usr/bin/env bash
# Publishes this repository's rules and indexes to one or more facility projects:
#   bash tools/deploy-rules.sh project-one project-two
set -euo pipefail
cd "$(dirname "$0")/.."
[ "$#" -ge 1 ] || { echo "Usage: bash tools/deploy-rules.sh <projectId> [<projectId> ...]"; exit 1; }
for p in "$@"; do
  echo "== $p"
  firebase deploy --only firestore --project "$p"
done
