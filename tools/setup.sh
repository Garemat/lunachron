#!/usr/bin/env bash
# Set up MCP server dependencies for the LunaChron Android codebase.
# Safe to re-run; skips steps that are already complete.
#
# What this installs (all into tools/, gitignored):
#   venv/    Python virtualenv with mcp, tree-sitter, tree-sitter-kotlin
#   jdk17/   Eclipse Temurin 17 JDK (required by kotlin-language-server)
#   kls/     kotlin-language-server 1.3.13

set -euo pipefail
TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Python venv ───────────────────────────────────────────────────────────────
if [ ! -d "$TOOLS/venv" ]; then
  echo "Creating Python venv..."
  python3 -m venv "$TOOLS/venv"
fi
echo "Installing Python packages..."
"$TOOLS/venv/bin/pip" install -q \
  -r "$TOOLS/tree_sitter_mcp/requirements.txt" \
  -r "$TOOLS/kls_mcp/requirements.txt"

# ── JDK 17 ───────────────────────────────────────────────────────────────────
if [ ! -d "$TOOLS/jdk17" ]; then
  echo "Downloading JDK 17 (Temurin)..."
  ARCH=$(uname -m)
  case "$ARCH" in
    x86_64)  JDK_ARCH="x64" ;;
    aarch64) JDK_ARCH="aarch64" ;;
    *)       echo "Unsupported arch: $ARCH"; exit 1 ;;
  esac
  JDK_URL=$(curl -sL \
    "https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=${JDK_ARCH}&image_type=jdk&jvm_impl=hotspot&os=linux&vendor=eclipse" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['binary']['package']['link'])")
  TMP=$(mktemp -d)
  curl -L --progress-bar "$JDK_URL" -o "$TMP/jdk17.tar.gz"
  tar xzf "$TMP/jdk17.tar.gz" -C "$TOOLS/"
  mv "$TOOLS"/jdk-* "$TOOLS/jdk17"
  rm -rf "$TMP"
  echo "JDK 17 installed."
fi

# ── kotlin-language-server ────────────────────────────────────────────────────
if [ ! -d "$TOOLS/kls" ]; then
  echo "Downloading kotlin-language-server..."
  KLS_URL=$(curl -sL "https://api.github.com/repos/fwcd/kotlin-language-server/releases/latest" \
    | python3 -c "import json,sys; r=json.load(sys.stdin); print(next(a['browser_download_url'] for a in r['assets'] if a['name'].endswith('.zip')))")
  TMP=$(mktemp -d)
  curl -L --progress-bar "$KLS_URL" -o "$TMP/kls.zip"
  unzip -q "$TMP/kls.zip" -d "$TOOLS/kls"
  chmod +x "$TOOLS/kls/server/bin/kotlin-language-server"
  rm -rf "$TMP"
  echo "kotlin-language-server installed."
fi

echo ""
echo "Setup complete. MCP servers are ready."
echo "Reload Claude Code in the lunachron/ directory to activate them."
