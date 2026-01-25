# Plan: Improve TypeId Test Coverage

**Goal**: Increase TypeId module test coverage from 55.64% to above 60% branch coverage by adding tests for under-covered APIs.

**Current Coverage**: 62.84% statement, 55.64% branch

## Under-Covered Areas

### 1. Annotation (0% coverage)
- `Annotation.name` - accessor
- `Annotation.fullName` - accessor
- Capture `@deprecated` on classes
- Capture custom annotations

### 2. TypeBounds (12.5% stmt, 0% branch)
- `hasOnlyUpper`, `hasOnlyLower`, `hasBothBounds` - predicates
- `isUnbounded`, `isAlias` - predicates  
- `upper`, `lower`, `aliasType` - accessors
- Test with bounded type parameters: `T <: Animal`, `T >: Dog`

### 3. TypeDefKind - Enum variants (0%)
- Scala 3 only: `Enum`, `EnumCase`, `EnumCaseInfo`
- Test enum with cases
- Test `isObjectCase`
- Test case arity

### 4. Extractors (0-50%)
- `Sealed` extractor - pattern match sealed traits
- `Alias` extractor - pattern match type aliases
- `Opaque` extractor - pattern match opaque types (Scala 3)
- `Nominal` extractor - pattern match regular classes

### 5. TypeRepr Utilities (0-19%)
- `TypeRepr.union` - builder
- `TypeRepr.intersection` - builder
- `TypeRepr.tuple` - builder
- `TypeRepr.function` - builder
- `containsParam` - predicate for type parameters

### 6. Owner/TermPath (52% stmt, 33% branch)
- `Owner.parent` - navigation
- `Owner.lastName`, `Owner.tpe` - accessors
- `TermPath.isEmpty`, `TermPath./` - operations

### 7. TypeParam Accessors (65% stmt, but many 0%)
- `isCovariant`, `isContravariant`, `isInvariant` - variance predicates
- `isProperType`, `isTypeConstructor` - kind predicates
- `hasUpperBound`, `hasLowerBound` - bounds predicates

### 8. Variance (28% stmt, 25% branch)
- `combine` - variance composition
- `flip` - contravariant flip

## Implementation Tasks

- [ ] Create `TypeIdAdvancedSpec.scala` test file
- [ ] Add Annotation tests (4-5 tests)
- [ ] Add TypeBounds tests (4-5 tests)
- [ ] Add Extractor tests (4 tests)
- [ ] Add TypeRepr utility tests (6 tests)
- [ ] Add Owner/TermPath tests (5 tests)
- [ ] Add TypeParam accessor tests (6 tests)
- [ ] Add Variance tests (6 tests)
- [ ] For Scala 3: Add Enum tests in `Scala3DerivationSpec.scala` (3-4 tests)
- [ ] Run tests on both Scala versions
- [ ] Verify coverage improvement

## Expected Outcome

- New test file with 35-40 tests covering under-tested APIs
- Coverage improvement: ~60-65% branch coverage (up from 55.64%)
- All tests pass on Scala 2.13.18 and 3.3.7
