# ScopeLift Redesign - Design Document

## IMPORTANT: Read This First (Lessons from Failed Experiments)

This document chronicles an attempt to fix ScopeLift unsoundness using Scala 3 macros.
**The approach described here DOES NOT WORK.** Read the "Why This Approach Failed" section
before attempting anything similar.

### Why This Approach Failed

1. **Subtype checking doesn't work across path-dependent types at macro expansion time**
   - `child.ScopeTag <:< parent.ScopeTag` returns `false` in the macro
   - The compiler can't prove subtype relationships when types come from different scope instances
   - This breaks any approach relying on `<:<` checks

2. **TermRef name matching is unsound**
   - We can extract `TermRef.name` (e.g., "child" from `child.ScopeTag`)
   - BUT: stripping by name strips ALL tags with different names, including sibling-scoped tags
   - Sibling-scoped values capture dead resources - stripping their tags allows use-after-close

3. **Inline methods + existential types don't mix**
   - `scopedMacro` returns `Any` because we can't express the output type
   - Nested `scopedMacro` calls fail because the parent scope has existential type `Scope[?, ?]`
   - This makes inline/macro-based `scoped` impractical

4. **The fundamental problem remains unsolved**
   - At compile time, we can't distinguish "current closing child" from "already-dead sibling"
   - Both have different TermRef names from parent
   - Both are (in principle) subtypes of parent, but macro can't prove it

### What Might Work Instead

1. **The Original/Intersection design** (discussed but not implemented):
   - Track birth scope (`Original`) separately from accumulated tags (`Intersection`)
   - At boundary, check if `ChildTag <:< Original` to decide if promotion is safe
   - BUT: still requires working subtype checks, which don't work at macro time

2. **Runtime tagging**:
   - Attach scope identity at runtime, not just compile time
   - Check scope liveness at runtime when forcing thunks
   - Trades compile-time safety for actual soundness

3. **Stricter API**:
   - Reject ALL child-scoped values at boundaries (no scopedUnscoped)
   - Force users to explicitly extract values while scope is open
   - Sound but restrictive

4. **Accept partial unsoundness**:
   - Document that sibling-scoped values reaching boundaries are UB
   - If each boundary handles its own children correctly, siblings shouldn't exist
   - Relies on invariants holding, not type system enforcement

## Problem Statement

The current `scopedUnscoped` instance in `ScopeLift` is **UNSOUND**:

```scala
given scopedUnscoped[A, T, S](using Unscoped[A]): ScopeLift.Aux[A @@ T, S, A]
```

It outputs raw `A` instead of `A @@ S` (parent-scoped), losing all scope tracking. Values become accessible even after the parent scope closes.

## Core Concepts

### What is `A @@ S`?

- A **lazy thunk** that depends on resources acquired in scope `S`
- The thunk captures over resources that may be closed
- You can only safely evaluate it while scope `S` is open

### What is `A @@ (T & S)`?

- A thunk depending on resources from **BOTH** T and S scopes
- Arises from `flatMap` when operations touch multiple scopes' resources
- Example: socket from parent, file from child, logger from grandparent

### Scope Boundaries

Each `scoped { ... }` block is the **ONE and ONLY chance** to eliminate that scope's tag:

```scala
grandparent.scoped { parent =>
  parent.scoped { child =>
    // result: A @@ (grandparent & parent & child)
    result
  }
  // Exit child: strip child → A @@ (grandparent & parent)
}
// Exit parent: strip parent → A @@ grandparent
// Exit grandparent: strip grandparent → raw A
```

## The Unsoundness Problem

### Why We Can't Strip All Subtypes

Initial idea: strip any tag `Ti` where `Ti <: S` (proper subtype of parent tag).

**This is WRONG because:**

1. Sibling-scoped values: `sibling.ScopeTag <: parent.ScopeTag`
2. Grandchild-scoped values: `grandchild.ScopeTag <: parent.ScopeTag`

Both are subtypes of parent, but:
- Sibling scope is **already closed** before current child was created
- Grandchild scope is **already closed** when child's nested `scoped` exited

If we strip these tags, the thunk escapes but still captures **dead resources**. Forcing it later = undefined behavior.

### The Key Insight

We must strip **exactly the current closing scope's tag** - nothing more, nothing less.

The challenge: at compile time, how do we identify which tag is "the current child" vs "some other descendant/sibling"?

## Proposed Solution: Type Tree Inspection

Path-dependent types like `parent.ScopeTag` may be easily detectable in the macro:

- Parent scopes have paths like `parent.ScopeTag`, `grandparent.ScopeTag`
- The current child scope has a path like `child.ScopeTag` where `child` is the lambda parameter

### Exploratory Approach

Build a simple macro that:
1. Prints the type tree structure
2. Shows how different scope tags appear (path-dependent types, etc.)
3. For now, unsafely strips all tags (to get something working)

Once we understand the type structure, we can implement proper tag detection.

## Alternative Design Considered: Original + Intersection

We explored tracking two tags separately:

```scala
trait Scoped[+A, Original, -Intersection]
```

- `Original` = exact scope where value was CREATED (immutable provenance)
- `Intersection` = accumulated tags from operations

At boundary, check `ChildTag <:< Original`:
- If yes: value born in scope containing closing scope → safe to strip
- If no: value from dead/unrelated scope → pass through unchanged

This is more complex but provides stronger guarantees. May revisit if simple approach fails.

## Implementation Notes

### `scoped` Must Be Inline/Macro

The `scoped` method needs access to the exact child scope tag at compile time:

```scala
inline def scoped[A](f: Scope[self.ScopeTag, ? <: self.ScopeTag] => A): ??? = ${
  scopedImpl[A, self.ScopeTag]('f)
}
```

### Lift Before Close

The lift/promotion happens BEFORE the scope closes (already fixed in `ScopeVersionSpecific.scala`):

```scala
try {
  val result = f(childScope)
  out = lift(result)  // BEFORE close - thunk evaluated while scope open
} finally {
  childScope.close()
}
```

### When All Tags Are Stripped

If after stripping the child tag, NO tags remain:
- Safe to force the thunk and return raw value
- This is the common case (child scope using only child resources)

## Files Involved

- `scope/shared/src/main/scala-3/zio/blocks/scope/ScopeLift.scala`
- `scope/shared/src/main/scala-2/zio/blocks/scope/ScopeLift.scala`
- `scope/shared/src/main/scala-3/zio/blocks/scope/ScopeVersionSpecific.scala`
- `scope/shared/src/main/scala-2/zio/blocks/scope/ScopeVersionSpecific.scala`
- `scope/shared/src/main/scala-3/zio/blocks/scope/Scoped.scala`
- `scope/shared/src/test/scala-3/zio/blocks/scope/ScopeCompileTimeSafetyScala3Spec.scala`
- `scope-examples/src/main/scala/scope/examples/TransactionBoundaryExample.scala`

## Type Tree Structure (from exploratory macro)

### Key Finding

Scope tags appear as path-dependent types with **distinct `TermRef` qualifiers**:

```
parent.ScopeTag → TypeRef(name=ScopeTag)
                    qualifier: TermRef(name=parent)
                      qualifier: NoPrefix

child.ScopeTag → TypeRef(name=ScopeTag) 
                   qualifier: TermRef(name=child)
                     qualifier: NoPrefix

Scope.GlobalTag → TypeRef(name=GlobalTag)
                    qualifier: TermRef(name=Scope)
                      qualifier: ThisType(...)
```

### Detection Strategy

The lambda parameter name (`parent`, `child`, `scope`) is preserved in the type tree as `TermRef.name`.

**Key insight:** We don't need to extract the lambda parameter name from the AST - we can extract it from the S type parameter itself!

When `ScopeLift[A, S]` is resolved:
- S is the parent scope's tag (e.g., `parent.ScopeTag`)
- S's `TermRef.name` is `"parent"`
- A might contain `child.ScopeTag` with `TermRef.name = "child"`

To detect if a tag in A should be stripped:
1. Extract `TermRef.name` from S (e.g., "parent")
2. For each tag T in A's intersection:
   - If T's `TermRef.name` ≠ S's `TermRef.name` AND `T <:< S` → it's a child tag, strip it
   - If T's `TermRef.name` == S's `TermRef.name` → it's the parent tag, keep it
   - If `!(T <:< S)` → it's an ancestor tag, keep it

### For Intersection Types

`A @@ (parent.ScopeTag & child.ScopeTag)` appears as:
```
AppliedType
  tycon: Scoped.Scoped
  args:
    [0]: A's type
    [1]: AndType(&)
           left: TypeRef(name=ScopeTag) / qualifier: TermRef(name=parent)
           right: TypeRef(name=ScopeTag) / qualifier: TermRef(name=child)
```

The macro can:
1. Flatten the `AndType` into a list of component types
2. Filter out the one matching the child's parameter name
3. Reconstruct the intersection (or use parent tag if empty)

## Current Implementation Status

### What Works

1. **Type tree inspection**: We can extract `TermRef.name` from scope tags
2. **Tag detection**: `child.ScopeTag` has `TermRef.name = "child"`, `parent.ScopeTag` has `TermRef.name = "parent"`
3. **Intersection handling**: We can flatten `A @@ (child & parent)` and filter tags by name

### The Detection Rule

Strip a tag T from A if:
- We can extract `TermRef.name` from both T and S
- T's `TermRef.name` ≠ S's `TermRef.name`

This correctly handles:
- `A @@ child.ScopeTag` with `S = parent.ScopeTag` → strip child tag, force thunk
- `A @@ (child & parent)` with `S = parent.ScopeTag` → strip child, keep parent
- `A @@ parent.ScopeTag` with `S = parent.ScopeTag` → keep parent tag

### Known Limitation

Subtype checking (`T <:< S`) doesn't work across path-dependent types from different scope instances at macro expansion time. We rely solely on `TermRef.name` matching.

This means if somehow a sibling-scoped value (with `sibling.ScopeTag`) reaches the `child→parent` boundary, it would be incorrectly stripped. However, this shouldn't happen if each boundary correctly handles its own child tags.

### scopedMacro Implementation

Added `scopedMacro` method in `ScopeVersionSpecific.scala` that uses macro-based lifting:
- No ScopeLift typeclass needed
- Returns `Any` (type information lost, but value is correct)
- Works for simple cases

**Limitation**: Nested `scopedMacro` calls don't work due to existential type limitations with inline methods.

### Next Steps

1. Consider replacing `scoped` with `scopedMacro` once nested scope issues are resolved
2. Or: Add macro-based `given` for ScopeLift that integrates with existing `scoped`
3. Update existing tests to validate new behavior
4. Fix scope-examples

## PR #1053 Status

- [x] Fixed lift-before-close bug in ScopeVersionSpecific (this is a valid fix, keep it)
- [x] Build exploratory macro to understand type tree structure
- [x] Implement TermRef name-based tag detection
- [x] Implement `scopedMacro` method with macro-based lifting
- [x] **FAILED**: TermRef approach is unsound (sibling tags stripped incorrectly)
- [x] **FAILED**: Inline scoped doesn't work with nested scopes

## Cleanup Required

The following experimental files should be removed or marked as exploratory:

1. `scope/shared/src/main/scala-3/zio/blocks/scope/ScopeLiftMacros.scala` - experimental macros
2. `scope/shared/src/test/scala-3/zio/blocks/scope/ScopeLiftMacroExploreSpec.scala` - exploration tests
3. Changes to `ScopeVersionSpecific.scala` adding `scopedMacro` - doesn't work for nested scopes
4. Changes to `Scope.scala` adding `createChildScope` - only needed for scopedMacro

The fix to `ScopeVersionSpecific.scala` ensuring `lift(result)` runs BEFORE `childScope.close()` 
is valid and should be kept.

## For the Next Agent

If you're picking up this work:

1. **Don't repeat these experiments** - they don't work
2. **The core issue**: we need to know the EXACT child scope identity at compile time to strip only its tag
3. **Read the thread discussion** about the Original/Intersection design - it's the most promising path
4. **Consider runtime solutions** - compile-time may not be sufficient
5. **The lift-before-close fix is good** - don't revert it

### Technical Details That May Help

**Type tree structure** (from macro exploration):
```
child.ScopeTag appears as:
  TypeRef(name=ScopeTag)
    qualifier: TermRef(name=child)
      qualifier: NoPrefix

parent.ScopeTag appears as:
  TypeRef(name=ScopeTag)
    qualifier: TermRef(name=parent)
      qualifier: NoPrefix
```

**The Scoped opaque type**:
- Defined in `Scoped.scala` as `opaque type Scoped[+A, -S] = A | LazyScoped[A]`
- `@@` is an infix type alias for `Scoped.Scoped`
- Tag type (S) is contravariant, enabling parent-scoped values in child scopes

**Why sibling tags are dangerous**:
```scala
parent.scoped { sibling =>
  val x = allocate(...) // x: A @@ sibling.ScopeTag, captures resource
}
// sibling scope CLOSED, resource freed

parent.scoped { child =>
  // If somehow x reaches here and we strip sibling tag...
  // x becomes usable but captures a DEAD resource
  // Forcing x accesses freed memory = UB
}
```

**The user's key insight** (from discussion):
> Because the boundary of scoped {. .... } is the only way you can eliminate a tag, 
> it's important that we only eliminate the very tag introduced by the scope. 
> Otherwise, unsoundness results and leaks may happen.

**Possible direction not fully explored**:
- Pass the child scope's exact type to ScopeLift via an additional type parameter
- Make `scoped` signature include the child tag explicitly
- This requires API changes but might enable sound compile-time checking
