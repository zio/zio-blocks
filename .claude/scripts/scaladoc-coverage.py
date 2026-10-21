#!/usr/bin/env python3
"""Report Scaladoc coverage of a module's main sources, per package.

Counts API-level declarations: members at an indentation of at most four spaces (top-level types and
their members), excluding private, protected and override members. It is a heuristic for tracking
progress, not a gate: it never fails a build.

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
    r"^((final|sealed|abstract|implicit|lazy|inline|transparent|case|override|private|protected|opaque)"
    r"(\[\w+\])?\s+)*(def|val|var|class|trait|object|type|enum|given)\s+([^\s\[(:=]+)"
)
_HIDDEN = re.compile(r"\b(private|protected|override)\b")


@dataclasses.dataclass
class Decl:
    name: str
    line: int
    doc: Optional[str]


def _normalise(comment_lines):
    text = " ".join(re.sub(r"^\s*(/\*\*|\*/|\*)?\s?", "", l).replace("*/", "") for l in comment_lines)
    return " ".join(text.split())


def scan(text):
    lines = text.split("\n")
    decls = []
    for i, line in enumerate(lines):
        indent = len(line) - len(line.lstrip())
        m = _DECL.match(line.strip())
        if indent > 4 or not m or _HIDDEN.search(line.split("(")[0].split("=")[0]):
            continue
        j = i - 1
        while j >= 0 and lines[j].strip().startswith("@"):
            j -= 1
        doc = None
        if j >= 0 and lines[j].strip().endswith("*/"):
            k = j
            while k >= 0 and "/**" not in lines[k]:
                k -= 1
            doc = _normalise(lines[k : j + 1])
        decls.append(Decl(f"{m.group(4)} {m.group(5)}", i + 1, doc))
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
                docs_a = {d.name: d.doc for d in scan(a.read())}
                docs_b = {d.name: d.doc for d in scan(b.read())}
            shown = re.sub(left, "/" + label + "/" if left.startswith("/") else label + "/", rel, count=1)
            for name in sorted(docs_a.keys() & docs_b.keys()):
                if docs_a[name] != docs_b[name]:
                    report.append(f"{shown}: {name}")
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
        print(f"{pkg:32} {t:6} {d:6} {100 * d // max(t, 1):4}")
    print(f"{'TOTAL':32} {total:6} {documented:6} {100 * documented // max(total, 1):4}")
    if "--drift" in argv:
        print("\nScaladoc that differs between cross-version copies:")
        for line in drift(root):
            print("  " + line)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
