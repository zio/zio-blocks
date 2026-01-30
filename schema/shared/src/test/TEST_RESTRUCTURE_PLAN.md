# Test Restructuring Plan for Into/As Specs

## Overview

This document outlines the plan to restructure the `Into` and `As` tests from the current monolithic files into a well-organized, categorized test structure.

## Current State

### Existing Test Files
- `schema/shared/src/test/scala/zio/blocks/schema/IntoSpec.scala` (856 lines)
- `schema/shared/src/test/scala/zio/blocks/schema/AsSpec.scala` (449 lines)
- `schema/shared/src/test/scala-3/zio/blocks/schema/IntoScala3Spec.scala` (39 lines)
- `schema/shared/src/test/scala-3/zio/blocks/schema/IntoOpaqueTypeSpec.scala` (252 lines)
- `schema/shared/src/test/scala-2/zio/blocks/schema/IntoZIOPreludeNewtypeSpec.scala` (203 lines)
- `schema/shared/src/test/scala-3/zio/blocks/schema/IntoZIOPreludeNewtypeSpec.scala` (199 lines)

### Tests Already Covered in Existing Files

#### IntoSpec.scala covers:
- Product to Product (exact match, unique type match, position match, combined)
- Case Class to Case Class
- Case Class to Tuple
- Tuple to Case Class
- Tuple to Tuple
- Coproduct to Coproduct (sealed trait by name, by signature, with payloads)
- Primitive Type Coercions (numeric widening/narrowing)
- Collection Element Coercion
- Map Key/Value Coercion
- Option Type Coercion
- Either Type Coercion
- Collection Type Conversions (List↔Vector, Set conversions, combined, nested)
- Collections with Product Types
- Collections with Sum Types
- Schema Evolution Patterns (adding/removing optional fields, reordering, renaming, type refinement)
- Nested Conversions (nested products, coproducts, collections)
- Error Accumulation
- Implicit Into Discovery
- Structural Types

#### AsSpec.scala covers:
- Basic Case Class Conversion
- Case Class ↔ Tuple
- Numeric Coercion (Int ↔ Long)
- Collection Type Conversions (List ↔ Vector)
- Optional Field Handling
- Nested Products
- Coproduct (Sealed Trait) Conversion
- Coproduct with Payloads
- Lossy Conversions (Set ↔ List)
- Tuple to Tuple
- Arity Mismatch
- Compilation Failure Tests (Default Values)
- Structural Types

---

## Target Structure

```
schema/shared/src/test/scala/zio/blocks/schema/
└── conversion/
    ├── into/
    │   ├── products/
    │   │   ├── CaseClassToCaseClassSpec.scala
    │   │   ├── CaseClassToTupleSpec.scala
    │   │   ├── TupleToCaseClassSpec.scala
    │   │   ├── TupleToTupleSpec.scala
    │   │   ├── FieldReorderingSpec.scala
    │   │   ├── FieldRenamingSpec.scala
    │   │   └── NestedProductsSpec.scala
    │   ├── coproducts/
    │   │   ├── SealedTraitToSealedTraitSpec.scala
    │   │   ├── CaseMatchingSpec.scala
    │   │   ├── SignatureMatchingSpec.scala
    │   │   ├── AmbiguousCaseSpec.scala
    │   │   └── NestedCoproductsSpec.scala
    │   ├── primitives/
    │   │   ├── NumericWideningSpec.scala
    │   │   ├── NumericNarrowingSpec.scala
    │   │   ├── CollectionCoercionSpec.scala
    │   │   ├── OptionCoercionSpec.scala
    │   │   ├── EitherCoercionSpec.scala
    │   │   └── NestedCollectionSpec.scala
    │   ├── collections/
    │   │   ├── ListToVectorSpec.scala
    │   │   ├── ListToSetSpec.scala
    │   │   ├── VectorToArraySpec.scala
    │   │   ├── CollectionTypeWithCoercionSpec.scala
    │   │   ├── NestedCollectionTypeSpec.scala
    │   │   └── SetDuplicateHandlingSpec.scala
    │   ├── structural/
    │   │   ├── StructuralTypeTargetSpec.scala
    │   │   └── StructuralTypeSourceSpec.scala
    │   ├── evolution/
    │   │   ├── AddOptionalFieldSpec.scala
    │   │   ├── RemoveOptionalFieldSpec.scala
    │   │   └── TypeRefinementSpec.scala
    │   ├── disambiguation/
    │   │   ├── UniqueTypeDisambiguationSpec.scala
    │   │   ├── NameDisambiguationSpec.scala
    │   │   ├── PositionDisambiguationSpec.scala
    │   │   └── AmbiguousCompileErrorSpec.scala
    │   └── edge/
    │       ├── EmptyProductSpec.scala
    │       ├── SingleFieldSpec.scala
    │       ├── CaseObjectSpec.scala
    │       ├── DeepNestingSpec.scala
    │       ├── LargeProductSpec.scala
    │       ├── LargeCoproductSpec.scala
    │       ├── RecursiveTypeSpec.scala
    │       └── MutuallyRecursiveTypeSpec.scala
    │
    └── as/
        ├── reversibility/
        │   ├── RoundTripProductSpec.scala
        │   ├── RoundTripCoproductSpec.scala
        │   ├── RoundTripTupleSpec.scala
        │   ├── RoundTripCollectionTypeSpec.scala
        │   ├── NumericNarrowingRoundTripSpec.scala
        │   └── OptionalFieldRoundTripSpec.scala
        ├── validation/
        │   ├── OverflowDetectionSpec.scala
        │   ├── NarrowingFailureSpec.scala
        │   └── CollectionLossyConversionSpec.scala
        ├── compile_errors/
        │   └── DefaultValueSpec.scala
        └── structural/
            └── StructuralTypeRoundTripSpec.scala

schema/shared/src/test/scala-3/zio/blocks/schema/conversion/
├── into/
│   ├── coproducts/
│   │   └── EnumToEnumSpec.scala
│   ├── validation/
│   │   ├── OpaqueTypeValidationSpec.scala
│   │   ├── ValidationErrorAccumulationSpec.scala
│   │   ├── NestedValidationSpec.scala
│   │   └── NarrowingValidationSpec.scala
│   └── evolution/
│       └── AddDefaultFieldSpec.scala
└── as/
    └── reversibility/
        └── OpaqueTypeRoundTripSpec.scala

schema/shared/src/test/scala-2/zio/blocks/schema/conversion/
├── into/
│   └── validation/
│       └── ZIONewtypeValidationSpec.scala
└── as/
    └── reversibility/
        └── ZIONewtypeRoundTripSpec.scala
```

---

## Implementation Order (Priority)

Each phase adds a set of test files. After each phase, we'll ensure the tests compile and pass.

### Phase 1: Products (Core functionality) ✅ Priority: HIGH
| # | File | Description | Source |
|---|------|-------------|--------|
| 1.1 | `into/products/CaseClassToCaseClassSpec.scala` | Case class to case class conversions | IntoSpec: "Product to Product", "Case Class to Case Class" |
| 1.2 | `into/products/CaseClassToTupleSpec.scala` | Case class to tuple conversions | IntoSpec: "Case Class to Tuple" |
| 1.3 | `into/products/TupleToCaseClassSpec.scala` | Tuple to case class conversions | IntoSpec: "Tuple to Case Class" |
| 1.4 | `into/products/TupleToTupleSpec.scala` | Tuple to tuple conversions | IntoSpec: "Tuple to Tuple" |
| 1.5 | `into/products/FieldReorderingSpec.scala` | Field ordering tests | IntoSpec: "Priority 1: Exact match" |
| 1.6 | `into/products/FieldRenamingSpec.scala` | Field rename with unique types | IntoSpec: "Field Renaming" |
| 1.7 | `into/products/NestedProductsSpec.scala` | Nested case class conversions | IntoSpec: "Nested Products" |

### Phase 2: Coproducts ✅ Priority: HIGH
| # | File | Description | Source |
|---|------|-------------|--------|
| 2.1 | `into/coproducts/SealedTraitToSealedTraitSpec.scala` | Sealed trait conversions | IntoSpec: "Coproduct to Coproduct" |
| 2.2 | `into/coproducts/CaseMatchingSpec.scala` | Matching by case name | IntoSpec: "by name" tests |
| 2.3 | `into/coproducts/SignatureMatchingSpec.scala` | Matching by constructor signature | IntoSpec: "by signature" tests |
| 2.4 | `into/coproducts/AmbiguousCaseSpec.scala` | Ambiguous case errors | NEW |
| 2.5 | `into/coproducts/NestedCoproductsSpec.scala` | Nested sealed trait conversions | IntoSpec: "Nested Coproducts" |
| 2.6 | `into/coproducts/EnumToEnumSpec.scala` (Scala 3) | Scala 3 enum conversions | IntoScala3Spec |

### Phase 3: Primitives & Coercion ✅ Priority: HIGH
| # | File | Description | Source |
|---|------|-------------|--------|
| 3.1 | `into/primitives/NumericWideningSpec.scala` | Byte→Short→Int→Long→Float→Double | IntoSpec: "Numeric Widening" |
| 3.2 | `into/primitives/NumericNarrowingSpec.scala` | Reverse narrowing with validation | IntoSpec: "Numeric Narrowing" |
| 3.3 | `into/primitives/CollectionCoercionSpec.scala` | List[Int]→List[Long] etc. | IntoSpec: "Collection Element Coercion" |
| 3.4 | `into/primitives/OptionCoercionSpec.scala` | Option[Int]→Option[Long] | IntoSpec: "Option Type Coercion" |
| 3.5 | `into/primitives/EitherCoercionSpec.scala` | Either coercion | IntoSpec: "Either Type Coercion" |
| 3.6 | `into/primitives/NestedCollectionSpec.scala` | Nested collection element coercion | IntoSpec: nested collection tests |

### Phase 4: Collection Type Conversions ✅ Priority: HIGH
| # | File | Description | Source |
|---|------|-------------|--------|
| 4.1 | `into/collections/ListToVectorSpec.scala` | List↔Vector | IntoSpec: "Between Standard Collection Types" |
| 4.2 | `into/collections/ListToSetSpec.scala` | List→Set (duplicate handling) | IntoSpec: "Set Conversions" |
| 4.3 | `into/collections/VectorToArraySpec.scala` | Vector↔Array | IntoSpec: array tests |
| 4.4 | `into/collections/CollectionTypeWithCoercionSpec.scala` | Combined type+element coercion | IntoSpec: "Combined Element and Collection Type" |
| 4.5 | `into/collections/NestedCollectionTypeSpec.scala` | Nested collection type changes | IntoSpec: "Nested Collection Type Conversions" |
| 4.6 | `into/collections/SetDuplicateHandlingSpec.scala` | Set duplicate semantics | IntoSpec: Set tests |

### Phase 5: Structural Types ✅ Priority: MEDIUM
| # | File | Description | Source |
|---|------|-------------|--------|
| 5.1 | `into/structural/StructuralTypeTargetSpec.scala` | Case class → structural | IntoSpec: "Product to Structural Type" |
| 5.2 | `into/structural/StructuralTypeSourceSpec.scala` | Structural → case class | IntoSpec: "Structural Type to Product" |

### Phase 6: Schema Evolution ✅ Priority: MEDIUM
| # | File | Description | Source |
|---|------|-------------|--------|
| 6.1 | `into/evolution/AddOptionalFieldSpec.scala` | Adding Option fields | IntoSpec: "Adding Optional Fields" |
| 6.2 | `into/evolution/RemoveOptionalFieldSpec.scala` | Removing Option fields | IntoSpec: "Removing Optional Fields" |
| 6.3 | `into/evolution/TypeRefinementSpec.scala` | Int→Long widening | IntoSpec: "Type Refinement" |
| 6.4 | `into/evolution/AddDefaultFieldSpec.scala` (Scala 3) | Default value handling | NEW |

### Phase 7: Disambiguation ✅ Priority: MEDIUM
| # | File | Description | Source |
|---|------|-------------|--------|
| 7.1 | `into/disambiguation/UniqueTypeDisambiguationSpec.scala` | Unique type matching | IntoSpec: "Priority 3: Unique type match" |
| 7.2 | `into/disambiguation/NameDisambiguationSpec.scala` | Name-based matching | IntoSpec: "Combined" tests |
| 7.3 | `into/disambiguation/PositionDisambiguationSpec.scala` | Position-based matching | IntoSpec: "Priority 4: Position" |
| 7.4 | `into/disambiguation/AmbiguousCompileErrorSpec.scala` | Compile-time ambiguity errors | NEW |

### Phase 8: Validation (Scala 3 Opaque Types) ✅ Priority: MEDIUM
| # | File | Description | Source |
|---|------|-------------|--------|
| 8.1 | `into/validation/OpaqueTypeValidationSpec.scala` (Scala 3) | Opaque type validation | IntoOpaqueTypeSpec |
| 8.2 | `into/validation/ValidationErrorAccumulationSpec.scala` | Error accumulation | IntoOpaqueTypeSpec |
| 8.3 | `into/validation/NestedValidationSpec.scala` | Nested validation | IntoOpaqueTypeSpec |
| 8.4 | `into/validation/NarrowingValidationSpec.scala` | Numeric narrowing validation | IntoSpec: narrowing tests |

### Phase 9: Validation (Scala 2 ZIO Prelude) ✅ Priority: MEDIUM
| # | File | Description | Source |
|---|------|-------------|--------|
| 9.1 | `into/validation/ZIONewtypeValidationSpec.scala` (Scala 2) | ZIO Prelude newtype validation | IntoZIOPreludeNewtypeSpec |

### Phase 10: Edge Cases ✅ Priority: LOW
| # | File | Description | Source |
|---|------|-------------|--------|
| 10.1 | `into/edge/EmptyProductSpec.scala` | Empty case classes | NEW |
| 10.2 | `into/edge/SingleFieldSpec.scala` | Single-field case classes | NEW |
| 10.3 | `into/edge/CaseObjectSpec.scala` | Case objects | IntoSpec: case object tests |
| 10.4 | `into/edge/DeepNestingSpec.scala` | 5+ levels of nesting | IntoSpec: nested tests |
| 10.5 | `into/edge/LargeProductSpec.scala` | 20+ field case classes | NEW |
| 10.6 | `into/edge/LargeCoproductSpec.scala` | 20+ case sealed traits | NEW |
| 10.7 | `into/edge/RecursiveTypeSpec.scala` | Recursive types (Tree) | NEW |
| 10.8 | `into/edge/MutuallyRecursiveTypeSpec.scala` | Mutually recursive types | NEW |

### Phase 11: As Reversibility Tests ✅ Priority: HIGH
| # | File | Description | Source |
|---|------|-------------|--------|
| 11.1 | `as/reversibility/RoundTripProductSpec.scala` | Product round-trips | AsSpec: all product tests |
| 11.2 | `as/reversibility/RoundTripCoproductSpec.scala` | Coproduct round-trips | AsSpec: coproduct tests |
| 11.3 | `as/reversibility/RoundTripTupleSpec.scala` | Tuple round-trips | AsSpec: tuple tests |
| 11.4 | `as/reversibility/RoundTripCollectionTypeSpec.scala` | Collection round-trips | AsSpec: collection tests |
| 11.5 | `as/reversibility/NumericNarrowingRoundTripSpec.scala` | Numeric narrowing round-trips | AsSpec: numeric tests |
| 11.6 | `as/reversibility/OptionalFieldRoundTripSpec.scala` | Optional field round-trips | AsSpec: optional field tests |
| 11.7 | `as/reversibility/OpaqueTypeRoundTripSpec.scala` (Scala 3) | Opaque type round-trips | NEW |
| 11.8 | `as/reversibility/ZIONewtypeRoundTripSpec.scala` (Scala 2) | ZIO Prelude round-trips | NEW |

### Phase 12: As Validation Tests ✅ Priority: HIGH
| # | File | Description | Source |
|---|------|-------------|--------|
| 12.1 | `as/validation/OverflowDetectionSpec.scala` | Numeric overflow detection | AsSpec: overflow tests |
| 12.2 | `as/validation/NarrowingFailureSpec.scala` | Narrowing failure cases | AsSpec: narrowing tests |
| 12.3 | `as/validation/CollectionLossyConversionSpec.scala` | Set→List lossy | AsSpec: lossy tests |

### Phase 13: As Compile Error Tests ✅ Priority: MEDIUM
| # | File | Description | Source |
|---|------|-------------|--------|
| 13.1 | `as/compile_errors/DefaultValueSpec.scala` | Default value compile errors | AsSpec: "Compilation Failure Tests" |

### Phase 14: As Structural Types ✅ Priority: LOW
| # | File | Description | Source |
|---|------|-------------|--------|
| 14.1 | `as/structural/StructuralTypeRoundTripSpec.scala` | Structural type round-trips | AsSpec: structural tests |

---

## Migration Strategy

1. **Create new directory structure** under `conversion/`
2. **For each phase:**
   - Create the new spec file with extracted/new tests
   - Run tests to ensure they pass
   - Mark the corresponding tests in old files as migrated (comment)
3. **After all phases complete:**
   - Delete the original monolithic test files
   - Update any references in build files if needed

---

## Test Template

Each test file should follow this pattern:

```scala
package zio.blocks.schema.conversion.into.products

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

object CaseClassToCaseClassSpec extends ZIOSpecDefault {

  // === Test Data Types ===
  case class Source(...)
  case class Target(...)

  // === Test Suite ===
  def spec: Spec[TestEnvironment, Any] = suite("CaseClassToCaseClassSpec")(
    suite("Basic Conversions")(
      test("...") { ... }
    ),
    suite("Edge Cases")(
      test("...") { ... }
    )
  )
}
```

---

## Notes

- **Scala 2 vs Scala 3**: Files in `scala-2/` and `scala-3/` directories are version-specific
- **Shared tests**: Files in `scala/` work for both versions
- **Error messages**: Validation error messages may differ between versions
- **NEW tests**: Tests marked as NEW need to be written from scratch
- **Keep the original files**: Until migration is complete, keep original files for reference

---

## Next Steps

1. Start with **Phase 1.1: CaseClassToCaseClassSpec.scala**
2. Confirm the test compiles and passes
3. Proceed to the next file

Ready to begin?

