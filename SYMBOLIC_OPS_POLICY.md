# Symbolic Operator Policy

## The Two Rules

1. **Named-First** — Every symbolic operator MUST alias a named method. The name is the primary API; the symbol is sugar.
2. **Read-Aloud Test** — If the target audience can't instantly read the symbol aloud, use a name instead.

## Allowed Operators

### Tier 1 — Universal (any programmer)

| Symbols | Domain |
|---|---|
| `+` `-` `*` `/` `%` | Arithmetic |
| `<` `>` `<=` `>=` `===` `!=` | Comparison |
| `&&` `\|\|` `unary_!` | Boolean logic |
| `&` `\|` `^` `~` `<<` `>>` `>>>` | Bitwise (integral types) |

### Tier 2 — Scala-standard (Scala devs expect these)

| Symbol | Reads as | Precedent |
|---|---|---|
| `++` | "concat" | `Seq.++` |
| `:+` | "append" | `Seq.:+` |
| `+:` | "prepend" | `Seq.+:` |
| `/` | "slash" / "child" | Path composition |
| `:=` | "assign" | Config/template DSLs |

### Tier 3 — Domain-scoped DSL (obvious to practitioners of that domain)

Allowed **only** when ALL of these hold:

1. **Domain-native** — The symbol mirrors notation that practitioners already use outside of Scala (CSS syntax, SQL operators, regex, math notation, etc.)
2. **Scoped** — The operator lives inside a DSL-specific package/object, not in general-purpose APIs.
3. **Still aliases a named method** — Rule 1 still applies, no exceptions.
4. **Documented at DSL entry point** — A quick-reference table of the DSL's operators exists in its Scaladoc or docs page.

Examples of what qualifies:

| DSL | Operator | Why it's clear |
|---|---|---|
| CSS | `:=` for property values | CSS devs write `color: red` — colon-assign is natural |
| CSS | `-` in compound names | CSS devs write `font-size` — hyphen is native CSS |
| HTML | `:=` for attributes | `href := "/home"` reads like `href="/home"` |
| Path | `/` for segments | Universal path separator |
| JSON | `/` for pointer paths | RFC 6901 notation |

Examples of what does NOT qualify:

| Rejected | Why |
|---|---|
| `~>` for CSS transitions | Not how CSS notation works — invented symbolism |
| `\|+\|` for style merging | Category theory, not CSS |
| `>>>` for selector chaining | Multi-arrow chain, not domain-native |

## Banned (no exceptions)

- **Multi-arrow chains**: `>+>` `>=>` `>>>` `<<<` `==>` `~>` `<~`
- **Category-theory art**: `|+|` `<*>` `<+>` `*>` `<*` `>>=`
- **Ambiguous singles**: `<>` `?` postfix `!`
- **Unicode**: `⊛` `∘` `≟` `η`
- **Any symbol with 3+ distinct punctuation characters**
- **Invented symbolism** — symbols that look related to the domain but aren't actually used in it

## Decision Flowchart

```
Tier 1 or 2?  → YES → Use it (alias a named method)
               → NO  → Is it domain-native for a scoped DSL?
                         → YES + all 4 criteria met → Use it (alias a named method)
                         → NO → Use a named method. Period.
```

## Enforcement

- Every `def <symbol>` delegates to a named `def`.
- Named method appears first in source; symbolic alias follows.
- Scaladoc goes on the named method, not the symbol.
- Tier 3 operators require a DSL operator table in the package/module docs.
