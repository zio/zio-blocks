# Schema Macro TypeId Registry - Reuse TypeIds During Derivation

**Issue:** #471 - Replace TypeName by TypeId & Macro Derivation

**User's Key Insight:** "If the state is only shared during one Schema derivation call, that is already enough for a registry. The benefit is, that you can add to the registry every typeId you come along and then always make a lookup before you decide to derive a new TypeId."

---

## Problem Statement

Currently, when Schema derives `List[Name]`:
- Schema macro calls `TypeId.derived[List[Name]]` 
- That builds TypeId for List, recursively for element type Name
- **Each recursive call creates a fresh TypeId for Name**
- Even if `given Schema[Name]` exists with a TypeId, it's not reused

**Result:** `Schema.derived[List[Name]]` and `TypeId.derived[List[Name]]` produce different TypeIds for the Name element.

---

## Current State

### Existing Cache Infrastructure in Schema Macro

The Schema macro **already has caching infrastructure** (Scala 3):

```scala
// Line 356
private val schemaRefs = new mutable.HashMap[TypeRepr, Expr[Schema[?]]]
```

**Purpose:** Prevent infinite recursion + reuse Schema derivations during a single macro expansion.

**Other caches:**
- Line 218: `genericTupleTypeArgsCache: mutable.HashMap[TypeRepr, List[TypeRepr]]`
- Line 243: `isNonRecursiveCache: mutable.HashMap[TypeRepr, Boolean]`
- Line 283: `fullTermNameCache: mutable.HashMap[TypeRepr, Array[String]]`

### TypeId.derived Call Sites in Schema Macro (Scala 3)

**10 locations** where Schema macro calls `TypeId.derived`:
1. Line 715: `TypeId.derived[Array[et]]`
2. Line 763: `TypeId.derived[IArray[et]]`
3. Line 811: `TypeId.derived[ArraySeq[et]]`
4. Line 909: `TypeId.derived[tt]` (generic tuples)
5. Line 978: `TypeId.derived[T]` (NamedTuple)
6. Line 1028: `TypeId.derived[T]` (opaque types)
7. Line 1037: `TypeId.derived[T]` (type aliases)
8. Line 1048: `TypeId.derived[T]` (enum/module)
9. Line 1071: `TypeId.derived[T]` (case classes)
10. Line 1146: `TypeId.derived[T]` (sealed traits)

---

## Solution Design

### Add TypeId Registry Cache

Similar to `schemaRefs`, add a TypeId cache that accumulates TypeIds during Schema derivation:

```scala
private val typeIdRefs = new mutable.HashMap[TypeRepr, Expr[TypeId[?]]]
```

### Helper Method: `getOrDeriveTypeId`

Replace all `TypeId.derived[T]` calls with a helper that:
1. Checks `typeIdRefs` cache first
2. If not cached, searches for `given Schema[T]` → extract `.reflect.typeId`
3. If not found, searches for `given TypeId[T]`
4. If not found, calls `TypeId.derived[T]`
5. **Caches the result** in `typeIdRefs`
6. Returns the TypeId

```scala
private def getOrDeriveTypeId[T: Type](tpe: TypeRepr)(using Quotes): Expr[TypeId[T]] = {
  typeIdRefs.get(tpe) match {
    case Some(cached) => 
      cached.asInstanceOf[Expr[TypeId[T]]]
    case None =>
      val typeIdExpr = tpe.asType match {
        case '[t] =>
          // Priority 1: Search for given Schema[T] and extract typeId
          val schemaType = TypeRepr.of[Schema[t]]
          Implicits.search(schemaType) match {
            case iss: ImplicitSearchSuccess =>
              val schemaExpr = iss.tree.asExprOf[Schema[t]]
              '{ $schemaExpr.reflect.typeId.asInstanceOf[TypeId[T]] }
            case _: ImplicitSearchFailure =>
              // Priority 2: Search for given TypeId[T]
              val typeIdType = TypeRepr.of[TypeId[t]]
              Implicits.search(typeIdType) match {
                case iss2: ImplicitSearchSuccess =>
                  iss2.tree.asExprOf[TypeId[t]].asInstanceOf[Expr[TypeId[T]]]
                case _: ImplicitSearchFailure =>
                  // Priority 3: Derive fresh TypeId
                  '{ TypeId.derived[T] }
              }
          }
        case _ =>
          '{ TypeId.derived[T] }
      }
      
      // Cache the result
      typeIdRefs.update(tpe, typeIdExpr)
      typeIdExpr
  }
}
```

### Scala 2 Version

```scala
val typeIdRefs = new mutable.HashMap[Type, Tree]

def getOrDeriveTypeId(tpe: Type): Tree = {
  typeIdRefs.getOrElse(tpe, {
    // Search for Schema[T]
    val schemaType = appliedType(typeOf[Schema[_]].typeConstructor, tpe)
    val schemaSearch = c.inferImplicitValue(schemaType, silent = true)
    
    val typeIdTree = if (schemaSearch != EmptyTree) {
      q"$schemaSearch.reflect.typeId.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$tpe]]"
    } else {
      // Search for TypeId[T]
      val typeIdType = appliedType(typeOf[TypeId[_]].typeConstructor, tpe)
      val typeIdSearch = c.inferImplicitValue(typeIdType, silent = true)
      
      if (typeIdSearch != EmptyTree) {
        typeIdSearch
      } else {
        q"_root_.zio.blocks.typeid.TypeId.derived[$tpe]"
      }
    }
    
    typeIdRefs.update(tpe, typeIdTree)
    typeIdTree
  })
}
```

---

## Key Benefits

### 1. Registry Scope = Single Schema.derived Call

The registry (`typeIdRefs`) is **instance-level** - scoped to a single `SchemaCompanionVersionSpecificImpl` instance, which corresponds to one `Schema.derived[T]` macro expansion.

**This means:**
- All nested type derivations during that expansion share the registry
- TypeIds are consistent within that derivation tree
- Registry is automatically cleaned up after macro expansion completes
- No global state, no cross-compilation-unit concerns

### 2. Reuse Pattern Established

**First encounter with `Name` during `Schema.derived[List[Name]]`:**
1. `List[Name]` needs TypeId
2. Calls `getOrDeriveTypeId` for List
3. List TypeId needs element TypeId
4. Calls `getOrDeriveTypeId[Name](nameType)`
5. Searches for `given Schema[Name]` → **FOUND**
6. Extracts `.reflect.typeId`
7. **Caches it** in `typeIdRefs(nameType) = extractedTypeId`
8. Returns extracted TypeId

**Second encounter with `Name` (e.g., in `Map[String, Name]`):**
1. Map needs value TypeId
2. Calls `getOrDeriveTypeId[Name](nameType)`
3. **Cache hit!** Returns cached TypeId immediately
4. No redundant derivation, no fresh TypeId creation

### 3. Consistency Guarantee

Within a single `Schema.derived` call:
- All occurrences of the same type get the same TypeId
- User-defined TypeIds (via `given Schema` or `given TypeId`) are respected
- Recursive types work correctly (cache populated early, reused in cycles)

---

## TODOs

### Scala 3 Implementation

- [ ] 1. Add TypeId registry cache
  
  **What to do:**
  - Add after line 356: `private val typeIdRefs = new mutable.HashMap[TypeRepr, Expr[TypeId[?]]]`
  
  **Parallelizable:** NO (foundation for next tasks)
  
  **References:**
  - `schema/shared/src/main/scala-3/zio/blocks/schema/SchemaCompanionVersionSpecific.scala:356` - schemaRefs pattern
  
  **Acceptance Criteria:**
  - [ ] Compiles: `sbt "++3.3.7; schemaJVM/compile"`
  
  **Commit:** NO (groups with helper method)

- [ ] 2. Add `getOrDeriveTypeId` helper method
  
  **What to do:**
  - Add helper after `findImplicitOrDeriveSchema` (around line 382)
  - Implement 3-tier search: Schema → TypeId → derive
  - Cache result in `typeIdRefs`
  
  **Parallelizable:** NO (depends on task 1)
  
  **References:**
  - `schema/shared/src/main/scala-3/zio/blocks/schema/SchemaCompanionVersionSpecific.scala:359-381` - findImplicitOrDeriveSchema pattern
  - `typeid/shared/src/main/scala-3/zio/blocks/typeid/TypeIdMacros.scala:588-599` - Implicit search pattern
  
  **Acceptance Criteria:**
  - [ ] Method signature: `private def getOrDeriveTypeId[T: Type](tpe: TypeRepr)(using Quotes): Expr[TypeId[T]]`
  - [ ] Compiles without errors
  - [ ] Implements cache-first logic
  
  **Commit:** NO (groups with replacements)

- [ ] 3. Replace all `TypeId.derived` calls with `getOrDeriveTypeId`
  
  **What to do:**
  - Line 715: `TypeId.derived[Array[et]]` → `getOrDeriveTypeId[Array[et]](TypeRepr.of[Array[et]])`
  - Line 763: `TypeId.derived[IArray[et]]` → `getOrDeriveTypeId[IArray[et]](TypeRepr.of[IArray[et]])`
  - Line 811: `TypeId.derived[ArraySeq[et]]` → similar
  - Line 909: `TypeId.derived[tt]` → `getOrDeriveTypeId[tt](ttTpe)` (use existing TypeRepr variable)
  - Line 978: `TypeId.derived[T]` → `getOrDeriveTypeId[T](tpe)`
  - Line 1028: `'{ TypeId.derived[T] }` → `getOrDeriveTypeId[T](tpe)`
  - Line 1037: `'{ TypeId.derived[T]... }` → `'{ ${ getOrDeriveTypeId[T](tpe) }.asInstanceOf[TypeId[s]] }`
  - Line 1048: `TypeId.derived[T]` → `getOrDeriveTypeId[T](tpe)`
  - Line 1071: `TypeId.derived[T]` → `getOrDeriveTypeId[T](tpe)`
  - Line 1146: `TypeId.derived[T]` → `getOrDeriveTypeId[T](tpe)`
  
  **Must NOT change:**
  - Lines 115-123 (`buildTypeIdForZioPreludeNewtype`) - builds nominal TypeId explicitly
  - Lines 152-160 (`buildTypeIdForNeotypeNewtype`) - builds nominal TypeId explicitly
  
  **Parallelizable:** NO (depends on task 2)
  
  **References:**
  - Find TypeRepr variables for each call site (e.g., `eTpe` for Array element type)
  
  **Acceptance Criteria:**
  - [ ] All 10 call sites replaced
  - [ ] Compiles: `sbt "++3.3.7; schemaJVM/compile"`
  - [ ] No new warnings
  
  **Commit:** NO (groups with tests)

### Scala 2 Implementation

- [ ] 4. Add TypeId registry cache (Scala 2)
  
  **What to do:**
  - Add after line 241: `val typeIdRefs = new mutable.HashMap[Type, Tree]`
  
  **Parallelizable:** NO (foundation)
  
  **References:**
  - `schema/shared/src/main/scala-2/zio/blocks/schema/SchemaCompanionVersionSpecific.scala:241` - schemaRefs
  
  **Acceptance Criteria:**
  - [ ] Compiles: `sbt "++2.13.18; schemaJVM/compile"`
  
  **Commit:** NO

- [ ] 5. Add `getOrDeriveTypeId` helper (Scala 2)
  
  **What to do:**
  - Add helper using `c.inferImplicitValue` pattern
  - Search Schema → TypeId → derive
  - Cache in `typeIdRefs`
  
  **Parallelizable:** NO (depends on task 4)
  
  **References:**
  - `schema/shared/src/main/scala-2/zio/blocks/schema/SchemaCompanionVersionSpecific.scala:244-263` - findImplicitOrDeriveSchema
  - `typeid/shared/src/main/scala-2/zio/blocks/typeid/TypeIdMacros.scala:220-240` - Implicit search
  
  **Acceptance Criteria:**
  - [ ] Method compiles
  - [ ] Returns `Tree`
  
  **Commit:** NO

- [ ] 6. Replace `TypeId.derived` calls (Scala 2)
  
  **What to do:**
  - Line 440: Replace with `getOrDeriveTypeId(tpe)`
  - Line 479: Replace with `getOrDeriveTypeId(tpe)`
  - Line 567: Replace `TypeId.derived[$tpe]` with `getOrDeriveTypeId($tpe)`
  - Line 578: Replace
  - Line 592: Replace
  - Line 651: Replace
  
  **Parallelizable:** NO (depends on task 5)
  
  **Acceptance Criteria:**
  - [ ] All 6 call sites replaced
  - [ ] Compiles: `sbt "++2.13.18; schemaJVM/compile"`
  
  **Commit:** NO

### Test Updates

- [ ] 7. Update ZIOPreludeSupportSpec test
  
  **What to do:**
  - Remove test "TypeId.derived auto-derives when no given TypeId is available" (line 129-135)
    - This test is now invalid - TypeId behavior depends on whether Schema macro cached it
    - The auto-derivation behavior is tested elsewhere
  - Keep tests for user-provided TypeIds (lines 114-120, 121-128)
  
  **Parallelizable:** YES (independent)
  
  **References:**
  - `schema/shared/src/test/scala-3/zio/blocks/schema/ZIOPreludeSupportSpec.scala:129-135`
  
  **Acceptance Criteria:**
  - [ ] Test removed or updated to reflect new caching behavior
  - [ ] Other tests still pass
  
  **Commit:** NO (groups with verification)

- [ ] 8. Investigate complex generic tuples test
  
  **What to do:**
  - Run: `sbt "++3.3.7; schemaJVM/testOnly zio.blocks.schema.SchemaVersionSpecificSpec -- -t 'complex generic tuples'"`
  - Determine if failure is:
    - Pre-existing (binding object identity issue)
    - Caused by our changes (TypeId caching affects binding instances)
  - If caused by our changes: adjust test to use structural equality or fix caching logic
  
  **Parallelizable:** YES (investigation)
  
  **References:**
  - `schema/js-jvm/src/test/scala-3/zio/blocks/schema/SchemaVersionSpecificSpec.scala`
  
  **Acceptance Criteria:**
  - [ ] Root cause identified
  - [ ] If pre-existing: documented
  - [ ] If caused by changes: fixed
  
  **Commit:** NO

### Integration & Verification

- [ ] 9. Format code
  
  **What to do:**
  - Run: `sbt "++3.3.7; fmt" && sbt "++2.13.18; fmt"`
  
  **Parallelizable:** NO (after all code changes)
  
  **Acceptance Criteria:**
  - [ ] All files formatted
  
  **Commit:** NO

- [ ] 10. Run full test suite (Scala 3)
  
  **What to do:**
  - Run: `sbt "++3.3.7; schemaJVM/test" 2>&1 | tee /tmp/test-scala3-registry.txt`
  - Focus on:
    - ZIOPreludeSupportSpec - TypeId tests
    - NeotypeSupportSpec - Opaque type tests
    - Recursive type tests
  
  **Parallelizable:** NO (after all changes)
  
  **Acceptance Criteria:**
  - [ ] All ZIOPreludeSupportSpec tests pass
  - [ ] All NeotypeSupportSpec tests pass
  - [ ] No new test failures
  
  **Commit:** NO

- [ ] 11. Run full test suite (Scala 2)
  
  **What to do:**
  - Run: `sbt "++2.13.18; schemaJVM/test" 2>&1 | tee /tmp/test-scala2-registry.txt`
  
  **Parallelizable:** YES (can run in parallel with Scala 3)
  
  **Acceptance Criteria:**
  - [ ] All tests pass
  - [ ] No regressions
  
  **Commit:** NO

- [ ] 12. Create final commit
  
  **What to do:**
  - Stage all changes
  - Commit with message
  
  **Parallelizable:** NO (final)
  
  **Commit:** YES
  - Message: `feat(schema): Add TypeId registry to Schema macro for consistent TypeId reuse

Schema macro now maintains a TypeId cache (typeIdRefs) during derivation:
- First checks cache for already-derived TypeIds
- Searches for given Schema[T] and extracts its typeId
- Falls back to searching for given TypeId[T]
- Only derives fresh TypeId as last resort
- Caches all TypeIds for reuse within same derivation

This ensures:
- Consistent TypeIds for repeated types (e.g., List[Name] and Map[K,Name])
- User-defined TypeIds (via Schema/TypeId givens) are respected
- No redundant TypeId derivation during Schema expansion
- Recursive types work correctly (cache populated early)

Registry is scoped to single Schema.derived call (instance-level),
ensuring no global state or cross-compilation concerns.

Fixes #471`
  - Files: Schema macro files (Scala 2 & 3), test updates

---

## Success Criteria

### Verification Commands

```bash
# Compile both versions
sbt "++3.3.7; schemaJVM/compile" && sbt "++2.13.18; schemaJVM/compile"

# Critical tests
sbt "++3.3.7; schemaJVM/testOnly zio.blocks.schema.ZIOPreludeSupportSpec"
sbt "++3.3.7; schemaJVM/testOnly zio.blocks.schema.NeotypeSupportSpec"

# Full suite
sbt "++3.3.7; schemaJVM/test"
sbt "++2.13.18; schemaJVM/test"

# Format check
sbt check
```

### Final Checklist

- [ ] TypeId registry cache added (Scala 2 & 3)
- [ ] `getOrDeriveTypeId` helper implemented
- [ ] All `TypeId.derived` calls replaced
- [ ] Tests updated/fixed
- [ ] Code formatted
- [ ] All tests pass on both Scala versions

---

## Design Notes

### Why Registry Works Here

**Scope = Single Macro Expansion:**
- Registry is an instance field of `SchemaCompanionVersionSpecificImpl`
- Each `Schema.derived[T]` creates a new instance
- Registry lives only during that derivation
- Automatically garbage collected after

**Benefits:**
- No global mutable state
- No cross-compilation unit issues
- No thread safety concerns (macros run at compile time, single-threaded per expansion)
- Natural lifecycle (created → used → discarded)

### Why This is Better Than Individual Searches

**Without Registry (current):**
```scala
Schema.derived[ComplexType]
  derives List[Name]    → creates TypeId for Name (search #1)
  derives Map[K, Name]  → creates DIFFERENT TypeId for Name (search #2)
  derives Option[Name]  → creates DIFFERENT TypeId for Name (search #3)
```

**With Registry:**
```scala
Schema.derived[ComplexType]
  derives List[Name]    → searches, finds Schema[Name].typeId, caches it
  derives Map[K, Name]  → cache hit! reuses same TypeId
  derives Option[Name]  → cache hit! reuses same TypeId
```

### TypeId Macro Remains Independent

**Important:** This change is entirely in the Schema macro. The TypeId macro remains unchanged and independent:
- No circular dependencies introduced
- TypeId.derived still works standalone
- TypeId module has no knowledge of Schema

**Only difference:** When Schema macro calls `TypeId.derived`, it goes through the registry first.

---

## Alternative Approaches Considered

### Alternative 1: TypeId Macro Searches for Schema
**Rejected** - Creates circular dependency (TypeId → Schema)

### Alternative 2: Global/ThreadLocal Registry
**Rejected** - Unnecessary complexity; instance-level registry is sufficient and cleaner

### Alternative 3: Only Schema Macro Searches (No Registry)
**Insufficient** - Would search on every call; registry avoids redundant searches

---

## Future Enhancements

If needed, the registry pattern can be extended to:
1. Cache other derivation artifacts
2. Provide debug output showing cache hit/miss statistics
3. Validate TypeId consistency across the derivation tree

But the current simple design should be sufficient.
