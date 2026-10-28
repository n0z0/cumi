#!/usr/bin/env bash
# Uploads the local release keystore (release.jks + keystore.properties) as the
# GitHub Actions secrets used by .github/workflows/release.yml.
# Requires the GitHub CLI logged in as an account with admin access to the repo.
set -euo pipefail

repo="${1:-n0z0/cumi}"
root="$(cd "$(dirname "$0")/.." && pwd)"
props="$root/keystore.properties"

[ -f "$props" ] || { echo "Missing $props" >&2; exit 1; }
prop() { grep -E "^$1=" "$props" | head -n1 | cut -d= -f2-; }

keystore="$root/$(prop storeFile)"
[ -f "$keystore" ] || { echo "Missing keystore $keystore" >&2; exit 1; }

base64 -w0 "$keystore" | gh secret set SIGNING_KEYSTORE_BASE64 -R "$repo"
prop storePassword | gh secret set SIGNING_STORE_PASSWORD -R "$repo"
prop keyAlias      | gh secret set SIGNING_KEY_ALIAS -R "$repo"
prop keyPassword   | gh secret set SIGNING_KEY_PASSWORD -R "$repo"
echo "Signing secrets set on $repo"
