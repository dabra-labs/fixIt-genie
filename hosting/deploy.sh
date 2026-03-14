#!/bin/bash
# Deploy FixIt Genie demo page to Firebase Hosting
# Usage: ./hosting/deploy.sh

set -e

cd "$(dirname "$0")"

echo "Deploying to https://fixit-genie.web.app ..."
firebase deploy --only hosting --project rational-investor-cf3ff

echo ""
echo "✓  Live at https://fixit-genie.web.app"
echo "✓  Blog at https://fixit-genie.web.app/blog.html"
