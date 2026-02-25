package zio.blocks.schema.comptime

/**
 * A compile-time capability token that proves type `A` satisfies the grammar
 * shape `S`.
 *
 * `Allows[A, S]` is an upper bound: any type whose structure is a strict subset
 * of `S` also satisfies it. A type that uses only some of the allowed shapes
 * trivially passes. This is analogous to a subtype bound.
 *
 * @tparam A
 *   The Scala data type being validated.
 * @tparam S
 *   The grammar shape that `A` must satisfy. Must be a subtype of
 *   [[Allows.Structural]].
 *
 * ==Usage (Scala 3)==
 * {{{
 * import zio.blocks.schema.Schema
 * import zio.blocks.schema.comptime.Allows
 * import Allows._
 *
 * // Flat records only (e.g. CSV, RDBMS row)
 * def writeCsv[A: Schema](rows: Seq[A])(using
 *   Allows[A, Record[Primitive | Optional[Primitive]]]
 * ): Unit = ???
 *
 * // Discriminated union of flat records (e.g. event bus)
 * def publish[A: Schema](event: A)(using
 *   Allows[A, Variant[Record[Primitive | Sequence[Primitive]]]]
 * ): Unit = ???
 *
 * // Recursive tree (e.g. GraphQL, JSON Schema)
 * def graphqlType[A: Schema]()(using
 *   Allows[A, Record[Primitive | Optional[Self] | Sequence[Self]]]
 * ): String = ???
 * }}}
 *
 * ==Usage (Scala 2)==
 *
 * Scala 2 lacks native union types. Use the `` `|`[A, B] `` type from the
 * `Allows` companion written in infix position to express unions:
 *
 * {{{
 * import zio.blocks.schema.Schema
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
 *   - [[Allows.Primitive]] — any scalar type (Int, String, UUID, Instant, …)
 *   - [[Allows.Record]] — a product type (case class); fields must satisfy `A`
 *   - [[Allows.Variant]] — a sum type (sealed trait / enum); cases must satisfy
 *     `A`
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
   * Matches any primitive type: Unit, Boolean, Byte, Short, Int, Long, Float,
   * Double, Char, String, BigInt, BigDecimal, UUID, Currency, and all
   * `java.time.*` types.
   */
  abstract class Primitive extends Structural

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
   * Matches a variant (sealed trait / enum) whose every case satisfies at least
   * one branch of `A`. `A` is a union of allowed case shapes, not a union of
   * the actual concrete case types. No requirement that all branches of `A` are
   * exercised by actual cases.
   *
   * @tparam A
   *   The constraint that every case of the variant must satisfy.
   */
  abstract class Variant[A <: Structural] extends Structural

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
   * Matches `Option[A]` where the inner type satisfies `A`. `Option` is
   * surfaced as a dedicated grammar node for ergonomics rather than as a
   * generic two-case `Variant`.
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
