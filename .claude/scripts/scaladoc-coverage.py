#!/usr/bin/env python3
"""Report Scaladoc coverage of a module's main sources, per package.

Counts API members: declarations at the top level of a file or inside types (class, trait, object, enum,
extension), excluding private, protected and override members and every member of a private or protected
type, except the members of a `private[pkg]` trait, which a public object mixes in. Locals inside method
bodies do not count. It is a heuristic for tracking progress, not a gate: it
never fails a build.

Usage: python3 .claude/scripts/scaladoc-coverage.py <module-dir> [--drift]
  --drift  also list members declared in scala-2/ and scala-3/ (or jvm/ and js/) copies of a file
           whose Scaladoc text differs between the copies.
"""
import dataclasses
import os
import re
import sys
from typing import Optional

_DECL = re.compile(
    r"^(?P<mods>(?:(?:final|sealed|abstract|implicit|lazy|inline|transparent|case|override|private|protected|opaque|infix)"
    r"(?:\[[\w.]+\])?\s+)*)(?P<kind>def|val|var|class|trait|object|type|enum|given)\s+(?P<name>[^\s\[(:=]+)"
)
_HIDDEN = re.compile(r"\b(private|protected|override)\b")
_ANNOTATION = re.compile(r"^@\w+(?:\([^)]*\))?\s+")
_TYPE_KINDS = {"class", "trait", "object", "enum"}
# A line at the same indentation as an open declaration that starts with one of these continues its header
# (`) extends Codec[A] {`), so it does not close the declaration's scope.
_CONTINUATION = re.compile(r"^(\)|\]|extends\b|with\b|derives\b)")


@dataclasses.dataclass
class Decl:
    name: str
    line: int
    doc: Optional[str]


@dataclasses.dataclass
class _Scope:
    indent: int
    is_type: bool  # a class, trait, object, enum, extension or package object: its members are API members
    hidden: bool  # this scope or an enclosing one is private, protected or override
    in_params: bool = False  # inside a multi-line constructor parameter list


def _normalise(comment_lines):
    text = " ".join(re.sub(r"^\s*(/\*\*|\*/|\*)?\s?", "", l).replace("*/", "") for l in comment_lines)
    return " ".join(text.split())


def _open_parens(code):
    code = re.sub(r'"(?:\\.|[^"\\])*"', '""', code)
    code = re.sub(r"'(?:\\.|[^'\\])'", "''", code)
    return code.count("(") + code.count("[") - code.count(")") - code.count("]")


def _is_annotation_line(line):
    code = line.strip()
    while _ANNOTATION.match(code):
        code = _ANNOTATION.sub("", code, count=1)
    return line.strip().startswith("@") and not _DECL.match(code)


def _doc_before(lines, i):
    """Returns the normalised Scaladoc above line `i`, skipping annotation lines, or None if there is none."""
    j = i - 1
    while j >= 0 and _is_annotation_line(lines[j]):
        j -= 1
    if j < 0 or not lines[j].strip().endswith("*/"):
        return None
    k = j
    while k >= 0 and "/*" not in lines[k]:
        k -= 1
    if k < 0 or "/**" not in lines[k]:  # a plain `/* ... */` comment is not Scaladoc
        return None
    return _normalise(lines[k : j + 1])


def scan(text):
    """Returns the API members declared in a source file.

    A member counts when every scope that encloses it is a type (class, trait, object, enum, extension or
    package object) or the top level of the file, and neither it nor an enclosing type is private, protected
    or override; the members of a `private[pkg]` or `protected[pkg]` trait count, because a public object mixes
    the trait in. Locals inside method bodies and members of anonymous classes do not count. Scopes are
    tracked by indentation, which `scalafmt` keeps consistent.
    """
    lines = text.split("\n")
    decls = []
    stack = []
    in_comment = False
    in_string = False
    for i, line in enumerate(lines):
        code = line.strip()
        if in_string:
            in_string = line.count('"""') % 2 == 0
            continue
        if in_comment:
            in_comment = "*/" not in code
            continue
        if not code or code.startswith("//"):
            continue
        if code.startswith("/*"):
            in_comment = "*/" not in code
            continue
        in_string = line.count('"""') % 2 == 1
        indent = len(line) - len(line.lstrip())
        while stack and stack[-1].indent > indent:
            stack.pop()
        top = stack[-1] if stack else None
        if top and top.indent == indent and top.is_type and _CONTINUATION.match(code):
            opened = _open_parens(code)  # `)(` keeps a parameter list open, `) extends` closes it
            top.in_params = (top.in_params and opened >= 0) or opened > 0
            continue
        while stack and stack[-1].indent >= indent:
            stack.pop()
        in_api = all(s.is_type and not s.in_params for s in stack)
        hidden_parent = any(s.hidden for s in stack)
        while _ANNOTATION.match(code):
            code = _ANNOTATION.sub("", code, count=1)
        m = _DECL.match(code)
        if m:
            # Privacy is decided by the modifiers before the keyword only: a `private[schema]` constructor
            # (`class Patch[S] private[schema] (...)`) does not hide the class.
            hidden = hidden_parent or bool(_HIDDEN.search(m.group("mods")))
            if in_api and not hidden:
                decls.append(Decl(f'{m.group("kind")} {m.group("name")}', i + 1, _doc_before(lines, i)))
            is_type = m.group("kind") in _TYPE_KINDS
            # A `private[pkg] trait` is mixed into a public object (`object Schema extends ... with DocsSchemas`):
            # the trait itself is hidden, but its members are API members of that object.
            mixin = m.group("kind") == "trait" and re.search(r"\b(private|protected)\[", m.group("mods"))
            scope_hidden = hidden_parent if mixin else hidden
            stack.append(_Scope(indent, is_type, scope_hidden, is_type and _open_parens(code) > 0))
        elif re.match(r"^extension\b", code) or re.match(r"^package\s+object\b", code):
            stack.append(_Scope(indent, True, hidden_parent))
        elif not re.match(r"^(package|import)\b", code):
            stack.append(_Scope(indent, False, hidden_parent))
    return decls


def package_of(path):
    parts = path.replace("\\", "/").split("/")
    pkg = parts[parts.index("blocks") + 1 : -1] if "blocks" in parts else parts[:-1]
    return ".".join(pkg)


def _main_sources(root):
    for dirpath, _, files in os.walk(root):
        if "/src/main" not in dirpath.replace("\\", "/"):
            continue
        for f in files:
            if f.endswith(".scala"):
                path = os.path.join(dirpath, f)
                yield os.path.relpath(path, root).replace("\\", "/"), path


def coverage(root):
    result = {}
    for rel, path in _main_sources(root):
        with open(path, encoding="utf-8") as fh:
            decls = scan(fh.read())
        total, documented = result.get(package_of(rel), (0, 0))
        result[package_of(rel)] = (total + len(decls), documented + sum(d.doc is not None for d in decls))
    return result


_PAIRS = ((r"/scala-2/", "/scala-3/", "{scala-2,scala-3}"), (r"^jvm/", "js/", "{jvm,js}"))


def _keyed(decls):
    """Key each declaration by (name, occurrence index) so overloads are compared pairwise."""
    seen = {}
    keyed = {}
    for d in decls:
        k = seen.get(d.name, 0)
        seen[d.name] = k + 1
        keyed[(d.name, k)] = d.doc
    return keyed


def drift(root):
    files = {rel: path for rel, path in _main_sources(root)}
    report = []
    for rel in sorted(files):
        for left, right, label in _PAIRS:
            if not re.search(left, rel):
                continue
            other = re.sub(left, right, rel, count=1)
            if other not in files:
                continue
            with open(files[rel], encoding="utf-8") as a, open(files[other], encoding="utf-8") as b:
                docs_a = _keyed(scan(a.read()))
                docs_b = _keyed(scan(b.read()))
            shown = re.sub(left, "/" + label + "/" if left.startswith("/") else label + "/", rel, count=1)
            for key in sorted(docs_a.keys() & docs_b.keys()):
                if docs_a[key] != docs_b[key]:
                    name, k = key
                    report.append(f"{shown}: {name}" + (f" #{k + 1}" if k else ""))
    return report


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    root = argv[1]
    rows = sorted(coverage(root).items(), key=lambda kv: -kv[1][0])
    total = sum(t for _, (t, _) in rows)
    documented = sum(d for _, (_, d) in rows)
    print(f"{'package':32} {'decls':>6} {'doc':>6} {'%':>4}")
    for pkg, (t, d) in rows:
        if t == 0:
            continue
        print(f"{pkg:32} {t:6} {d:6} {100 * d // max(t, 1):4}")
    print(f"{'TOTAL':32} {total:6} {documented:6} {100 * documented // max(total, 1):4}")
    if "--drift" in argv:
        print("\nScaladoc that differs between cross-version copies:")
        for line in drift(root):
            print("  " + line)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
