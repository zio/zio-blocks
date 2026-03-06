package zio.blocks.schema.comptime

/**
 * A compile-time capability token that proves type `A` satisfies the grammar
 * shape `S`.
 *
 * `Allows[A, S]` is an upper bound: any type whose structure is a strict subset
 * of `S` also satisfies it. A type that uses only some of the allowed shapes
 * trivially passes. This is analogous to a subtype bound.
 *
 * `Allows` does not require or use `Schema[A]`. It inspects the Scala type
 * structure of `A` directly at compile time. Any `Schema[A]` that appears
 * alongside `Allows` in examples is the library author's own separate
 * constraint — it is not imposed by `Allows` itself.
 *
 * @tparam A
 *   The Scala data type being validated.
 * @tparam S
 *   The grammar shape that `A` must satisfy. Must be a subtype of
 *   [[Allows.Structural]].
 *
 * ==Usage (Scala 3)==
 * {{{
 * import zio.blocks.schema.comptime.Allows
 * import Allows._
 *
 * // Shape constraint alone — no Schema required
 * def validate[A](v: A)(using Allows[A, Record[Primitive]]): Boolean = ???
 *
 * // Specific primitives only (e.g. JSON: no Char, UUID, java.time types)
 * type JsonPrimitive = Primitive.Boolean | Primitive.Int | Primitive.Long |
 *                      Primitive.Float | Primitive.Double | Primitive.String |
 *                      Primitive.BigInt | Primitive.BigDecimal | Primitive.Unit
 *
 * def toJson[A](doc: A)(using Allows[A, Record[JsonPrimitive | Self]]): String = ???
 *
 * // Combined with Schema when runtime encoding is also needed
 * def writeCsv[A: Schema](rows: Seq[A])(using
 *   Allows[A, Record[Primitive | Optional[Primitive]]]
 * ): Unit = ???
 * }}}
 *
 * ==Usage (Scala 2)==
 *
 * Scala 2 lacks native union types. Use the `` `|`[A, B] `` type from the
 * `Allows` companion written in infix position to express unions:
 *
 * {{{
 * import zio.blocks.schema.comptime.Allows
 * import Allows._
 *
 * def writeCsv[A: Schema](rows: Seq[A])(implicit
 *   ev: Allows[A, Record[Primitive | Optional[Primitive]]]
 * ): Unit = ???
 * }}}
 *
 * ==Grammar nodes==
 *
 *   - [[Allows.Primitive]] — any primitive scalar; or use a specific subtype
 *     such as [[Allows.Primitive$.Int]] to restrict to a single kind
 *   - [[Allows.Record]] — a product type (case class); fields must satisfy `A`.
 *     Sealed traits and enums are automatically unwrapped: each case is checked
 *     individually against the grammar, so `Variant` is not needed.
 *   - [[Allows.Sequence]] — any collection (List, Vector, Set, Array, Chunk,
 *     …); elements satisfy `A`. Use the subtypes in `Sequence.List`,
 *     `Sequence.Set`, `Sequence.Vector`, `Sequence.Array`, and `Sequence.Chunk`
 *     to narrow to a specific collection kind.
 *   - [[Allows.IsType]] — nominal type predicate; satisfied only when the Scala
 *     type is exactly `A` (`=:=`). Used as an element constraint to align a
 *     polymorphic type parameter with the element type of a collection, e.g.
 *     `Sequence.Set[IsType[A]]`.
 *   - [[Allows.Map]] — a key-value map; keys satisfy `K`, values satisfy `V`
 *   - [[Allows.Optional]] — `Option[A]`; inner type satisfies `A`
 *   - [[Allows.Wrapped]] — a newtype / opaque type; underlying type satisfies
 *     `A`
 *   - [[Allows.Dynamic]] — `DynamicValue`; no further constraint
 *   - [[Allows.Self]] — recursive self-reference back to the root grammar
 *   - [[Allows.`|`]] — union of two grammar nodes; write as `A | B` in infix
 *     position
 */
sealed abstract class Allows[A, S <: Allows.Structural]

object Allows extends AllowsCompanionVersionSpecific {

  /**
   * Single private singleton reused by the macro at every successful call site
   * via a cast. This eliminates any per-call-site allocation.
   */
  private[comptime] val instance: Allows[Any, Structural] = new Allows[Any, Structural] {}

  /** Root of the grammar type hierarchy. All shape descriptors extend this. */
  sealed abstract class Structural

  /**
   * Catch-all grammar node: matches any of the 30 Schema primitive types.
   *
   * For stricter control, use the type aliases in `Primitive.Int`,
   * `Primitive.String`, etc. — each is defined as `IsType[X]` for the
   * corresponding Scala/Java type, and is handled by the same `IsType` macro
   * path. A type that satisfies `Primitive.Int` also satisfies `Primitive` via
   * the union check; no special subtype relationship is required.
   *
   * {{{
   * // Allow only JSON-representable numbers:
   * type JsonNumber = Primitive.Int | Primitive.Long | Primitive.Double
   *
   * // Allow any primitive (the traditional usage):
   * type AnyPrim = Primitive
   * }}}
   *
   * In Scala 2 you may also write `` Primitive.Int `|` Primitive.Long ``.
   */
  sealed abstract class Primitive extends Structural

  object Primitive {

    /**
     * Matches `scala.Unit` only.
     *
     * This is a type alias for [[IsType]]`[scala.Unit]`. The macro routes it
     * through the same `IsType` path as any other nominal type predicate.
     */
    type Unit = IsType[scala.Unit]

    /**
     * Matches `scala.Boolean` only.
     *
     * Type alias for [[IsType]]`[scala.Boolean]`.
     */
    type Boolean = IsType[scala.Boolean]

    /**
     * Matches `scala.Byte` only.
     *
     * Type alias for [[IsType]]`[scala.Byte]`.
     */
    type Byte = IsType[scala.Byte]

    /**
     * Matches `scala.Short` only.
     *
     * Type alias for [[IsType]]`[scala.Short]`.
     */
    type Short = IsType[scala.Short]

    /**
     * Matches `scala.Int` only.
     *
     * Type alias for [[IsType]]`[scala.Int]`.
     */
    type Int = IsType[scala.Int]

    /**
     * Matches `scala.Long` only.
     *
     * Type alias for [[IsType]]`[scala.Long]`.
     */
    type Long = IsType[scala.Long]

    /**
     * Matches `scala.Float` only.
     *
     * Type alias for [[IsType]]`[scala.Float]`.
     */
    type Float = IsType[scala.Float]

    /**
     * Matches `scala.Double` only.
     *
     * Type alias for [[IsType]]`[scala.Double]`.
     */
    type Double = IsType[scala.Double]

    /**
     * Matches `scala.Char` only.
     *
     * Type alias for [[IsType]]`[scala.Char]`.
     */
    type Char = IsType[scala.Char]

    /**
     * Matches `java.lang.String` only.
     *
     * Type alias for [[IsType]]`[java.lang.String]`.
     */
    type String = IsType[java.lang.String]

    /**
     * Matches `scala.math.BigInt` only.
     *
     * Type alias for [[IsType]]`[scala.math.BigInt]`.
     */
    type BigInt = IsType[scala.math.BigInt]

    /**
     * Matches `scala.math.BigDecimal` only.
     *
     * Type alias for [[IsType]]`[scala.math.BigDecimal]`.
     */
    type BigDecimal = IsType[scala.math.BigDecimal]

    /**
     * Matches `java.util.UUID` only.
     *
     * Type alias for [[IsType]]`[java.util.UUID]`.
     */
    type UUID = IsType[java.util.UUID]

    /**
     * Matches `java.util.Currency` only.
     *
     * Type alias for [[IsType]]`[java.util.Currency]`.
     */
    type Currency = IsType[java.util.Currency]

    /**
     * Matches `java.time.DayOfWeek` only.
     *
     * Type alias for [[IsType]]`[java.time.DayOfWeek]`.
     */
    type DayOfWeek = IsType[java.time.DayOfWeek]

    /**
     * Matches `java.time.Duration` only.
     *
     * Type alias for [[IsType]]`[java.time.Duration]`.
     */
    type Duration = IsType[java.time.Duration]

    /**
     * Matches `java.time.Instant` only.
     *
     * Type alias for [[IsType]]`[java.time.Instant]`.
     */
    type Instant = IsType[java.time.Instant]

    /**
     * Matches `java.time.LocalDate` only.
     *
     * Type alias for [[IsType]]`[java.time.LocalDate]`.
     */
    type LocalDate = IsType[java.time.LocalDate]

    /**
     * Matches `java.time.LocalDateTime` only.
     *
     * Type alias for [[IsType]]`[java.time.LocalDateTime]`.
     */
    type LocalDateTime = IsType[java.time.LocalDateTime]

    /**
     * Matches `java.time.LocalTime` only.
     *
     * Type alias for [[IsType]]`[java.time.LocalTime]`.
     */
    type LocalTime = IsType[java.time.LocalTime]

    /**
     * Matches `java.time.Month` only.
     *
     * Type alias for [[IsType]]`[java.time.Month]`.
     */
    type Month = IsType[java.time.Month]

    /**
     * Matches `java.time.MonthDay` only.
     *
     * Type alias for [[IsType]]`[java.time.MonthDay]`.
     */
    type MonthDay = IsType[java.time.MonthDay]

    /**
     * Matches `java.time.OffsetDateTime` only.
     *
     * Type alias for [[IsType]]`[java.time.OffsetDateTime]`.
     */
    type OffsetDateTime = IsType[java.time.OffsetDateTime]

    /**
     * Matches `java.time.OffsetTime` only.
     *
     * Type alias for [[IsType]]`[java.time.OffsetTime]`.
     */
    type OffsetTime = IsType[java.time.OffsetTime]

    /**
     * Matches `java.time.Period` only.
     *
     * Type alias for [[IsType]]`[java.time.Period]`.
     */
    type Period = IsType[java.time.Period]

    /**
     * Matches `java.time.Year` only.
     *
     * Type alias for [[IsType]]`[java.time.Year]`.
     */
    type Year = IsType[java.time.Year]

    /**
     * Matches `java.time.YearMonth` only.
     *
     * Type alias for [[IsType]]`[java.time.YearMonth]`.
     */
    type YearMonth = IsType[java.time.YearMonth]

    /**
     * Matches `java.time.ZoneId` only.
     *
     * Type alias for [[IsType]]`[java.time.ZoneId]`.
     */
    type ZoneId = IsType[java.time.ZoneId]

    /**
     * Matches `java.time.ZoneOffset` only.
     *
     * Type alias for [[IsType]]`[java.time.ZoneOffset]`.
     */
    type ZoneOffset = IsType[java.time.ZoneOffset]

    /**
     * Matches `java.time.ZonedDateTime` only.
     *
     * Type alias for [[IsType]]`[java.time.ZonedDateTime]`.
     */
    type ZonedDateTime = IsType[java.time.ZonedDateTime]
  }

  /**
   * Matches a record (case class / product type) whose every field satisfies
   * `A`. Vacuously true for zero-field records (case objects / enum
   * singletons).
   *
   * @tparam A
   *   The constraint that every field of the record must satisfy.
   */
  sealed abstract class Record[A <: Structural] extends Structural

  /**
   * Matches any sequence type (List, Vector, Set, Array, Chunk, …) whose
   * element type satisfies `A`.
   *
   * Use the subtypes in `Sequence.List`, `Sequence.Vector`, `Sequence.Set`,
   * `Sequence.Array`, and `Sequence.Chunk` when you need to distinguish a
   * specific collection kind. Each subtype extends `Sequence[A]`, so a grammar
   * written with the parent still accepts all collections.
   *
   * {{{
   * // Any collection of primitives:
   * Allows[List[Int], Sequence[Primitive]]
   *
   * // Only a Set, not a List:
   * Allows[Set[Int], Sequence.Set[Primitive]]
   *
   * // Only a Set, and the element type must be exactly Int:
   * Allows[Set[Int], Sequence.Set[IsType[Int]]]
   * }}}
   *
   * @tparam A
   *   The constraint that the element type of the sequence must satisfy.
   */
  sealed abstract class Sequence[A <: Structural] extends Structural

  object Sequence {

    /**
     * Matches `scala.collection.immutable.List[_]` (and subtypes such as
     * `scala.collection.immutable.Nil.type`) whose element type satisfies `A`.
     *
     * @tparam A
     *   The constraint that the element type must satisfy.
     */
    sealed abstract class List[A <: Structural] extends Sequence[A]

    /**
     * Matches `scala.collection.immutable.Vector[_]` whose element type
     * satisfies `A`.
     *
     * @tparam A
     *   The constraint that the element type must satisfy.
     */
    sealed abstract class Vector[A <: Structural] extends Sequence[A]

    /**
     * Matches `scala.collection.immutable.Set[_]` (and subtypes such as
     * `HashSet`, `ListSet`, etc.) whose element type satisfies `A`.
     *
     * @tparam A
     *   The constraint that the element type must satisfy.
     */
    sealed abstract class Set[A <: Structural] extends Sequence[A]

    /**
     * Matches `scala.Array[_]` whose element type satisfies `A`.
     *
     * @tparam A
     *   The constraint that the element type must satisfy.
     */
    sealed abstract class Array[A <: Structural] extends Sequence[A]

    /**
     * Matches `zio.blocks.chunk.Chunk[_]` whose element type satisfies `A`.
     *
     * @tparam A
     *   The constraint that the element type must satisfy.
     */
    sealed abstract class Chunk[A <: Structural] extends Sequence[A]
  }

  /**
   * Matches a map type (Map, HashMap, …) whose key type satisfies `K` and whose
   * value type satisfies `V`.
   *
   * @tparam K
   *   The constraint that the key type of the map must satisfy.
   * @tparam V
   *   The constraint that the value type of the map must satisfy.
   */
  sealed abstract class Map[K <: Structural, V <: Structural] extends Structural

  /**
   * Matches `Option[A]` where the inner type satisfies `A`. `Option` is handled
   * as a dedicated grammar node rather than being auto-unwrapped as a two-case
   * sealed trait.
   *
   * @tparam A
   *   The constraint that the inner (unwrapped) type of the `Option` must
   *   satisfy.
   */
  sealed abstract class Optional[A <: Structural] extends Structural

  /**
   * Matches a wrapper / newtype / opaque type whose underlying type satisfies
   * `A`.
   *
   * @tparam A
   *   The constraint that the underlying type of the wrapper must satisfy.
   */
  sealed abstract class Wrapped[A <: Structural] extends Structural

  /**
   * Matches `DynamicValue` — the schema-less escape hatch. No further
   * constraint is imposed on the value.
   */
  sealed abstract class Dynamic extends Structural

  /**
   * Recursive self-reference. When used inside a grammar expression, `Self`
   * refers back to the entire enclosing `Allows[…, S]` constraint, allowing the
   * grammar to describe recursive data structures.
   *
   * Mutual recursion between two or more distinct types is a compile-time
   * error.
   *
   * Non-recursive types satisfy `Self`-containing grammars without issue: since
   * the `Self` position is never reached, the constraint is vacuously
   * satisfied.
   */
  sealed abstract class Self extends Structural

  /**
   * Nominal type predicate. `IsType[A]` is satisfied only when the Scala type
   * being checked is exactly `A` (i.e. `checked =:= A`).
   *
   * This is the building block for precise element-type constraints. The
   * canonical use-case is constraining the element type of a polymorphic DSL
   * method so that no separate type-alignment proof (e.g. a `Containable` type
   * class) is needed:
   *
   * {{{
   * import zio.blocks.typeid.IsNominalType
   *
   * // `To` must be a Set whose element type is exactly `A`.
   * // `IsNominalType[A]` ensures A is concrete at the call site.
   * def contains[A: IsNominalType](a: A)(implicit
   *   ev: Allows[To, Sequence.Set[IsType[A]]]
   * ): ConditionExpression[From]
   * }}}
   *
   * `IsType[A]` can also replace specific primitive nodes as type aliases:
   * {{{
   * type MyInt = IsType[scala.Int]  // equivalent to Primitive.Int semantically
   * }}}
   *
   * Requires that `A` is a concrete nominal type. Combining with
   * `IsNominalType[A]` (from `zio-blocks-typeid`) at the call site ensures the
   * macro always sees a concrete `A`.
   *
   * @tparam A
   *   The exact Scala type that must match.
   */
  sealed abstract class IsType[A] extends Structural

  /**
   * Union of two grammar nodes. Write in infix position: `A | B`.
   *
   * In Scala 3 the native union type syntax `A | B` is also accepted and
   * produces identical semantics with a more concise spelling. In Scala 2,
   * write `` A `|` B `` in infix position.
   *
   * For three-or-more alternatives, chain: `` A `|` B `|` C ``.
   *
   * @tparam A
   *   The left-hand grammar node alternative.
   * @tparam B
   *   The right-hand grammar node alternative.
   *
   * Example (both Scala 2 infix and Scala 3 native produce the same grammar):
   * {{{
   * Allows[Person, Record[Primitive | Sequence[Primitive] | Map[Primitive, Primitive]]]
   * }}}
   */
  sealed abstract class `|`[A <: Structural, B <: Structural] extends Structural
}
