#!/usr/bin/env python3
"""
KLS MCP server for the LunaChron Android codebase.
Wraps kotlin-language-server to provide type-aware cross-file navigation.

Tools accept symbol names rather than file positions so Claude doesn't need
to track exact byte offsets.

First startup is slow (20-30s) while KLS indexes via Gradle; subsequent starts
use the cached index (~/.cache/lunachron-kls/) and take a few seconds.
"""

from __future__ import annotations

import json
import os
import queue
import subprocess
import threading
import time
from pathlib import Path
from typing import Optional
from urllib.parse import urlparse, unquote

from mcp.server.fastmcp import FastMCP

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

TOOLS_DIR   = Path(__file__).resolve().parent.parent   # lunachron/tools/
ANDROID_DIR = TOOLS_DIR.parent                         # lunachron/
KLS_BIN     = TOOLS_DIR / "kls" / "server" / "bin" / "kotlin-language-server"
JAVA_HOME   = TOOLS_DIR / "jdk17"
KLS_CACHE   = Path.home() / ".cache" / "lunachron-kls"

SRC_ROOT = ANDROID_DIR / "app" / "src" / "main" / "java"

# ---------------------------------------------------------------------------
# Environment for the KLS subprocess
# ---------------------------------------------------------------------------

def _build_env() -> dict[str, str]:
    env = os.environ.copy()
    env["JAVA_HOME"] = str(JAVA_HOME)
    env["PATH"] = str(JAVA_HOME / "bin") + ":" + env.get("PATH", "")
    env["HOME"] = str(Path.home())

    # Give Gradle enough heap
    env["GRADLE_OPTS"] = "-Xmx1g"
    env["JAVA_TOOL_OPTIONS"] = "-Xmx1g"

    # Try to find Android SDK so Gradle can resolve Android deps
    for candidate in [
        Path.home() / "Android" / "Sdk",
        Path.home() / ".android" / "sdk",
        Path("/opt/android-sdk"),
    ]:
        if candidate.exists():
            env["ANDROID_HOME"] = str(candidate)
            break

    return env

# ---------------------------------------------------------------------------
# LSP client
# ---------------------------------------------------------------------------

class LSPClient:
    """Thread-safe JSON-RPC client over a KLS subprocess's stdio."""

    def __init__(self) -> None:
        self._proc: Optional[subprocess.Popen] = None
        self._next_id = 1
        self._pending: dict[int, queue.Queue] = {}
        self._lock = threading.Lock()
        self._open_uris: set[str] = set()
        self._ready = threading.Event()
        self._start_error: Optional[str] = None

    # ── lifecycle ────────────────────────────────────────────────────────────

    def ensure_ready(self) -> Optional[str]:
        """Start KLS if not already running. Returns error string or None."""
        with self._lock:
            if self._proc is not None:
                return self._start_error
        self._launch()
        return self._start_error

    def _launch(self) -> None:
        KLS_CACHE.mkdir(parents=True, exist_ok=True)
        env = _build_env()

        try:
            proc = subprocess.Popen(
                [str(KLS_BIN)],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                env=env,
                cwd=str(ANDROID_DIR),
            )
        except Exception as exc:
            self._start_error = f"Failed to start KLS: {exc}"
            return

        with self._lock:
            self._proc = proc

        reader = threading.Thread(target=self._read_loop, daemon=True)
        reader.start()

        try:
            self._do_initialize()
        except Exception as exc:
            self._start_error = f"KLS initialize failed: {exc}"

    def _do_initialize(self) -> None:
        resp = self._request("initialize", {
            "processId": None,
            "rootUri": ANDROID_DIR.as_uri(),
            "capabilities": {
                "textDocument": {
                    "definition": {"dynamicRegistration": False, "linkSupport": False},
                    "references": {"dynamicRegistration": False},
                    "hover": {
                        "dynamicRegistration": False,
                        "contentFormat": ["plaintext"],
                    },
                },
                "workspace": {
                    "symbol": {"dynamicRegistration": False},
                },
            },
            "initializationOptions": {
                "storagePath": str(KLS_CACHE),
                "kotlin": {
                    "compiler": {"jvm": {"target": "17"}},
                    "inlayHints": {"typeHints": False, "parameterHints": False},
                    "completion": {"snippets": {"enabled": False}},
                },
            },
        }, timeout=120.0)

        if "error" in resp:
            raise RuntimeError(resp["error"])

        self._notify("initialized", {})
        self._ready.set()

    # ── low-level I/O ────────────────────────────────────────────────────────

    def _read_loop(self) -> None:
        while True:
            msg = self._read_one()
            if msg is None:
                break
            req_id = msg.get("id")
            if req_id is not None:
                with self._lock:
                    q = self._pending.get(req_id)
                if q is not None:
                    q.put(msg)

    def _read_one(self) -> Optional[dict]:
        proc = self._proc
        if proc is None or proc.stdout is None:
            return None
        headers: dict[str, str] = {}
        while True:
            raw = proc.stdout.readline()
            if not raw:
                return None
            line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
            if not line:
                break
            key, _, val = line.partition(":")
            headers[key.strip()] = val.strip()
        length = int(headers.get("Content-Length", 0))
        if not length:
            return None
        body = b""
        while len(body) < length:
            chunk = proc.stdout.read(length - len(body))
            if not chunk:
                return None
            body += chunk
        return json.loads(body)

    def _write(self, msg: dict) -> None:
        data = json.dumps(msg).encode("utf-8")
        frame = f"Content-Length: {len(data)}\r\n\r\n".encode() + data
        with self._lock:
            proc = self._proc
        if proc and proc.stdin:
            proc.stdin.write(frame)
            proc.stdin.flush()

    def _request(self, method: str, params: dict, timeout: float = 30.0) -> dict:
        with self._lock:
            req_id = self._next_id
            self._next_id += 1
            q: queue.Queue = queue.Queue()
            self._pending[req_id] = q

        self._write({"jsonrpc": "2.0", "id": req_id, "method": method, "params": params})

        try:
            return q.get(timeout=timeout)
        except queue.Empty:
            return {"error": f"Timeout waiting for {method} response"}
        finally:
            with self._lock:
                self._pending.pop(req_id, None)

    def _notify(self, method: str, params: dict) -> None:
        self._write({"jsonrpc": "2.0", "method": method, "params": params})

    # ── document management ──────────────────────────────────────────────────

    def _open(self, path: Path) -> str:
        """Open a file in KLS if not already open; returns its URI."""
        uri = path.as_uri()
        if uri not in self._open_uris:
            self._notify("textDocument/didOpen", {
                "textDocument": {
                    "uri": uri,
                    "languageId": "kotlin",
                    "version": 1,
                    "text": path.read_text(encoding="utf-8"),
                }
            })
            self._open_uris.add(uri)
            time.sleep(0.3)
        return uri

    # ── LSP requests ─────────────────────────────────────────────────────────

    def workspace_symbol(self, query: str) -> list[dict]:
        resp = self._request("workspace/symbol", {"query": query})
        return resp.get("result") or []

    def references(self, uri: str, line: int, char: int) -> list[dict]:
        resp = self._request("textDocument/references", {
            "textDocument": {"uri": uri},
            "position": {"line": line, "character": char},
            "context": {"includeDeclaration": True},
        })
        return resp.get("result") or []

    def definition(self, uri: str, line: int, char: int) -> list[dict]:
        resp = self._request("textDocument/definition", {
            "textDocument": {"uri": uri},
            "position": {"line": line, "character": char},
        })
        result = resp.get("result") or []
        return result if isinstance(result, list) else [result]

    def hover(self, uri: str, line: int, char: int) -> Optional[str]:
        resp = self._request("textDocument/hover", {
            "textDocument": {"uri": uri},
            "position": {"line": line, "character": char},
        })
        result = resp.get("result")
        if not result:
            return None
        contents = result.get("contents", {})
        if isinstance(contents, dict):
            return contents.get("value") or contents.get("language")
        if isinstance(contents, list):
            return "\n".join(
                c.get("value", c) if isinstance(c, dict) else str(c) for c in contents
            )
        return str(contents)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

_kls = LSPClient()
mcp = FastMCP("lunachron-kls")


def _uri_to_rel(uri: str) -> str:
    path = Path(unquote(urlparse(uri).path))
    try:
        return str(path.relative_to(SRC_ROOT))
    except ValueError:
        try:
            return str(path.relative_to(ANDROID_DIR))
        except ValueError:
            return str(path)


def _fmt_location(loc: dict) -> str:
    uri = loc.get("uri", "")
    start = loc.get("range", {}).get("start", {})
    line = start.get("line", 0) + 1  # LSP is 0-indexed
    return f"{_uri_to_rel(uri)}:{line}"


def _resolve_symbol(name: str) -> Optional[tuple[str, int, int]]:
    """Return (uri, line, char) for a symbol, with exact position via text search.

    workspace/symbol only stores the container class location for nested types
    (e.g. sealed interface variants always show as line 1). We refine the
    position by scanning the file for the actual declaration line.
    """
    symbols = _kls.workspace_symbol(name)
    match = next((s for s in symbols if s.get("name") == name), None)
    if match is None:
        return None

    loc = match.get("location", {})
    uri = loc.get("uri", "")
    path = Path(unquote(urlparse(uri).path))
    if not path.exists():
        return None

    _kls._open(path)

    decl_keywords = ("class ", "object ", "fun ", "val ", "var ", "interface ", "typealias ")
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines()):
        if any(kw in line for kw in decl_keywords):
            col = line.find(name)
            if col >= 0:
                return uri, i, col

    start = loc.get("range", {}).get("start", {})
    return uri, start.get("line", 0), start.get("character", 0)


def _ensure_ready() -> Optional[str]:
    err = _kls.ensure_ready()
    if err:
        return err
    if not _kls._ready.wait(timeout=120.0):
        return "KLS did not finish initializing within 120s"
    return None

# ---------------------------------------------------------------------------
# MCP tools
# ---------------------------------------------------------------------------

@mcp.tool()
def kls_symbols(query: str) -> str:
    """
    Search for Kotlin symbols by name across the whole project using the
    Kotlin compiler — returns type-accurate results with file and line.
    Supports partial name matching (e.g. "TroupeFav" finds ToggleTroupeFavourite).

    Use this when you need to locate a symbol and tree-sitter find_definition
    has gaps, or when you need the compiler's understanding of the symbol kind.
    """
    if err := _ensure_ready():
        return f"KLS not ready: {err}"

    symbols = _kls.workspace_symbol(query)
    if not symbols:
        return f"No symbols found for '{query}'"

    lines = []
    for sym in symbols:
        name = sym.get("name", "?")
        kind = _SYMBOL_KINDS.get(sym.get("kind", 0), "?")
        container = sym.get("containerName", "")
        loc = _fmt_location(sym.get("location", {}))
        container_str = f" [{container}]" if container else ""
        lines.append(f"{loc}  {kind} {name}{container_str}")

    return "\n".join(lines)


@mcp.tool()
def kls_find_references(symbol_name: str) -> str:
    """
    Find all usages of a Kotlin symbol across the codebase using the compiler.
    More accurate than text search — respects scope and type resolution.
    Works best on symbols in the pure Kotlin domain layer (CharacterEvent
    variants, CharacterState fields, Character model types, etc.).

    symbol_name: exact symbol name, e.g. "ToggleTroupeFavourite", "Troupe", "isFavourite"
    """
    if err := _ensure_ready():
        return f"KLS not ready: {err}"

    resolved = _resolve_symbol(symbol_name)
    if resolved is None:
        return (
            f"Symbol '{symbol_name}' not found via workspace/symbol.\n"
            f"Tip: try kls_symbols('{symbol_name}') first to confirm the exact name."
        )

    uri, line, char = resolved
    path = Path(unquote(urlparse(uri).path))
    if path.exists():
        _kls._open(path)

    refs = _kls.references(uri, line, char)
    if not refs:
        return f"No references found for '{symbol_name}'"

    locations = sorted({_fmt_location(r) for r in refs})
    return f"References to '{symbol_name}' ({len(locations)}):\n" + "\n".join(locations)


@mcp.tool()
def kls_type_at(file: str, line: int, character: int) -> str:
    """
    Get the type of the expression at a specific position using the Kotlin
    compiler. Useful after reading a file when you need to understand what
    type a variable or expression resolves to.

    file:      filename or relative path, e.g. "CharacterViewModel.kt"
    line:      1-based line number
    character: 0-based column/character offset
    """
    if err := _ensure_ready():
        return f"KLS not ready: {err}"

    p = Path(file)
    if not p.is_absolute():
        candidates = list(SRC_ROOT.rglob(p.name if p.suffix else p.name + ".kt"))
        if not candidates:
            return f"File not found: {file}"
        p = candidates[0]

    uri = _kls._open(p)
    result = _kls.hover(uri, line - 1, character)  # LSP is 0-indexed
    if result is None:
        return f"No type info at {file}:{line}:{character}"
    return result


# ── Symbol kind names (LSP spec) ─────────────────────────────────────────────

_SYMBOL_KINDS = {
    1: "File", 2: "Module", 3: "Namespace", 4: "Package",
    5: "Class", 6: "Method", 7: "Property", 8: "Field",
    9: "Constructor", 10: "Enum", 11: "Interface", 12: "Function",
    13: "Variable", 14: "Constant", 15: "String", 16: "Number",
    17: "Boolean", 18: "Array", 19: "Object", 20: "Key",
    21: "Null", 22: "EnumMember", 23: "Struct", 24: "Event",
    25: "Operator", 26: "TypeParameter",
}

if __name__ == "__main__":
    mcp.run()
