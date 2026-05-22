#!/usr/bin/env bash
TOOLS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$TOOLS/venv/bin/python3" "$TOOLS/tree_sitter_mcp/server.py"
