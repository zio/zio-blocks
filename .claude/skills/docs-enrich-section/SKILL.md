---
name: docs-enrich-section
description: Use when a documentation section exists but lacks motivation or use-cases — thin sections that show a signature and a toy example but never explain why a reader would choose this API over alternatives.
---

# Enrich a Documentation Section with Motivation and Use-Cases

## Overview

A thin section answers *what* but not *why*. Readers landing on such a section cannot judge when
to use the API or how it fits into the larger picture. This skill turns a thin section into one
that answers: what does this return, why does it exist, when is it the right choice, and what does
a realistic use look like.

## Signals That a Section Needs Enriching

- Shows only a signature + a trivial example (toy type, no realistic scenario)
- No mention of alternatives or when *not* to use this API
- A reader could not decide between this and the nearest related operation from the section alone
- Opening sentence restates the method name without adding context

## Source Research (Do This First)

Before writing a word of prose, read the implementation:

1. **Read the source** — understand what the method actually does, not just its signature
2. **Find the contrast** — locate the nearest alternative (e.g. `rebind` vs `toSchema`) and
   understand the exact difference in return type, requirements, and guarantees
3. **Find real usage** — search the docs and example files for existing uses of this API to
   anchor your realistic example
4. **Identify the gap** — ask: "In what situation would a reader need this but *not* the
   alternative?" That gap is the motivation.

## The Five-Part Expansion Pattern

Replace the thin section with these five parts, in order:

### 1. Opening sentence
State what the method returns and the one-line rule for when to use it. Lead with the return type
and the key constraint that distinguishes it from alternatives.

> `DynamicSchema#toSchema` returns a `Schema[DynamicValue]` — it stays fully in the dynamic world
> and requires no bindings. Use it when you have received a `DynamicSchema` over the wire and need
> a codec-compatible schema that enforces structural conformance without binding any Scala types.

### 2. Motivation paragraph
Explain the gap the method fills. Name the scenario where the alternative fails or is
impractical. Name the concrete contexts (middleware, gateways, converters, validators) where
this method is the right tool.

### 3. Contrast sentence or table
State explicitly: "Use X when … Use Y instead when …". One sentence is enough if the distinction
is clear; a two-row table if the dimensions are multiple.

| Situation | Right choice |
|---|---|
| No Scala types available; need structural validation only | `toSchema` |
| Have a `BindingResolver`; need a fully operational `Schema[A]` | `rebind[A]` |

### 4. Signature block
Keep the existing signature block unchanged. Precede it with a bridging sentence ending in `:`.

### 5. Realistic example
Replace any toy example (single-field type, no context) with a scenario that could exist in a
real application. The scenario should exercise the method's distinguishing behavior — the part
that makes it different from the alternative.

Checklist for the example:
- [ ] Models a plausible real scenario (gateway, registry, pipeline, validator)
- [ ] Uses `mdoc:compile-only`
- [ ] Imports everything it needs
- [ ] No hardcoded output comments (`// None`, `// "hello"`, etc.)
- [ ] Preceded by a prose sentence ending in `:`

## Common Mistakes

| Mistake | Fix |
|---|---|
| Motivation paragraph is abstract ("useful in many cases") | Name one concrete scenario. Abstract motivation helps nobody. |
| Contrast buried at the end | Put it before the signature block, after motivation |
| Example uses the same toy type as before | Create a new type that reflects the motivated use-case |
| Prose sentence before code does not end with `:` | Every sentence immediately before a code fence must end with `:` |
| Added output comments to show what expressions return | Delete them — mdoc evaluates and renders output automatically |

## Verification

After enriching the section, run the mdoc compilation check to ensure all code examples are syntactically correct and type-check:

```bash
# Single file:
sbt "docs/mdoc --in <path/to/file.md>"

# Multiple files — repeat --in/--out pairs:
sbt "docs/mdoc --in docs/reference/file1.md --out out/file1.md --in docs/reference/file2.md --out out/file2.md"
```

> **Never use bare `sbt docs/mdoc`** without `--in` — it recompiles all documentation (~90 seconds).

**Success criterion:** The output contains **zero `[error]` lines**. Warnings are acceptable.

**If mdoc reports errors:** Fix them immediately before marking the enrichment as complete. Do not commit or claim the work is done until all errors are resolved.
