# DynamicSchemaExpr Design Document

## Problem Statement

Current `SchemaExpr[A, B]` embeds `Schema[A]` in the `Optic` case class:
```scala
final case class Optic[A, B](path: DynamicOptic, sourceSchema: Schema[A]) extends SchemaExpr[A, B]
```

This makes migrations non-serializable because `Schema` contains:
- `Binding` with `Constructor`/`Deconstructor` (functions)
- Runtime type information that can't be serialized

Additionally, `Arithmetic` embeds `NumericPrimitiveType[A]` which contains `Numeric[A]` (a type class instance).

## Goal

Split `SchemaExpr` into:
1. **`DynamicSchemaExpr`** - Fully serializable, no type parameters, operates on `DynamicValue`
2. **`SchemaExpr[A, B]`** - Typed wrapper that adds compile-time type safety

## Analysis of Current SchemaExpr Case Classes

### Already Serializable (no changes needed for dynamic version)
| Case Class | Fields | Notes |
|------------|--------|-------|
| `Literal[S, A]` | `dynamicValue: DynamicValue` | Pure data |
| `PrimitiveConversion[S]` | `conversionType: ConversionType` | Sealed trait of case objects |

### Blockers (need redesign)
| Case Class | Blocking Field | Solution |
|------------|----------------|----------|
| `Optic[A, B]` | `sourceSchema: Schema[A]` | Replace with just `path: DynamicOptic` |
| `Arithmetic[S, A]` | `numericType: NumericPrimitiveType[A]` | Replace with `NumericTypeTag` enum |

### Recursive (depend on sub-expressions)
All other case classes are recursive and will work once their sub-expressions are `DynamicSchemaExpr`.

## DynamicSchemaExpr Design

```scala
/**
 * A fully serializable expression that operates on DynamicValue.
 * No type parameters, no Schema references, no function closures.
 */
sealed trait DynamicSchemaExpr {
  /**
   * Evaluate this expression on a DynamicValue input.
   */
  def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]]
}

object DynamicSchemaExpr {

  // ==================== Leaf Expressions ====================

  /**
   * A literal value - always returns the same DynamicValue.
   */
  final case class Literal(value: DynamicValue) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      Right(Seq(value))
  }

  /**
   * Select a value at a path from the input.
   * Replaces Optic[A, B] - no Schema needed, just the path.
   */
  final case class Select(path: DynamicOptic) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      walkPath(input, path)
    
    // Implementation similar to current Optic.walkPath but on DynamicValue directly
  }

  /**
   * Convert a primitive value from one type to another.
   */
  final case class PrimitiveConversion(conversionType: ConversionType) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      conversionType.convert(input).map(Seq(_)).left.map(SchemaError.fromString)
  }

  // ==================== Relational Operators ====================

  final case class Relational(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: RelationalOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield for { x <- xs; y <- ys } yield operator.apply(x, y)
  }

  sealed trait RelationalOperator {
    def apply(x: DynamicValue, y: DynamicValue): DynamicValue
  }
  object RelationalOperator {
    case object LessThan extends RelationalOperator { ... }
    case object LessThanOrEqual extends RelationalOperator { ... }
    case object GreaterThan extends RelationalOperator { ... }
    case object GreaterThanOrEqual extends RelationalOperator { ... }
    case object Equal extends RelationalOperator { ... }
    case object NotEqual extends RelationalOperator { ... }
  }

  // ==================== Logical Operators ====================

  final case class Logical(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: LogicalOperator
  ) extends DynamicSchemaExpr

  sealed trait LogicalOperator
  object LogicalOperator {
    case object And extends LogicalOperator
    case object Or extends LogicalOperator
  }

  final case class Not(expr: DynamicSchemaExpr) extends DynamicSchemaExpr

  // ==================== Arithmetic Operators ====================

  /**
   * Serializable tag for numeric types.
   * Replaces NumericPrimitiveType[A] which contains Numeric[A].
   */
  sealed trait NumericTypeTag {
    def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
    def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
    def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
    def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
    def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
    def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
  }

  object NumericTypeTag {
    case object ByteTag extends NumericTypeTag { ... }
    case object ShortTag extends NumericTypeTag { ... }
    case object IntTag extends NumericTypeTag { ... }
    case object LongTag extends NumericTypeTag { ... }
    case object FloatTag extends NumericTypeTag { ... }
    case object DoubleTag extends NumericTypeTag { ... }
    case object BigIntTag extends NumericTypeTag { ... }
    case object BigDecimalTag extends NumericTypeTag { ... }
  }

  final case class Arithmetic(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: ArithmeticOperator,
    numericType: NumericTypeTag  // Serializable tag instead of NumericPrimitiveType
  ) extends DynamicSchemaExpr

  sealed trait ArithmeticOperator
  object ArithmeticOperator {
    case object Add extends ArithmeticOperator
    case object Subtract extends ArithmeticOperator
    case object Multiply extends ArithmeticOperator
    case object Divide extends ArithmeticOperator
    case object Pow extends ArithmeticOperator
    case object Modulo extends ArithmeticOperator
  }

  // ==================== Bitwise Operators ====================

  final case class Bitwise(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: BitwiseOperator
  ) extends DynamicSchemaExpr

  sealed trait BitwiseOperator
  object BitwiseOperator {
    case object And extends BitwiseOperator
    case object Or extends BitwiseOperator
    case object Xor extends BitwiseOperator
    case object LeftShift extends BitwiseOperator
    case object RightShift extends BitwiseOperator
    case object UnsignedRightShift extends BitwiseOperator
  }

  final case class BitwiseNot(expr: DynamicSchemaExpr) extends DynamicSchemaExpr

  // ==================== String Operations ====================

  final case class StringConcat(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr
  ) extends DynamicSchemaExpr

  final case class StringRegexMatch(
    regex: DynamicSchemaExpr,
    string: DynamicSchemaExpr
  ) extends DynamicSchemaExpr

  final case class StringLength(string: DynamicSchemaExpr) extends DynamicSchemaExpr

  final case class StringSubstring(
    string: DynamicSchemaExpr,
    start: DynamicSchemaExpr,
    end: DynamicSchemaExpr
  ) extends DynamicSchemaExpr

  final case class StringTrim(string: DynamicSchemaExpr) extends DynamicSchemaExpr

  final case class StringToUpperCase(string: DynamicSchemaExpr) extends DynamicSchemaExpr

  final case class StringToLowerCase(string: DynamicSchemaExpr) extends DynamicSchemaExpr

  final case class StringReplace(
    string: DynamicSchemaExpr,
    target: DynamicSchemaExpr,
    replacement: DynamicSchemaExpr
  ) extends DynamicSchemaExpr

  final case class StringStartsWith(
    string: DynamicSchemaExpr,
    prefix: DynamicSchemaExpr
  ) extends DynamicSchemaExpr

  final case class StringEndsWith(
    string: DynamicSchemaExpr,
    suffix: DynamicSchemaExpr
  ) extends DynamicSchemaExpr

  final case class StringContains(
    string: DynamicSchemaExpr,
    substring: DynamicSchemaExpr
  ) extends DynamicSchemaExpr

  final case class StringIndexOf(
    string: DynamicSchemaExpr,
    substring: DynamicSchemaExpr
  ) extends DynamicSchemaExpr
}
```

## Typed SchemaExpr Wrapper Design

```scala
/**
 * A typed wrapper around DynamicSchemaExpr that provides compile-time type safety.
 * 
 * @tparam A The input type
 * @tparam B The output type
 * @param dynamic The underlying serializable expression
 * @param inputSchema Schema for converting A to DynamicValue
 * @param outputSchema Schema for converting DynamicValue back to B
 */
final case class SchemaExpr[A, B](
  dynamic: DynamicSchemaExpr,
  inputSchema: Schema[A],
  outputSchema: Schema[B]
) {
  /**
   * Evaluate the expression on a typed input.
   */
  def eval(input: A): Either[OpticCheck, Seq[B]] = {
    val dynamicInput = inputSchema.toDynamicValue(input)
    dynamic.eval(dynamicInput) match {
      case Right(results) =>
        val converted = results.map(outputSchema.fromDynamicValue)
        val errors = converted.collect { case Left(e) => e }
        if (errors.nonEmpty) {
          Left(new OpticCheck(errors.map(e => OpticCheck.DynamicConversionError(e.message)).toList))
        } else {
          Right(converted.collect { case Right(v) => v })
        }
      case Left(error) =>
        Left(new OpticCheck(List(OpticCheck.DynamicConversionError(error.message))))
    }
  }

  /**
   * Evaluate the expression and return DynamicValue results.
   */
  def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] = {
    val dynamicInput = inputSchema.toDynamicValue(input)
    dynamic.eval(dynamicInput).left.map(e => 
      new OpticCheck(List(OpticCheck.DynamicConversionError(e.message)))
    )
  }

  // Combinators delegate to dynamic and wrap result
  def &&[B2](that: SchemaExpr[A, B2])(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.Logical(this.dynamic, that.dynamic, DynamicSchemaExpr.LogicalOperator.And),
      inputSchema,
      Schema[Boolean]
    )

  def ||[B2](that: SchemaExpr[A, B2])(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.Logical(this.dynamic, that.dynamic, DynamicSchemaExpr.LogicalOperator.Or),
      inputSchema,
      Schema[Boolean]
    )
}

object SchemaExpr {
  /**
   * Create a literal expression.
   */
  def literal[A](value: A)(implicit schema: Schema[A]): SchemaExpr[Any, A] =
    SchemaExpr(
      DynamicSchemaExpr.Literal(schema.toDynamicValue(value)),
      Schema[Any], // Any input is ignored
      schema
    )

  /**
   * Create an optic expression from a path.
   */
  def optic[A, B](path: DynamicOptic)(implicit inputSchema: Schema[A], outputSchema: Schema[B]): SchemaExpr[A, B] =
    SchemaExpr(
      DynamicSchemaExpr.Select(path),
      inputSchema,
      outputSchema
    )

  /**
   * Get the schema's default value as an expression.
   */
  def schemaDefault[A](implicit schema: Schema[A]): Option[SchemaExpr[Any, A]] =
    schema.getDefaultValue.map { defaultValue =>
      SchemaExpr(
        DynamicSchemaExpr.Literal(schema.toDynamicValue(defaultValue)),
        Schema[Any],
        schema
      )
    }
}
```

## Evaluation Strategy

### DynamicSchemaExpr.eval

The `eval` method on `DynamicSchemaExpr` operates purely on `DynamicValue`:

1. **Input**: `DynamicValue` (no schema needed)
2. **Output**: `Either[SchemaError, Seq[DynamicValue]]`
3. **No type parameters**: Everything is dynamic

The `Select` case class implements path traversal similar to current `Optic.walkPath`:

```scala
final case class Select(path: DynamicOptic) extends DynamicSchemaExpr {
  def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] = {
    walkPath(Chunk(input), path.nodes, 0)
  }

  private def walkPath(
    current: Chunk[DynamicValue],
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int
  ): Either[SchemaError, Seq[DynamicValue]] = {
    if (idx >= nodes.length) return Right(current.toSeq)
    if (current.isEmpty) return Right(Seq.empty)

    val node = nodes(idx)
    node match {
      case DynamicOptic.Node.Field(name) =>
        val next = current.flatMap {
          case r: DynamicValue.Record => r.fields.collect { case (n, v) if n == name => v }
          case _ => Chunk.empty
        }
        walkPath(next, nodes, idx + 1)

      case DynamicOptic.Node.Case(expectedCase) =>
        val next = current.flatMap {
          case v: DynamicValue.Variant if v.caseNameValue == expectedCase => Chunk(v.value)
          case _ => Chunk.empty
        }
        walkPath(next, nodes, idx + 1)

      // ... other cases similar to current Optic.walkPath
    }
  }
}
```

### SchemaExpr.eval

The typed wrapper:
1. Converts input `A` to `DynamicValue` using `inputSchema`
2. Delegates to `dynamic.eval(dynamicInput)`
3. Converts results back to `B` using `outputSchema`

## Serialization Approach

### DynamicSchemaExpr Serialization

`DynamicSchemaExpr` is fully serializable because:
1. All case classes contain only serializable data
2. No `Schema[_]` references
3. No function closures
4. All operators are sealed traits of case objects

Serialization follows the same pattern as `DynamicOptic`:

```scala
object DynamicSchemaExpr {
  // Manual Schema derivation for Scala 2 compatibility
  implicit lazy val literalSchema: Schema[Literal] = ...
  implicit lazy val selectSchema: Schema[Select] = ...
  // ... etc for all case classes

  implicit lazy val schema: Schema[DynamicSchemaExpr] = new Schema(
    reflect = new Reflect.Variant[Binding, DynamicSchemaExpr](
      cases = Vector(
        literalSchema.reflect.asTerm("Literal"),
        selectSchema.reflect.asTerm("Select"),
        // ... all cases
      ),
      typeId = TypeId.of[DynamicSchemaExpr],
      variantBinding = ...
    )
  )
}
```

### MigrationAction Update

`MigrationAction` changes from:
```scala
final case class AddField(at: DynamicOptic, default: SchemaExpr[_, _]) extends MigrationAction
```

To:
```scala
final case class AddField(at: DynamicOptic, default: DynamicSchemaExpr) extends MigrationAction
```

This makes `MigrationAction` fully serializable.

## Migration Path

### Phase 1: Add DynamicSchemaExpr
1. Create `DynamicSchemaExpr.scala` with all case classes
2. Implement `eval` for each case class
3. Add Schema derivation for serialization

### Phase 2: Refactor SchemaExpr
1. Change `SchemaExpr` to wrap `DynamicSchemaExpr`
2. Update all combinators to work with the new structure
3. Maintain backward compatibility for existing API

### Phase 3: Update MigrationAction
1. Change `SchemaExpr[_, _]` fields to `DynamicSchemaExpr`
2. Update `MigrationExecutor` to use dynamic evaluation
3. Add Schema for `MigrationAction` serialization

## Key Design Decisions

### 1. Select vs Optic naming
- `Select` is clearer for "select a value at a path"
- Avoids confusion with typed `Optic` from optics library

### 2. NumericTypeTag
- Replaces `NumericPrimitiveType[A]` which contains `Numeric[A]`
- Each tag implements arithmetic operations directly on `DynamicValue`
- Fully serializable (sealed trait of case objects)

### 3. Error handling
- `DynamicSchemaExpr.eval` returns `Either[SchemaError, Seq[DynamicValue]]`
- `SchemaExpr.eval` converts to `Either[OpticCheck, Seq[B]]` for compatibility

### 4. Seq vs Chunk
- `DynamicSchemaExpr.eval` returns `Seq[DynamicValue]` for simplicity
- Internal implementation uses `Chunk` for performance
- Can be changed to `Chunk` if needed

## Complete Case Class List

### DynamicSchemaExpr (22 case classes)
1. `Literal(value: DynamicValue)`
2. `Select(path: DynamicOptic)`
3. `PrimitiveConversion(conversionType: ConversionType)`
4. `Relational(left, right, operator: RelationalOperator)`
5. `Logical(left, right, operator: LogicalOperator)`
6. `Not(expr)`
7. `Arithmetic(left, right, operator: ArithmeticOperator, numericType: NumericTypeTag)`
8. `Bitwise(left, right, operator: BitwiseOperator)`
9. `BitwiseNot(expr)`
10. `StringConcat(left, right)`
11. `StringRegexMatch(regex, string)`
12. `StringLength(string)`
13. `StringSubstring(string, start, end)`
14. `StringTrim(string)`
15. `StringToUpperCase(string)`
16. `StringToLowerCase(string)`
17. `StringReplace(string, target, replacement)`
18. `StringStartsWith(string, prefix)`
19. `StringEndsWith(string, suffix)`
20. `StringContains(string, substring)`
21. `StringIndexOf(string, substring)`

### Supporting Sealed Traits (already exist, reuse)
- `ConversionType` (from current SchemaExpr)
- `RelationalOperator` (from current SchemaExpr)
- `LogicalOperator` (from current SchemaExpr)
- `ArithmeticOperator` (from current SchemaExpr)
- `BitwiseOperator` (from current SchemaExpr)

### New Sealed Trait
- `NumericTypeTag` (replaces NumericPrimitiveType for serialization)

## Example Usage

```scala
// Creating a migration with serializable expressions
val migration = Migration(
  from = schemaV1,
  to = schemaV2,
  actions = Vector(
    MigrationAction.AddField(
      at = DynamicOptic.root.field("newField"),
      default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
    ),
    MigrationAction.TransformValue(
      at = DynamicOptic.root.field("count"),
      transform = DynamicSchemaExpr.Arithmetic(
        left = DynamicSchemaExpr.Select(DynamicOptic.root),
        right = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))),
        operator = DynamicSchemaExpr.ArithmeticOperator.Add,
        numericType = DynamicSchemaExpr.NumericTypeTag.IntTag
      )
    )
  )
)

// Serialize the migration
val json = migration.toJson  // Now works!

// Deserialize and apply
val restored = json.fromJson[Migration]
val result = restored.apply(oldData)
```

## Risks and Mitigations

### Risk 1: Performance overhead from dynamic evaluation
- **Mitigation**: The current `evalDynamic` already operates on `DynamicValue`, so no new overhead
- **Mitigation**: Hot paths can cache converted schemas

### Risk 2: Loss of type safety in MigrationAction
- **Mitigation**: `SchemaExpr[A, B]` wrapper still provides type safety at construction time
- **Mitigation**: Runtime validation during migration execution

### Risk 3: Breaking changes to existing API
- **Mitigation**: Keep `SchemaExpr[A, B]` as the public API
- **Mitigation**: `DynamicSchemaExpr` is internal/advanced use only
