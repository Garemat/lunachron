#!/usr/bin/env python3
"""
Tree-sitter MCP server for the LunaChron Android codebase.
Provides token-efficient structural queries over Kotlin source files
without requiring full file reads.
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import Optional

from mcp.server.fastmcp import FastMCP
import tree_sitter_kotlin as tskotlin
from tree_sitter import Language, Parser, Node

# ---------------------------------------------------------------------------
# Setup
# ---------------------------------------------------------------------------

KOTLIN = Language(tskotlin.language())
_parser = Parser(KOTLIN)

REPO_ROOT = Path(__file__).resolve().parent.parent.parent   # lunachron/
SRC_ROOT  = REPO_ROOT / "app" / "src" / "main" / "java"

mcp = FastMCP("lunachron-kotlin")

DECLARATION_KINDS = frozenset({
    "class_declaration",
    "function_declaration",
    "object_declaration",
    "property_declaration",
    "type_alias",
})

BODY_KINDS = frozenset({"function_body", "class_body"})

# ---------------------------------------------------------------------------
# Core helpers
# ---------------------------------------------------------------------------

def _parse(path: Path) -> tuple[bytes, Node]:
    source = path.read_bytes()
    return source, _parser.parse(source).root_node


def _text(source: bytes, node: Node) -> str:
    return source[node.start_byte:node.end_byte].decode("utf-8")


def _signature(source: bytes, node: Node) -> str:
    """Declaration text up to (but not including) the body block."""
    end = node.end_byte
    for child in node.children:
        if child.type in BODY_KINDS:
            end = child.start_byte
            break
    raw = source[node.start_byte:end].decode("utf-8").strip()
    return re.sub(r"\s+", " ", raw)  # collapse interior whitespace


def _name(node: Node) -> str:
    """Declared name of a declaration node (first 'identifier' child)."""
    for child in node.children:
        if child.type == "identifier":
            return child.text.decode("utf-8") if child.text else ""
    return ""


def _has_annotation(source: bytes, node: Node, annotation: str) -> bool:
    for child in node.children:
        if child.type == "modifiers" and f"@{annotation}" in _text(source, child):
            return True
    return False


def _resolve(path: str) -> Optional[Path]:
    """Accept several path formats and resolve to an absolute Path.

    - Relative to SRC_ROOT: "io/github/garemat/lunachron/CharacterEvent.kt"
    - Filename only:         "CharacterEvent.kt"
    - Without extension:     "CharacterEvent"
    - Dotted package:        "io.github.garemat.lunachron.CharacterEvent"
    """
    # Dotted package style (no slashes, may or may not end in .kt)
    if "/" not in path and "\\" not in path and "." in path and not path.endswith(".kt"):
        path = path.replace(".", "/") + ".kt"

    p = Path(path)

    if p.is_absolute():
        return p if p.exists() else None

    # Relative to SRC_ROOT (e.g. "io/github/.../Foo.kt" or "ui/Foo.kt")
    candidate = SRC_ROOT / p
    if candidate.exists():
        return candidate

    # Bare filename (with or without .kt)
    name = p.name if p.suffix else p.name + ".kt"
    matches = [m for m in SRC_ROOT.rglob(name) if "test" not in str(m).lower()]
    if not matches:
        matches = list(SRC_ROOT.rglob(name))
    return matches[0] if len(matches) == 1 else (matches[0] if matches else None)


def _format(source: bytes, nodes: list[Node], depth: int = 0) -> list[str]:
    """Format declarations as indented signature lines, recursing into class bodies."""
    prefix = "  " * depth
    lines = []
    for node in nodes:
        if node.type not in DECLARATION_KINDS:
            continue
        lines.append(f"{prefix}{_signature(source, node)}")
        for child in node.children:
            if child.type in BODY_KINDS:
                nested = [c for c in child.children if c.type in DECLARATION_KINDS]
                if nested:
                    lines.extend(_format(source, nested, depth + 1))
    return lines


# ---------------------------------------------------------------------------
# MCP tools
# ---------------------------------------------------------------------------

@mcp.tool()
def outline_file(path: str) -> str:
    """
    Return all top-level declarations in a Kotlin file — class, function, and
    property signatures without bodies. For sealed classes and interfaces,
    includes all nested variants. Use this instead of reading the full file
    when you need to understand what a file contains.

    path: filename ("CharacterEvent.kt"), relative path ("ui/HomeScreen.kt"),
          or dotted package name ("io.github.garemat.lunachron.CharacterEvent")
    """
    resolved = _resolve(path)
    if resolved is None:
        return f"File not found: {path}"

    source, root = _parse(resolved)
    top = [c for c in root.children if c.type in DECLARATION_KINDS]
    lines = _format(source, top)

    rel = resolved.relative_to(SRC_ROOT)
    header = f"// {rel}\n"
    return header + ("\n".join(lines) if lines else "(no declarations found)")


@mcp.tool()
def list_composables(path: str) -> str:
    """
    List every @Composable function signature in a Kotlin file, without bodies.
    More focused than outline_file for navigating large UI files like
    ActiveGameScreen.kt (1500 lines) or CommonComponents.kt (1600 lines).

    path: filename or relative path, e.g. "ActiveGameScreen.kt"
    """
    resolved = _resolve(path)
    if resolved is None:
        return f"File not found: {path}"

    source, root = _parse(resolved)
    results: list[str] = []

    def walk(node: Node, depth: int = 0) -> None:
        if node.type == "function_declaration" and _has_annotation(source, node, "Composable"):
            results.append("  " * depth + _signature(source, node))
        for child in node.children:
            walk(child, depth + (1 if node.type == "class_body" else 0))

    walk(root)
    rel = resolved.relative_to(SRC_ROOT)
    if not results:
        return f"// {rel}\n(no @Composable functions found)"
    return f"// {rel} — {len(results)} composable(s)\n" + "\n".join(results)


@mcp.tool()
def find_definition(name: str) -> str:
    """
    Find where a Kotlin symbol is defined — class, function, object, sealed
    variant, property, typealias, etc. Returns file path and line number.
    Searches the entire lunachron Android source tree.

    name: symbol name, e.g. "CharacterEvent", "ThemedCard", "ToggleTroupeFavourite"
    """
    pattern = re.compile(
        rf"(?:^|[ \t])(?:class|interface|object|fun|val|var|sealed|data|enum|typealias)"
        rf"(?:[ \t]+(?:class|interface|object))*[ \t]+{re.escape(name)}\b",
        re.MULTILINE,
    )

    results: list[str] = []
    for kt in sorted(SRC_ROOT.rglob("*.kt")):
        try:
            text = kt.read_text(encoding="utf-8")
        except Exception:
            continue
        for m in pattern.finditer(text):
            line_no = text[: m.start()].count("\n") + 1
            line = text.splitlines()[line_no - 1].strip()
            rel = kt.relative_to(SRC_ROOT)
            results.append(f"{rel}:{line_no}  {line}")

    return "\n".join(results) if results else f"No definition found for '{name}'"


@mcp.tool()
def get_declaration(path: str, name: str) -> str:
    """
    Get the full source of a specific named declaration from a file — useful
    when you need one function's implementation without reading the whole file.

    path: filename or relative path, e.g. "CharacterViewModel.kt"
    name: declaration name, e.g. "onEvent", "HealthPipsChunked", "ThemedCard"
    """
    resolved = _resolve(path)
    if resolved is None:
        return f"File not found: {path}"

    source, root = _parse(resolved)

    def find(node: Node) -> Optional[Node]:
        if node.type in DECLARATION_KINDS and _name(node) == name:
            return node
        for child in node.children:
            result = find(child)
            if result:
                return result
        return None

    found = find(root)
    if found is None:
        return f"'{name}' not found in {path}"

    line_start = source[: found.start_byte].count(b"\n") + 1
    rel = resolved.relative_to(SRC_ROOT)
    return f"// {rel}:{line_start}\n{_text(source, found)}"


if __name__ == "__main__":
    mcp.run()
