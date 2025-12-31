# Structural Schema Implementation Plan

## Overview

This document tracks the implementation status of structural type schema support (Issue #517).

---

## Test Status Summary

| Category | Total Tests | ✅ Passing | ⏸️ Ignored | Status |
|----------|-------------|-----------|-----------|--------|
| **common/** | | | | |
| SimpleProductSpec | 12 | 12 | 0 | ✅ Complete |
| NestedProductSpec | 11 | 11 | 0 | ✅ Complete |
| CollectionsSpec | 17 | 17 | 0 | ✅ Complete |
| TuplesSpec | 12 | 12 | 0 | ✅ Complete |
| EmptyProductSpec | 8 | 8 | 0 | ✅ Complete |
| SingleFieldSpec | 5 | 5 | 0 | ✅ Complete |
| LargeProductSpec | 10 | 10 | 0 | ✅ Complete |
| TypeNameNormalizationSpec | 7 | 7 | 0 | ✅ Complete |
| IntoIntegrationSpec | 4 | 0 | 4 | 🔴 Not Started |
| AsIntegrationSpec | 3 | 0 | 3 | 🔴 Not Started |
| **scala3/** | | | | |
| UnionTypesSpec | 3 | 0 | 3 | 🔴 Not Started |
| SealedTraitToUnionSpec | 5 | 0 | 5 | 🔴 Not Started |
| EnumToUnionSpec | 4 | 0 | 4 | 🔴 Not Started |
| SelectableImplementationSpec | 6 | 0 | 6 | 🔴 Not Started |
| **scala2/** | | | | |
| DynamicImplementationSpec | 5 | 0 | 5 | 🔴 Not Started |
| SumTypeErrorSpec | 3 | 0 | 3 | 🔴 Not Started |
| **errors/** | | | | |
| RecursiveTypeErrorSpec | 3 | 0 | 3 | 🔴 Not Started |
| MutualRecursionErrorSpec | 2 | 0 | 2 | 🔴 Not Started |
| UnsupportedTypeErrorSpec | 4 | 0 | 4 | 🔴 Not Started |
| **TOTAL** | **124** | **81** | **43** | **65%** |

---

## Phase Status

### Phase 1: Core Product Types ✅ COMPLETE
1. ~~Simple product types (case classes)~~ ✅
2. ~~Empty products (empty case class, case object)~~ ✅
3. ~~Single-field products~~ ✅
4. ~~Type name normalization~~ ✅
5. ~~Basic tuples (2-3 elements)~~ ✅

### Phase 2: Extended Product Support ✅ COMPLETE
6. ~~Nested product types~~ ✅
7. ~~Collections in structural types~~ ✅ (List, Vector, Set, Map, Option, Either)
8. ~~Large tuples (4+ elements, up to 10)~~ ✅
9. ~~Large products (25+ fields)~~ ✅

### Phase 3: Error Handling 🔜
10. Recursive type detection & error
11. Mutual recursion detection & error
12. Unsupported type errors

### Phase 4: Sum Types (Scala 3) 🔜
13. Sealed trait to union type
14. Enum to union type
15. Union type name normalization

### Phase 5: Scala 2 Parity 🔜
16. Dynamic-based implementation
17. Sum type error in Scala 2

### Phase 6: Into/As Integration 🔜
18. Into[Nominal, Structural]
19. Into[Structural, Nominal]
20. As[Nominal, Structural] bidirectional

### Phase 7: Implementation Details 🔜
21. Selectable implementation details (Scala 3)
22. Dynamic implementation details (Scala 2)


