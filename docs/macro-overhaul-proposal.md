# Urgent: Scope Macro Infrastructure Requires Complete Overhaul

## Executive Summary

The scope library's macro infrastructure suffers from **massive code duplication**, **inconsistent bug fixes**, **feature drift between Scala versions**, and **inadequate testing**. This document presents evidence for an immediate, comprehensive overhaul.

---

## 1. Critical Bugs Still Present After "Fixes"

### Bug: Diamond Dependency Sharing (Partially Fixed)

The diamond dependency sharing bug was fixed in 4 locations but **remains unfixed in at least one more**:

**Fixed locations:**
- [ResourceMacros.scala (Scala 3)](../scope/shared/src/main/scala-3/zio/blocks/scope/ResourceMacros.scala#L211-L214)
- [WireMacros.scala (Scala 3)](../scope/shared/src/main/scala-3/zio/blocks/scope/WireMacros.scala#L71-L74)
- [ResourceVersionSpecific.scala (Scala 2)](../scope/shared/src/main/scala-2/zio/blocks/scope/ResourceVersionSpecific.scala#L184-L187)
- [WireVersionSpecific.scala (Scala 2)](../scope/shared/src/main/scala-2/zio/blocks/scope/WireVersionSpecific.scala#L119-L122)
- [WireableVersionSpecific.scala (Scala 2)](../scope/shared/src/main/scala-2/zio/blocks/scope/WireableVersionSpecific.scala#L178-L181)

**Also fixed (discovered during this audit):**
- [WireCodeGen.scala (Scala 3) line 372](../scope/shared/src/main/scala-3/zio/blocks/scope/internal/WireCodeGen.scala#L370-L373): Was using `Context.empty` instead of accumulated context

**The fact that this bug existed in 6 separate locations demonstrates the core problem: identical logic duplicated across files inevitably leads to inconsistent fixes.**

---

## 2. Massive Code Duplication

### 2.1 Duplication Within Scala 3

[WireCodeGen.scala](../scope/shared/src/main/scala-3/zio/blocks/scope/internal/WireCodeGen.scala) contains **three near-identical implementations**:

| Function | Lines | Purpose |
|----------|-------|---------|
| `deriveWire` | 29-127 | Generate Wire from constructor |
| `deriveWireable` | 160-246 | Generate Wireable from constructor |
| `deriveWireableWithOverrides` | 255-391 | Generate Wireable with wire overrides |

All three contain:
- Identical class validation (lines 39-46, 166-173, 261-268)
- Identical `generateArgTerm` helper (lines 64-77, 191-204, 310-331)
- Identical `generateWireBody` helper (lines 79-105, 206-232, 333-363)
- Identical constructor call generation

**Estimated duplicated code: ~200 lines within a single file.**

### 2.2 ResourceMacros Doesn't Use WireCodeGen

[ResourceMacros.scala](../scope/shared/src/main/scala-3/zio/blocks/scope/ResourceMacros.scala) duplicates all of WireCodeGen's logic:

- `generateArgTerm` (lines 158-175) — identical pattern
- `buildOverrideContext` (lines 205-217) — identical pattern
- Wire type extraction (lines 128-135) — identical pattern
- Dependency checking (lines 137-149) — identical pattern

**WireCodeGen was created to consolidate this, but ResourceMacros was never migrated to use it.**

### 2.3 Complete Duplication Between Scala 2 and Scala 3

Every macro has **two complete implementations** with nearly identical logic:

| Scala 3 File | Scala 2 File | Duplicated Logic |
|--------------|--------------|------------------|
| [ResourceMacros.scala](../scope/shared/src/main/scala-3/zio/blocks/scope/ResourceMacros.scala) | [ResourceVersionSpecific.scala](../scope/shared/src/main/scala-2/zio/blocks/scope/ResourceVersionSpecific.scala) | 225 lines vs 237 lines |
| [WireMacros.scala](../scope/shared/src/main/scala-3/zio/blocks/scope/WireMacros.scala) | [WireVersionSpecific.scala](../scope/shared/src/main/scala-2/zio/blocks/scope/WireVersionSpecific.scala) | 98 lines vs 146 lines |
| [ScopeMacros.scala](../scope/shared/src/main/scala-3/zio/blocks/scope/ScopeMacros.scala) | [ScopeMacros.scala](../scope/shared/src/main/scala-2/zio/blocks/scope/ScopeMacros.scala) | 52 lines vs 159 lines |
| [WireableMacros (in WireableVersionSpecific)](../scope/shared/src/main/scala-3/zio/blocks/scope/WireableVersionSpecific.scala) | [WireableVersionSpecific.scala](../scope/shared/src/main/scala-2/zio/blocks/scope/WireableVersionSpecific.scala) | 60 lines vs 237 lines |
| [MacroCore.scala](../scope/shared/src/main/scala-3/zio/blocks/scope/internal/MacroCore.scala) | [MacroCore.scala](../scope/shared/src/main/scala-2/zio/blocks/scope/internal/MacroCore.scala) | 620 lines vs 295 lines |

### 2.4 Error Rendering Duplication

[MacroCore.scala (Scala 3)](../scope/shared/src/main/scala-3/zio/blocks/scope/internal/MacroCore.scala#L310-L550) and [MacroCore.scala (Scala 2)](../scope/shared/src/main/scala-2/zio/blocks/scope/internal/MacroCore.scala#L157-L213) both contain:
- Identical `Colors` object
- Identical `ErrorRenderer` object
- Identical error message formatting

**This is pure text generation that could be shared between Scala versions.**

---

## 3. Feature Drift Between Scala Versions

### 3.1 Scala 3 Has Features Scala 2 Lacks

[MacroCore.scala (Scala 3)](../scope/shared/src/main/scala-3/zio/blocks/scope/internal/MacroCore.scala) defines error types that don't exist in Scala 2:
- `MissingDependency` with dependency tree visualization (lines 84-93)
- `DuplicateProvider` (lines 95-101)
- `DependencyCycle` with ASCII cycle visualization (lines 103-108)

The Scala 2 version only has:
- `NotAClass`
- `NoPrimaryCtor`
- `SubtypeConflict`

**Users get different error messages depending on which Scala version they use.**

### 3.2 Scala 3 Uses WireCodeGen, Scala 2 Doesn't

Scala 3's [ScopeMacros](../scope/shared/src/main/scala-3/zio/blocks/scope/ScopeMacros.scala#L28) delegates to `WireCodeGen.deriveWire`, but Scala 2's [ScopeMacros](../scope/shared/src/main/scala-2/zio/blocks/scope/ScopeMacros.scala#L94-L158) has its own `deriveWire` implementation.

---

## 4. Inadequate Test Coverage

### 4.1 No Cross-Entry-Point Tests

There are **7 different entry points** that all generate similar code:
1. `shared[T]`
2. `unique[T]`
3. `Resource.from[T]`
4. `Resource.from[T](wires*)`
5. `Wireable.from[T]`
6. `Wireable.from[T](wires*)`
7. `wire.toResource(wires*)`

**There is no test that verifies all 7 produce equivalent behavior for the same dependency graph.**

### 4.2 Diamond Dependency Test Added Late

[DependencySharingSpec.scala](../scope/shared/src/test/scala/zio/blocks/scope/DependencySharingSpec.scala) was just added to test diamond dependencies. Before this, **the bug existed in all entry points and went undetected**.

### 4.3 No Equivalence Tests Between Scala Versions

There are no tests verifying that Scala 2 and Scala 3 produce semantically equivalent results.

---

## 5. Maintenance Burden

### Current State

To fix a bug or add a feature:
1. Fix in [WireCodeGen.scala](../scope/shared/src/main/scala-3/zio/blocks/scope/internal/WireCodeGen.scala) (3 places)
2. Fix in [ResourceMacros.scala](../scope/shared/src/main/scala-3/zio/blocks/scope/ResourceMacros.scala)
3. Fix in [WireMacros.scala](../scope/shared/src/main/scala-3/zio/blocks/scope/WireMacros.scala)
4. Port to [ResourceVersionSpecific.scala](../scope/shared/src/main/scala-2/zio/blocks/scope/ResourceVersionSpecific.scala) (Scala 2)
5. Port to [WireVersionSpecific.scala](../scope/shared/src/main/scala-2/zio/blocks/scope/WireVersionSpecific.scala) (Scala 2)
6. Port to [WireableVersionSpecific.scala](../scope/shared/src/main/scala-2/zio/blocks/scope/WireableVersionSpecific.scala) (Scala 2)
7. Port to [ScopeMacros.scala](../scope/shared/src/main/scala-2/zio/blocks/scope/ScopeMacros.scala) (Scala 2)

**That's 7+ files to update for every change, with no automated verification of consistency.**

---

## 6. Proposed Solution

### 6.1 Consolidate Scala 3 Macros

1. **Delete** `ResourceMacros.scala` — migrate to use `WireCodeGen`
2. **Refactor** `WireCodeGen.scala` to eliminate internal duplication:
   - Single `generateArgTerm` function
   - Single `generateWireBody` function
   - Single `buildContext` function
   - Parameterize on output type (Wire vs Wireable vs Resource)

### 6.2 Create Shared Logic Layer

Extract Scala-version-independent logic:
- Error message strings
- Dependency analysis algorithms (as pure data transformations)
- Context-building strategy

### 6.3 Comprehensive Test Suite

Create a test matrix:

```
Entry Points × Scala Versions × Scenarios
─────────────────────────────────────────
shared[T]              │ Scala 2.13 │ Simple (no deps)
unique[T]              │ Scala 3.x  │ Single dep
Resource.from[T]       │            │ Multiple deps
Resource.from[T](w*)   │            │ Diamond pattern
Wireable.from[T]       │            │ AutoCloseable
Wireable.from[T](w*)   │            │ With Finalizer param
wire.toResource(w*)    │            │ Mixed shared/unique
```

### 6.4 Property-Based Equivalence Tests

Add tests that verify:
- `shared[T].toResource()` ≡ `Resource.from[T]` (for same T)
- `Wireable.from[T].wire` ≡ `shared[T]` (for same T)
- Scala 2 output ≡ Scala 3 output (runtime behavior)

---

## 7. Estimated Effort

| Task | Effort |
|------|--------|
| Fix remaining WireCodeGen bug | S (1 hour) |
| Consolidate WireCodeGen internally | M (4-8 hours) |
| Migrate ResourceMacros to WireCodeGen | M (4-8 hours) |
| Extract shared error rendering | S (2-4 hours) |
| Comprehensive test matrix | L (8-16 hours) |
| **Total** | **~20-40 hours** |

---

## 8. Risk of Inaction

Without this overhaul:
- **Every future bug fix risks being incomplete** (as the diamond bug was)
- **Feature parity between Scala versions will diverge further**
- **Maintenance burden will continue to grow**
- **Users will encounter inconsistent behavior across entry points**

---

## Conclusion

The current macro infrastructure is a **maintenance nightmare** that actively introduces bugs and inconsistencies. A focused 1-2 week effort to consolidate and properly test this code will pay dividends for the lifetime of the project.
