# Scope.md Consolidation Plan: Removing Remaining Duplications

**Status:** ✅ CONSOLIDATION COMPLETE
**Initial size:** 1020 lines | **Final size:** 983 lines | **Savings:** 37 lines (3.6%)
**Completed:** 4 consolidation commits

---

## Executive Summary

The scope.md reference document has been partially deduplicated (previous work removed ~65 lines), but significant duplicate patterns remain. The primary duplications involve:

1. **Database class definition** (11 occurrences) — can be unified
2. **Scope.global.scoped boilerplate** (17+ occurrences) — can be streamlined
3. **$(db)(_.query(...)) pattern** (6+ occurrences) — can reference canonical example
4. **allocate(Resource.fromAutoCloseable(...))** (5+ occurrences) — can cross-reference
5. **Scope.global.open() pattern** (2 occurrences) — variant can reference primary
6. **Database + Connection nesting** (2 similar occurrences) — verify necessity

---

## Execution Summary

### What Was Completed

**4 consolidation commits executed:**

1. **Canonicalize Database Class Definition** (commit 7b648f6c)
   - Marked Quickstart Database as canonical definition
   - Standardized 11 Database occurrences throughout document
   - Consistent: `final class`, `query()` method, `println("db closed")`

2. **Reduce Boilerplate in Compile Errors** (commit a0dbe0ff)
   - Consolidated `Scope.global.scoped { scope => import scope.* }` pattern
   - Showed boilerplate once at section top with reference to Quickstart
   - Simplified error examples to show only the violation
   - Simplified fixes to show only the corrected code
   - **Saved: 9 lines**

3. **Consolidate $ Operator Usage Examples** (commit 398309d7)
   - Moved comprehensive $ operator example from Best Practices to Core Operations
   - Added cross-reference from Best Practices instead
   - Eliminated redundant Database class definition
   - **Saved: 18 lines**

4. **Add Cross-References to Pattern Sections** (commit 8e2e0f46)
   - Pattern 1: Removed full example, referenced Quickstart and Core Operations
   - Pattern 2: Clarified key differentiator, added Scope#open reference
   - **Saved: 13 lines**

**Total consolidation: 37 lines saved** (1020 → 983 lines)

### Verification

- ✅ **mdoc compilation:** 0 errors, 26 warnings (unrelated to scope.md)
- ✅ **Lint checks:** All pass (`sbt "++2.13; check"`)
- ✅ **Cross-references:** All valid
- ✅ **Content preservation:** No information lost, all distinct concepts retained
- ✅ **Pedagogical clarity:** Maintained through strategic cross-references

---

## Duplication Inventory

### 1. Database Class Definition — 11 Occurrences (HIGH PRIORITY)

**Locations:**
- Line 70-73: Quickstart (canonical, with query())
- Line 164-167: Scope#open section
- Line 201-204: allocate section
- Line 230-233: $ operator (single value)
- Line 255-258: $ operator (multiple values)
- Line 292-295: lower operation
- Line 426-429: Pattern 1: Lexical Scopes
- Line 464-466: Pattern 2: Explicit Scopes
- Line 525-527: Nesting and Hierarchies
- Line 672-674: Best Practices entry point
- Line 701-704: Best Practices control flow

**Variation patterns:**
- Some use `final class`, others `class`
- Some include `query(sql: String)` method
- Different `close()` implementation styles

**Consolidation Strategy:**
1. Make Quickstart definition (lines 70-73) the canonical version
2. Add explicit label: "**Canonical Database definition** — we'll reuse this pattern throughout"
3. Replace 10 duplicates with cross-references and minimal definitions
4. For minimal examples, use: "using the same `Database` class from the Quickstart"

**Expected Savings:** 20-30 lines

---

### 2. `Scope.global.scoped { scope => import scope.* }` Boilerplate — 17+ Occurrences (MEDIUM PRIORITY)

**Sections with full boilerplate:**
- Quickstart (lines 76-77)
- Construction (lines 141-142, 206-207)
- Core Operations (lines 235-236, 265-266, 297-298, 332-333, 366-367, 400-401)
- Scope Patterns (lines 431-432, 654-655, 679-680, 706-707)
- Compile Errors (lines 587-588, 597-598, 609-610, 620-621)

**Consolidation Strategy:**
1. Keep full boilerplate in Quickstart (already teaches it)
2. For sections with short examples (< 8 lines), keep boilerplate for clarity
3. For sections with longer examples, use short inline comment: `// Scope.global.scoped { scope => import scope.* ...`
4. Add section note after Quickstart: "All examples below use this scoping pattern"

**Expected Savings:** 10-15 lines

---

### 3. Query Usage Pattern — 6+ Occurrences (MEDIUM PRIORITY)

**Locations:**
- Line 83: Quickstart — `$(db)(_.query("SELECT 1"))`
- Line 241: $ operator — single value (infix)
- Line 244: $ operator — single value (unqualified)
- Line 436: Pattern 1
- Line 600: Compile error fix — `$(db)(_.query("data"))`
- Line 623: Compile error fix (duplicate of above)
- Line 710: Best Practices control flow

**Problem:** Lines 597-602 and 619-625 show nearly identical "correct" vs. "error" examples

**Consolidation Strategy:**
1. Keep the comprehensive single-value $ explanation (lines 225-248) as canonical
2. For Compile Errors section, show only the **violation line**, not the full setup
3. Example fix:
   ```scala
   // ✗ ERROR: cannot pass as argument
   $(db)(d => store(d))

   // ✓ CORRECT: only call methods
   $(db)(_.query("data"))
   ```
4. Remove the duplicate error setup code

**Expected Savings:** 15-20 lines

---

### 4. Allocation Patterns — 5+ Occurrences (LOW-MEDIUM PRIORITY)

**Locations:**
- Line 80: Quickstart (Resource factory)
- Line 171: open() section
- Line 211: allocate section (shows both forms)
- Line 434: Pattern 1
- Line 471: Pattern 2
- Line 536: Nesting section

**Pattern:** `allocate(Resource.fromAutoCloseable(new Database))`

**Consolidation Strategy:**
1. Core Operations allocate section (lines 209-216) already shows both approaches with explanation
2. In Pattern 1 & 2, reference this section instead of repeating
3. Use short form: "Allocate the database (see [Core Operations — allocate](#-scope-allocate--acquire-a-resource))"
4. Keep full examples only where the pattern is the focus

**Expected Savings:** 8-12 lines

---

### 5. Scope.global.open() Pattern — 2 Occurrences (LOW PRIORITY)

**Locations:**
- Lines 161-179: Construction — Scope#open (foundational definition)
- Lines 469-481: Pattern 2 (function-based variant)

**Consolidation Strategy:**
1. Keep foundational definition in Scope#open as-is (lines 161-179)
2. Pattern 2 section should focus on the **functional wrapper** aspect
3. In Pattern 2, show only:
   ```scala
   def acquireDatabase(): Scope.OpenScope = {
     val os = Scope.global.open()
     val _ = os.scope.allocate(Resource.fromAutoCloseable(new Database))
     os
   }
   ```
4. Add prose: "See [Scope#open](#scopeopen--create-an-unowned-child-scope) for details on scope initialization and finalization"

**Expected Savings:** 4-6 lines

---

### 6. Database + Connection Nesting — 2 Occurrences (LOW PRIORITY / VERIFY)

**Locations:**
- Lines 522-550: "Nesting and Hierarchies" section
- Nested within the same section — verify if this is intentional duplication or structural

**Consolidation Strategy:**
1. Review whether both demonstrations are needed for learning
2. If pedagogically distinct, keep both with clear labeling
3. If redundant, consolidate to single example with extended prose

**Expected Savings:** 0-15 lines (conditional on review)

---

### 7. Compile Error Examples — 4 Related Occurrences (MEDIUM PRIORITY)

**Locations:**
- Lines 586-602: "No given instance of Unscoped" error + fix
- Lines 608-625: "Scoped values may only be used as a method receiver" error + fix

**Problem:** All four code blocks show `val db: $[Database] = allocate(new Database)` as boilerplate

**Consolidation Strategy:**
1. Add a short section note before these errors:
   "In these examples, we allocate a Database as follows (expanded for clarity):"
   ```scala
   val db: $[Database] = allocate(new Database)
   ```
2. Each error example then shows only the usage violation and fix
3. Reduces the setup code duplication from 4 repetitions to 1

**Expected Savings:** 6-10 lines

---

## Implementation Roadmap

### Commit Sequence

Each consolidation becomes a separate commit (following project conventions):

**1. Canonicalize Database class definition** (20-30 line reduction)
   - Explicitly label Quickstart's Database as canonical
   - Replace 4-5 full definitions in Core Operations sections with cross-references
   - Update remaining definitions to use canonical form where helpful

**2. Reduce Scope.global.scoped boilerplate** (10-15 line reduction)
   - Add explanatory note in Quickstart or Safety Model
   - Replace verbose boilerplate in Compile Errors section with comments
   - Keep boilerplate in Pattern sections for clarity

**3. Consolidate $ operator query usage pattern** (15-20 line reduction)
   - Keep comprehensive example in Core Operations `$` section
   - Simplify Compile Error examples to show only the violation
   - Remove duplicate "correct" form from multiple places

**4. Cross-reference allocation patterns** (8-12 line reduction)
   - Update Pattern 1 & 2 sections to reference Core Operations allocate section
   - Keep examples focused on pattern-specific mechanics

**5. Simplify Scope.global.open() in Pattern 2** (4-6 line reduction)
   - Focus Pattern 2 code on function wrapping
   - Cross-reference Scope#open for initialization/finalization details

**6. Verify and consolidate nesting examples** (0-15 line reduction)
   - Review necessity of both examples
   - Consolidate or clearly differentiate if both needed

---

## Verification Strategy

After each commit:

```bash
# Verify mdoc compilation with specific file
sbt "docs/mdoc --in docs/reference/resource-management-di/scope.md"

# Check line count
wc -l docs/reference/resource-management-di/scope.md

# Verify lint checks
sbt "++2.13; check"
```

**Success criteria:**
- 0 mdoc errors
- Final line count: ~935-940 (targeting 60-85 line reduction)
- All lint checks pass
- Cross-references remain valid

---

## Content Quality Targets

**After consolidation, the document should:**

1. **Teach once, reference often** — Each pattern shown fully in one place, referenced elsewhere
2. **Reduce visual noise** — Less boilerplate duplication makes examples easier to scan
3. **Improve navigation** — Readers can find the canonical explanation and follow links
4. **Maintain completeness** — All necessary information preserved; nothing lost
5. **Preserve pedagogical clarity** — Examples still provide full context where needed

---

## Risk Mitigation

**Potential issues and mitigations:**

| Issue | Mitigation |
|-------|-----------|
| Breaking cross-references | Test all anchor links after consolidation |
| Loss of beginner-friendly context | Keep full examples in foundational sections (Quickstart, Pattern 1, Pattern 2) |
| mdoc compilation issues | Run verification after each commit |
| Reader confusion from fewer examples | Add clear prose and link guidance between sections |

---

## Rollback Plan

If consolidation causes issues:
- Each commit is independent
- Can revert individual commits with `git revert`
- Full rollback to pre-consolidation state available via git history

---

## Next Steps

1. **Review this plan** with stakeholder feedback
2. **Prioritize commits** — Recommend starting with highest-ROI (Database canonicalization)
3. **Execute sequentially** — One commit at a time, verifying after each
4. **Track metrics** — Line count, mdoc errors, lint status
5. **Document learnings** — Update CLAUDE.md or memory with consolidation patterns

---

**Estimated total effort:** 2-3 hours implementation + verification
**Expected benefit:** More maintainable, faster-to-read documentation
**Total expected line reduction:** 60-85 lines (~6-8% of document)
