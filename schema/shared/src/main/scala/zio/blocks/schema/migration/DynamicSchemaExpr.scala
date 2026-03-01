package zio.blocks.schema.migration

import zio.blocks.schema.DynamicValue

/**
 * A pure, serializable expression that describes a value-level transformation.
 *
 * `DynamicSchemaExpr` is the dynamic counterpart of
 * [[zio.blocks.schema.SchemaExpr]]. It contains no functions, closures, or
 * runtime code — only pure data that can be serialized, stored in registries,
 * and interpreted by the migration execution engine.
 *
 * All primitive conversions are named case objects — the conversion logic lives
 * entirely in [[DynamicMigration]], not here.
 */
sealed trait DynamicSchemaExpr {

  /** Structurally reverse this expression. */
  def reverse: DynamicSchemaExpr
}

object DynamicSchemaExpr {

  // ─────────────────────────────────────────────────────────────────────────
  // Core Expressions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A literal constant value — always produces this value regardless of input.
   *
   * Corresponds to `SchemaExpr.Literal` but operates on [[DynamicValue]].
   *
   * @param value
   *   the constant value to produce
   */
  final case class Literal(value: DynamicValue) extends DynamicSchemaExpr {
    def reverse: DynamicSchemaExpr = this
  }

  /** Identity — passes the input value through unchanged. */
  case object Identity extends DynamicSchemaExpr {
    def reverse: DynamicSchemaExpr = Identity
  }

  /**
   * The default value for a field, as determined by its schema.
   *
   * Corresponds to `SchemaExpr.DefaultValue` from the spec. The actual default
   * is resolved at builder time and stored as a [[Literal]]; this sentinel is
   * used when no explicit default is available for reverse migrations.
   */
  case object DefaultValue extends DynamicSchemaExpr {
    def reverse: DynamicSchemaExpr = DefaultValue
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Primitive Conversions (pure data descriptors)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * A named primitive-to-primitive conversion.
   *
   * Each case is a pure data descriptor. The actual conversion logic lives in
   * the migration execution engine ([[DynamicMigration]]).
   */
  sealed trait PrimitiveConversion extends DynamicSchemaExpr

  /** Convert Int to String. */
  case object IntToString extends PrimitiveConversion {
    def reverse: DynamicSchemaExpr = StringToInt
  }

  /** Convert String to Int. */
  case object StringToInt extends PrimitiveConversion {
    def reverse: DynamicSchemaExpr = IntToString
  }

  /** Convert Long to String. */
  case object LongToString extends PrimitiveConversion {
    def reverse: DynamicSchemaExpr = StringToLong
  }

  /** Convert String to Long. */
  case object StringToLong extends PrimitiveConversion {
    def reverse: DynamicSchemaExpr = LongToString
  }

  /** Convert Int to Long (widening). */
  case object IntToLong extends PrimitiveConversion {
    def reverse: DynamicSchemaExpr = LongToInt
  }

  /** Convert Long to Int (narrowing, may overflow). */
  case object LongToInt extends PrimitiveConversion {
    def reverse: DynamicSchemaExpr = IntToLong
  }

  /** Convert Boolean to String ("true"/"false"). */
  case object BooleanToString extends PrimitiveConversion {
    def reverse: DynamicSchemaExpr = StringToBoolean
  }

  /** Convert String to Boolean ("true"/"false"). */
  case object StringToBoolean extends PrimitiveConversion {
    def reverse: DynamicSchemaExpr = BooleanToString
  }
}
