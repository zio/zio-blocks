package zio.blocks.schema.binding

/**
 * A type registry is a type-safe repository of bindings indexed by type
 * identity.
 *
 * TypeRegistry is a type alias for [[BindingResolver.Registry]]. It is retained
 * for source compatibility but new code should use `BindingResolver.Registry`
 * or the `BindingResolver` trait directly.
 *
 * @see
 *   [[BindingResolver]] for the composable resolver trait
 * @see
 *   [[BindingResolver.Registry]] for the map-backed implementation
 */
object TypeRegistry {

  /**
   * Type alias for the map-backed binding registry.
   */
  type Registry = BindingResolver.Registry

  /**
   * Creates an empty TypeRegistry with no bindings.
   */
  val empty: Registry = BindingResolver.empty

  /**
   * Creates a TypeRegistry with bindings for all primitive types.
   *
   * This includes:
   *   - Unit, Boolean, Byte, Short, Int, Long, Float, Double, Char, String
   *   - BigInt, BigDecimal
   *   - java.time types (DayOfWeek, Duration, Instant, LocalDate, etc.)
   *   - java.util.Currency, java.util.UUID
   *   - DynamicValue
   *   - Common sequence types (List, Vector, Set, IndexedSeq, Seq, Chunk)
   *   - Map
   */
  val default: Registry = BindingResolver.defaults
}
