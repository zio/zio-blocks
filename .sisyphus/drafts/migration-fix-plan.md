# Draft: Migration Branch Fix Plan

## Requirements (confirmed)

### 1. Deep Type-Level Tracking in MigrationBuilder
- User wants type parameters to track which source fields are handled and which target fields are provided
- Need more complex examples with nested and recursive types
- Pattern sources: Schema (Reflect, Binding, Term), Into/As derivation macros

### 2. SchemaExpr Split
- Create `DynamicSchemaExpr` - untyped, serializable expression AST
- `SchemaExpr[A, B]` becomes a typed wrapper containing Schema + DynamicSchemaExpr
- Current SchemaExpr embeds `Schema[A]` in `Optic` case class - this breaks serializability
- MigrationAction fields should store DynamicSchemaExpr

### 3. Error Message Tests
- Current tests only assert `.isLeft` without checking error content
- Need explicit error message assertions like `result.swap.exists(_.message.contains("..."))`
- Use SchemaError.MigrationErrorKind cases: PathNotFound, TypeMismatch, FieldNotFound, etc.

## Technical Decisions

### Branch Strategy
- **schema-migration-system-519** has 86 unique commits including #517 structural work and SchemaExpr bitwise work
- **schema-migration-clean** has 18 commits - squashed migration + intersection type refactors
- **Problem**: origin/schema-migration-system-519 was overwritten to point to schema-migration-clean
- **Strategy**: Use schema-migration-system-519 as base, cherry-pick intersection type improvements from clean

### Type-Level Tracking Approach
From Schema patterns:
- Use intersection types (Scala 3: `&`, Scala 2: `with` via alias) to accumulate handled/provided fields
- Use `FieldName[N <: String & Singleton]` wrapper to preserve literal types through macros
- Validate completeness at `build` time via `MigrationComplete[A, B, SourceHandled, TargetProvided]` evidence

From Into patterns:
- Into does NOT use type-level tracking - it's macro-time only with mutable sets
- Field matching is target-driven with 4-level priority
- Error accumulation via `Either[SchemaError, _]`

**Decision**: Keep the intersection type approach from schema-migration-clean, but restore the stronger `<: Tuple` constraint from schema-migration-system-519 for Scala 3.

### SchemaExpr Split Design
Current problematic types:
- `SchemaExpr.Optic(path: DynamicOptic, sourceSchema: Schema[A])` - stores Schema, not serializable

Proposed structure:
```scala
// Serializable, no type params
sealed trait DynamicSchemaExpr {
  def eval(input: DynamicValue): Either[SchemaError, DynamicValue]
}
object DynamicSchemaExpr {
  case class Literal(value: DynamicValue) extends DynamicSchemaExpr
  case class Select(path: DynamicOptic) extends DynamicSchemaExpr
  case class Relational(left: DynamicSchemaExpr, right: DynamicSchemaExpr, op: RelationalOperator)
  case class Logical(left: DynamicSchemaExpr, right: DynamicSchemaExpr, op: LogicalOperator)
  case class Arithmetic(left: DynamicSchemaExpr, right: DynamicSchemaExpr, op: ArithmeticOperator, kind: NumericKind)
  // ... string ops, bitwise ops, etc.
}

// Typed wrapper
final case class SchemaExpr[A, B](
  dynamic: DynamicSchemaExpr,
  inputSchema: Schema[A],
  outputSchema: Schema[B]
) {
  def eval(input: A): Either[SchemaError, B] = ...
}
```

### Error Testing Strategy
- Pattern A: Exact equality `assertTrue(err.message == "...")`
- Pattern B: Contains check `assertTrue(result.swap.exists(_.message.contains("...")))`
- Pattern C: Structured check on SchemaError.Single types

Use Pattern B for flexibility, Pattern C for type-safe assertions.

## Research Findings

### Nested Types in Codebase
- 2-4 level nesting: `Level1(Level2(Level3(Level4)))` in ProductToStructuralSpec
- Generic nesting: `Parent(child: Child[MySealedTrait])` in SchemaSpec
- Stress tests: `DeepNode(child: Option[DeepNode])` in DeepNestingStressSpec

### Recursive Types in Codebase
- Self-recursive: `Tree(value: Int, children: List[Tree])`
- Mutually recursive: `Node(edges: List[Edge])`, `Edge(to: Node)`
- Handled via `Reflect.Deferred` with cycle guards

### ZIO Schema Comparison
- ZIO Schema uses bidirectional isomorphisms for transforms
- Migration is ADT-based: AddNode, DeleteNode, ChangeType, etc.
- Automatic migration derivation via MetaSchema diffing
- DynamicValue as intermediate representation

## Scope Boundaries
- INCLUDE: Branch reconciliation, type tracking, SchemaExpr split, error tests
- EXCLUDE: ZIO Schema migration derivation (future work), structural type conversions (#517)

## Open Questions
- [RESOLVED] Should we start from clean or system-519? → system-519, cherry-pick clean improvements
- [RESOLVED] Type tracking: Tuple-based or Any-based? → Tuple-based for Scala 3, intersection types for both
