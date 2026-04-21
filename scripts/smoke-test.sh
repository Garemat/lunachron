#!/usr/bin/env bash
# Run a Haiku-powered smoke test against the app on a connected emulator/device.
# Usage: ./scripts/smoke-test.sh [device-serial]
#
# Requires:
#   - android-mcp installed and on PATH  (pip install android-mcp)
#   - claude CLI installed               (npm install -g @anthropic-ai/claude-code)
#   - Android emulator or device running with the app installed
set -euo pipefail

PACKAGE="io.github.garemat.lunachron"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

DEVICE="${1:-}"
ADB_TARGET=( adb )
[[ -n "$DEVICE" ]] && ADB_TARGET=( adb -s "$DEVICE" )

pass() { echo "  ✓ $*"; }
fail() { echo "  ✗ $*" >&2; exit 1; }

# --- Resolve latest version tag (needed by preflight) ---
LATEST_TAG=$(git tag --sort=-v:refname | grep -E '^v[0-9]' | head -1)
if [[ -z "$LATEST_TAG" ]]; then
  echo "Error: no version tags found in this repo." >&2
  exit 1
fi
VERSION="${LATEST_TAG#v}"

# ---------------------------------------------------------------------------
# Preflight — deterministic checks before the agent runs
# ---------------------------------------------------------------------------
echo ""
echo "Preflight checks..."

# 1. ADB available
command -v adb &>/dev/null || fail "adb not found on PATH"
pass "adb found"

# 2. Device visible
DEVICE_STATE=$( "${ADB_TARGET[@]}" get-state 2>/dev/null || true )
[[ "$DEVICE_STATE" == "device" ]] || fail "no device in 'device' state (got: '${DEVICE_STATE:-none}') — is the emulator running?"
pass "device online"

# 3. App installed
"${ADB_TARGET[@]}" shell pm list packages 2>/dev/null | grep -q "^package:${PACKAGE}$" \
  || fail "app not installed on device (${PACKAGE})"
pass "app installed"

# 4b. Download and install the release APK from GitHub
APK_TMP=$(mktemp /tmp/lunachron-XXXXXX.apk)
echo "  Downloading ${LATEST_TAG} APK..."
gh release download "$LATEST_TAG" \
  --repo Garemat/lunachron \
  --pattern "app-github-release.apk" \
  --output "$APK_TMP" 2>/dev/null \
  || fail "could not download APK for ${LATEST_TAG} — is gh authenticated?"
"${ADB_TARGET[@]}" install -r "$APK_TMP" &>/dev/null \
  || fail "adb install failed"
rm -f "$APK_TMP"
pass "release APK installed (${LATEST_TAG})"

# 5. App launches without immediate crash — force-stop first for a clean slate
"${ADB_TARGET[@]}" shell am force-stop "$PACKAGE" 2>/dev/null || true
"${ADB_TARGET[@]}" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 &>/dev/null \
  || fail "app failed to launch via monkey"
sleep 3
# Check logcat for a fatal exception in the last 5 s of logs
CRASH=$( "${ADB_TARGET[@]}" logcat -d -t 100 2>/dev/null \
  | grep -E "AndroidRuntime.*FATAL EXCEPTION|Process: ${PACKAGE}.*has died" || true )
[[ -z "$CRASH" ]] || fail "crash detected on launch:\n${CRASH}"
pass "app launched without crash"

echo ""

# --- Extract changelog section for this version ---
CHANGELOG_SECTION=$(awk "
  /^## \[${VERSION}\]/ { found=1; next }
  /^## \[/             { if (found) exit }
  found                { print }
" CHANGELOG.md | sed '/^[[:space:]]*$/d')

if [[ -z "$CHANGELOG_SECTION" ]]; then
  CHANGELOG_SECTION="(no changelog entry found for ${LATEST_TAG})"
fi

# --- Get files changed since previous tag ---
PREV_TAG=$(git tag --sort=-v:refname | grep -E '^v[0-9]' | sed -n '2p')
if [[ -n "$PREV_TAG" ]]; then
  CHANGED_FILES=$(git diff "${PREV_TAG}...${LATEST_TAG}" --name-only \
    | grep -v '^CHANGELOG\|^data\.version\|^app/build\.gradle' || true)
else
  CHANGED_FILES=""
fi

# --- Build device hint for the prompt ---
if [[ -n "$DEVICE" ]]; then
  DEVICE_HINT="Target device serial: ${DEVICE}. Pass this as the 'device' parameter to all MCP tool calls."
else
  DEVICE_HINT="Use the default connected device (omit the 'device' parameter from tool calls)."
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  LunaChron smoke test — ${LATEST_TAG}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Changes in this release:"
echo "$CHANGELOG_SECTION"
if [[ -n "$CHANGED_FILES" ]]; then
  echo ""
  echo "Files changed:"
  echo "$CHANGED_FILES"
fi
echo ""
echo "Preflight passed — handing off to agent..."
echo ""

# Build changed files section for prompt
if [[ -n "$CHANGED_FILES" ]]; then
  FILES_CONTEXT="Files modified in this release (use these to infer which screens to focus on):
${CHANGED_FILES}

"
else
  FILES_CONTEXT=""
fi

PROMPT="You are a QA tester for the LunaChron Moonstone companion Android app \
(package: io.github.garemat.lunachron).

Version ${LATEST_TAG} was just published to the Play Store. Here is what changed in this release:

${CHANGELOG_SECTION}

${FILES_CONTEXT}${DEVICE_HINT}

Your job: test the app on the emulator as a real user would. Focus on the features and fixes \
listed above, but also do a quick sanity check of the core flows so we catch obvious regressions.

Core flows to verify:
- App launches without crashing
- Home screen loads with news feed
- Bottom nav tabs are accessible (Compendium, Play, Troupes, Campaigns)
- At least one feature from the changelog above

Use the Android MCP tools to interact with the device. Start by launching the app, then navigate \
and interact naturally. Prefer get_ui_hierarchy over screenshot for navigation decisions — it is \
faster and cheaper. Only use screenshot when verifying something visual (colours, layout, theme).

For each area you test, report:
- What you tested
- Whether it worked correctly
- Any issues — crashes, slow loading (note if something took more than ~2 s), visual glitches, \
  or unexpected behaviour

Be concise and specific. Structure your final output as a short findings list. \
If everything looks fine, say so."

claude --model claude-haiku-4-5-20251001 --dangerously-skip-permissions -p "$PROMPT"
