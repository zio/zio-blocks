# Evaluation Assertions for docs-mdoc-conventions Skill

## Eval 1: New Writer Guidance
**Criteria:**
- ✅ **Explains two-block pattern** — Setup with `mdoc:silent`, then output with `mdoc`
- ✅ **Provides complete code example** — Both the silent and mdoc blocks shown
- ✅ **Shows expected rendering** — What the output looks like to readers
- ✅ **Beginner-friendly** — Clear explanations, no jargon

**Pass if:** 3+ criteria met

---

## Eval 2: Name Conflicts
**Criteria:**
- ✅ **Mentions `mdoc:silent:nest`** — First solution with explanation
- ✅ **Mentions `mdoc:silent:reset`** — Second solution with explanation
- ✅ **Explains when to use each** — Guidance on which to choose
- ✅ **Shows code examples** — At least one working example

**Pass if:** 3+ criteria met

---

## Eval 3: Debugging Broken mdoc
**Criteria:**
- ✅ **Explains isolation** — Correctly identifies that `compile-only` blocks are isolated
- ✅ **Root cause identified** — `Product` not in scope across blocks
- ✅ **Recommends silent+mdoc pattern** — Correct fix strategy
- ✅ **Provides fixed code** — Shows working example

**Pass if:** 3+ criteria met

---

## Eval 4: Complex Example Structure
**Criteria:**
- ✅ **Recommends multi-block approach** — Not one big compile-only
- ✅ **Explains silent for setup** — Types, imports, definitions
- ✅ **Explains mdoc for output** — Showing the result
- ✅ **Provides structure example** — Shows block ordering

**Pass if:** 3+ criteria met

---

## Eval 5: Quick Reference Usage
**Criteria:**
- ✅ **Provides modifier table or one-liners** — Concise summary of 6+ modifiers
- ✅ **Shows decision tree** — Visual or textual flowchart
- ✅ **Usable format** — Can be referenced while writing
- ✅ **Includes practical patterns** — Common use cases

**Pass if:** 3+ criteria met

---

## Eval 6: Scope Persistence
**Criteria:**
- ✅ **Answers first question correctly** — Helper function IS in scope 3 blocks later
- ✅ **Explains why** — Cumulative scope, no reset between blocks
- ✅ **Explains reset behavior** — `mdoc:silent:reset` wipes prior scope
- ✅ **Provides example** — Shows scope before/after reset

**Pass if:** 3+ criteria met

---

## Overall Skill Assessment

| Dimension | What We're Measuring |
|-----------|---------------------|
| **Clarity** | Are explanations understandable to documentation writers? |
| **Completeness** | Does the skill answer the full question? |
| **Actionability** | Can the writer immediately apply the guidance? |
| **Accuracy** | Is the technical information correct? |
| **Helpfulness** | Did the bundled resources (quick-ref, troubleshooting) get used? |

**Pass threshold:** 4/6 evals should pass with 3+ criteria each.
