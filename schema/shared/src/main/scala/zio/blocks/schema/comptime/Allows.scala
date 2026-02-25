package zio.blocks.schema.comptime

/**
 * A compile-time capability token that proves type `A` satisfies the grammar
 * shape `S`.
 *
 * `Allows[A, S]` is an upper bound: any type whose structure is a strict subset
 * of `S` also satisfies it. A type that uses only some of the allowed shapes
 * trivially passes. This is analogous to a subtype bound.
 *
 * ==Usage (Scala 3)==
 * {{{
 * import zio.blocks.schema.Schema
 * import zio.blocks.schema.comptime.Allows
 * import Allows._
 *
 * // Flat records only (e.g. CSV, RDBMS row)
 * def writeCsv[A: Schema](rows: Seq[A])(using Allows[A, Record[Primitive | Optional[Primitive]]]): Unit = ???
 *
 * // Discriminated union of flat records (e.g. event bus)
 * def publish[A: Schema](event: A)(using Allows[A, Variant[Record[Primitive | Sequence[Primitive]]]]): Unit = ???
 *
 * // Recursive tree (e.g. GraphQL, JSON Schema)
 * def graphqlType[A: Schema]()(using Allows[A, Record[Primitive | Optional[Self] | Sequence[Self]]]): String = ???
 * }}}
 *
 * ==Usage (Scala 2)==
 *
 * Scala 2 lacks native union types. Use the `` `|`[A, B] `` type alias from the
 * `Allows` companion to express unions:
 *
 * {{{
 * import zio.blocks.schema.comptime.Allows
 * import Allows._
 *
 * def writeCsv[A: Schema](rows: Seq[A])(implicit ev: Allows[A, Record[`|`[Primitive, Optional[Primitive]]]]): Unit = ???
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
 *   - [[Allows.|]] — union of two grammar nodes (Scala 2 alternative to
 *     `A | B`)
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
   */
  abstract class Record[A <: Structural] extends Structural

  /**
   * Matches a variant (sealed trait / enum) whose every case satisfies at least
   * one branch of `A`. `A` is a union of allowed case shapes, not a union of
   * the actual concrete case types. No requirement that all branches of `A` are
   * exercised by actual cases.
   */
  abstract class Variant[A <: Structural] extends Structural

  /**
   * Matches a sequence type (List, Vector, Set, Array, Chunk, …) whose element
   * type satisfies `A`.
   */
  abstract class Sequence[A <: Structural] extends Structural

  /**
   * Matches a map type (Map, HashMap, …) whose key type satisfies `K` and whose
   * value type satisfies `V`.
   */
  abstract class Map[K <: Structural, V <: Structural] extends Structural

  /**
   * Matches `Option[A]` where the inner type satisfies `A`. `Option` is
   * surfaced as a dedicated grammar node for ergonomics rather than as a
   * generic two-case `Variant`.
   */
  abstract class Optional[A <: Structural] extends Structural

  /**
   * Matches a wrapper / newtype / opaque type whose underlying type satisfies
   * `A`.
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
   * Union of two grammar nodes. Used in Scala 2 as a source-compatible
   * alternative to the native `A | B` union type syntax available in Scala 3.
   *
   * For three-or-more alternatives, nest: `` `|`[A, `|`[B, C]] ``.
   *
   * In Scala 3, prefer the native syntax: `A | B | C`.
   *
   * Example (Scala 2):
   * {{{
   * Allows[Person, Record[`|`[Primitive, `|`[Sequence[Primitive], Map[Primitive, Primitive]]]]]
   * }}}
   *
   * Equivalent (Scala 3):
   * {{{
   * Allows[Person, Record[Primitive | Sequence[Primitive] | Map[Primitive, Primitive]]]
   * }}}
   */
  abstract class `|`[A <: Structural, B <: Structural] extends Structural
}
