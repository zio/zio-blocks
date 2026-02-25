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
 *   - [[Allows.Sequence]] — a collection (List, Vector, Set, …); elements
 *     satisfy `A`
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
  abstract class Structural

  /**
   * Matches any primitive type. Also serves as the parent of all specific
   * primitive grammar nodes — a type satisfying e.g. [[Primitive.Int]] also
   * satisfies `Primitive`.
   *
   * To restrict to a subset of primitives, use the specific subtypes:
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
  abstract class Primitive extends Structural

  object Primitive {

    /**
     * Matches `scala.Unit` only.
     */
    abstract class Unit extends Primitive

    /**
     * Matches `scala.Boolean` only.
     */
    abstract class Boolean extends Primitive

    /**
     * Matches `scala.Byte` only.
     */
    abstract class Byte extends Primitive

    /**
     * Matches `scala.Short` only.
     */
    abstract class Short extends Primitive

    /**
     * Matches `scala.Int` only.
     */
    abstract class Int extends Primitive

    /**
     * Matches `scala.Long` only.
     */
    abstract class Long extends Primitive

    /**
     * Matches `scala.Float` only.
     */
    abstract class Float extends Primitive

    /**
     * Matches `scala.Double` only.
     */
    abstract class Double extends Primitive

    /**
     * Matches `scala.Char` only.
     */
    abstract class Char extends Primitive

    /**
     * Matches `java.lang.String` only.
     */
    abstract class String extends Primitive

    /**
     * Matches `scala.BigInt` only.
     */
    abstract class BigInt extends Primitive

    /**
     * Matches `scala.BigDecimal` only.
     */
    abstract class BigDecimal extends Primitive

    /**
     * Matches `java.util.UUID` only.
     */
    abstract class UUID extends Primitive

    /**
     * Matches `java.util.Currency` only.
     */
    abstract class Currency extends Primitive

    /**
     * Matches `java.time.DayOfWeek` only.
     */
    abstract class DayOfWeek extends Primitive

    /**
     * Matches `java.time.Duration` only.
     */
    abstract class Duration extends Primitive

    /**
     * Matches `java.time.Instant` only.
     */
    abstract class Instant extends Primitive

    /**
     * Matches `java.time.LocalDate` only.
     */
    abstract class LocalDate extends Primitive

    /**
     * Matches `java.time.LocalDateTime` only.
     */
    abstract class LocalDateTime extends Primitive

    /**
     * Matches `java.time.LocalTime` only.
     */
    abstract class LocalTime extends Primitive

    /**
     * Matches `java.time.Month` only.
     */
    abstract class Month extends Primitive

    /**
     * Matches `java.time.MonthDay` only.
     */
    abstract class MonthDay extends Primitive

    /**
     * Matches `java.time.OffsetDateTime` only.
     */
    abstract class OffsetDateTime extends Primitive

    /**
     * Matches `java.time.OffsetTime` only.
     */
    abstract class OffsetTime extends Primitive

    /**
     * Matches `java.time.Period` only.
     */
    abstract class Period extends Primitive

    /**
     * Matches `java.time.Year` only.
     */
    abstract class Year extends Primitive

    /**
     * Matches `java.time.YearMonth` only.
     */
    abstract class YearMonth extends Primitive

    /**
     * Matches `java.time.ZoneId` only.
     */
    abstract class ZoneId extends Primitive

    /**
     * Matches `java.time.ZoneOffset` only.
     */
    abstract class ZoneOffset extends Primitive

    /**
     * Matches `java.time.ZonedDateTime` only.
     */
    abstract class ZonedDateTime extends Primitive
  }

  /**
   * Matches a record (case class / product type) whose every field satisfies
   * `A`. Vacuously true for zero-field records (case objects / enum
   * singletons).
   *
   * @tparam A
   *   The constraint that every field of the record must satisfy.
   */
  abstract class Record[A <: Structural] extends Structural

  /**
   * Matches a sequence type (List, Vector, Set, Array, Chunk, …) whose element
   * type satisfies `A`.
   *
   * @tparam A
   *   The constraint that the element type of the sequence must satisfy.
   */
  abstract class Sequence[A <: Structural] extends Structural

  /**
   * Matches a map type (Map, HashMap, …) whose key type satisfies `K` and whose
   * value type satisfies `V`.
   *
   * @tparam K
   *   The constraint that the key type of the map must satisfy.
   * @tparam V
   *   The constraint that the value type of the map must satisfy.
   */
  abstract class Map[K <: Structural, V <: Structural] extends Structural

  /**
   * Matches `Option[A]` where the inner type satisfies `A`. `Option` is handled
   * as a dedicated grammar node rather than being auto-unwrapped as a two-case
   * sealed trait.
   *
   * @tparam A
   *   The constraint that the inner (unwrapped) type of the `Option` must
   *   satisfy.
   */
  abstract class Optional[A <: Structural] extends Structural

  /**
   * Matches a wrapper / newtype / opaque type whose underlying type satisfies
   * `A`.
   *
   * @tparam A
   *   The constraint that the underlying type of the wrapper must satisfy.
   */
  abstract class Wrapped[A <: Structural] extends Structural

  /**
   * Matches `DynamicValue` — the schema-less escape hatch. No further
   * constraint is imposed on the value.
   */
  abstract class Dynamic extends Structural

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
  abstract class Self extends Structural

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
  abstract class `|`[A <: Structural, B <: Structural] extends Structural
}
