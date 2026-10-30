/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.scope

/**
 * A typeclass for the types whose values may leave a scope: plain data that
 * holds no resource.
 *
 * `scoped` and `Resource#use` return only a type with an `Unscoped` instance,
 * and `$` returns a raw `B` instead of `$[B]` when `B` has one. A type without
 * an instance, such as a connection or a stream, stays inside its scope.
 *
 * ==Default Instances==
 *
 * The companion provides instances for:
 *   - Primitives: `Int`, `Long`, `Short`, `Byte`, `Char`, `Boolean`, `Float`,
 *     `Double`, `Unit`, and `Nothing`
 *   - Text: `String`
 *   - Numeric: `BigInt`, `BigDecimal`
 *   - Collections: `Array`, `List`, `Vector`, `Set`, `Seq`, `IndexedSeq`,
 *     `Iterable`, `Map` and [[zio.blocks.chunk.Chunk]] (when elements are
 *     `Unscoped`)
 *   - Containers: `Option`, `Either` (when elements are `Unscoped`)
 *   - Tuples: up to 4-tuples (when elements are `Unscoped`); on Scala 3, larger
 *     tuples get one through `derived`
 *   - Time: `java.time.Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`,
 *     `ZonedDateTime`, `OffsetDateTime`, `Duration`, `Period`, `ZoneId` and
 *     `ZoneOffset`, and `scala.concurrent.duration.Duration` and
 *     `FiniteDuration`
 *   - Other: `java.util.UUID`
 *
 * ==Your Own Types==
 *
 * On Scala 3, every case class, sealed trait and enum whose fields or cases all
 * have instances gets one automatically, through the `derived` given. On Scala
 * 2, define an instance with `Unscoped.derived`, as in the example (this also
 * compiles on Scala 3). On Scala 3 the automatic instance is also given to a
 * case class that extends `AutoCloseable`, so a closed resource can leave its
 * scope (a known bug). Do not define instances for types that hold resources.
 *
 * {{{
 * final case class Report(title: String, rows: Int)
 * object Report {
 *   implicit val unscoped: Unscoped[Report] = Unscoped.derived[Report]
 * }
 *
 * final class Database extends AutoCloseable {
 *   def count(table: String): Int = table.length
 *   def close(): Unit             = ()
 * }
 *
 * val report: Report = Scope.global.scoped { scope =>
 *   import scope._
 *   val db = allocate(new Database)
 *   Report("users", $(db)(_.count("users")))
 * }
 * // report: Report(users,5)
 * }}}
 *
 * @tparam A
 *   the type whose values may leave a scope
 * @see
 *   [[https://zioblocks.com/reference/resource-management/unscoped/ Unscoped]]
 */
trait Unscoped[A]

/**
 * Low-priority Unscoped instances to avoid ambiguity.
 */
private[scope] trait UnscopedLowPriority {
  // Nothing - can always escape (never actually returned); kept in a
  // lower-priority trait than the other instances
  /**
   * Marks `Nothing` as safe to leave a scope. It is declared in a
   * lower-priority trait than the other instances. A `scoped` block that always
   * throws still needs its type written out, as in `scoped[Nothing] { ... }` (a
   * known bug).
   */
  given given_Unscoped_Nothing: Unscoped[Nothing] = new Unscoped[Nothing] {}
}

/**
 * Instances of [[Unscoped]] for common data types, and `derived` for your own.
 *
 * ==Adding Custom Instances==
 *
 * To mark your own type as safe to leave a scope, derive an instance (see
 * `derived`) or define one directly; this form compiles on Scala 2 and 3:
 *
 * {{{
 * final case class UserId(value: Long)
 * object UserId {
 *   implicit val unscopedUserId: Unscoped[UserId] = new Unscoped[UserId] {}
 * }
 * }}}
 *
 * Only add `Unscoped` instances for plain data types that hold no resources.
 * Resource types (streams, connections, handles) should not have instances.
 */
object Unscoped extends UnscopedVersionSpecific with UnscopedLowPriority {
  // Primitives
  /** Marks `Int` values as safe to leave a scope. */
  given Unscoped[Int] = new Unscoped[Int] {}

  /** Marks `Long` values as safe to leave a scope. */
  given Unscoped[Long] = new Unscoped[Long] {}

  /** Marks `Short` values as safe to leave a scope. */
  given Unscoped[Short] = new Unscoped[Short] {}

  /** Marks `Byte` values as safe to leave a scope. */
  given Unscoped[Byte] = new Unscoped[Byte] {}

  /** Marks `Char` values as safe to leave a scope. */
  given Unscoped[Char] = new Unscoped[Char] {}

  /** Marks `Boolean` values as safe to leave a scope. */
  given Unscoped[Boolean] = new Unscoped[Boolean] {}

  /** Marks `Float` values as safe to leave a scope. */
  given Unscoped[Float] = new Unscoped[Float] {}

  /** Marks `Double` values as safe to leave a scope. */
  given Unscoped[Double] = new Unscoped[Double] {}

  /** Marks `Unit` values as safe to leave a scope. */
  given Unscoped[Unit] = new Unscoped[Unit] {}

  /** Marks `String` values as safe to leave a scope. */
  given Unscoped[String] = new Unscoped[String] {}

  /** Marks `BigInt` values as safe to leave a scope. */
  given Unscoped[BigInt] = new Unscoped[BigInt] {}

  /** Marks `BigDecimal` values as safe to leave a scope. */
  given Unscoped[BigDecimal] = new Unscoped[BigDecimal] {}

  // Collections of unscoped elements
  /** Marks `Array[A]` as safe to leave a scope when its element type `A` is. */
  given [A: Unscoped]: Unscoped[Array[A]] = new Unscoped[Array[A]] {}

  /** Marks `List[A]` as safe to leave a scope when its element type `A` is. */
  given [A: Unscoped]: Unscoped[List[A]] = new Unscoped[List[A]] {}

  /**
   * Marks `Vector[A]` as safe to leave a scope when its element type `A` is.
   */
  given [A: Unscoped]: Unscoped[Vector[A]] = new Unscoped[Vector[A]] {}

  /** Marks `Set[A]` as safe to leave a scope when its element type `A` is. */
  given [A: Unscoped]: Unscoped[Set[A]] = new Unscoped[Set[A]] {}

  /**
   * Marks `Option[A]` as safe to leave a scope when its element type `A` is.
   */
  given [A: Unscoped]: Unscoped[Option[A]] = new Unscoped[Option[A]] {}

  /** Marks `Seq[A]` as safe to leave a scope when its element type `A` is. */
  given [A: Unscoped]: Unscoped[Seq[A]] = new Unscoped[Seq[A]] {}

  /**
   * Marks `IndexedSeq[A]` as safe to leave a scope when its element type `A`
   * is.
   */
  given [A: Unscoped]: Unscoped[IndexedSeq[A]] = new Unscoped[IndexedSeq[A]] {}

  /**
   * Marks `Iterable[A]` as safe to leave a scope when its element type `A` is.
   */
  given [A: Unscoped]: Unscoped[Iterable[A]] = new Unscoped[Iterable[A]] {}

  // Either
  /**
   * Marks `Either[A, B]` as safe to leave a scope when both `A` and `B` are.
   */
  given [A: Unscoped, B: Unscoped]: Unscoped[Either[A, B]] = new Unscoped[Either[A, B]] {}

  // Tuples of unscoped elements
  /** Marks the tuple `(A, B)` as safe to leave a scope when `A` and `B` are. */
  given [A: Unscoped, B: Unscoped]: Unscoped[(A, B)] = new Unscoped[(A, B)] {}

  /**
   * Marks the tuple `(A, B, C)` as safe to leave a scope when `A`, `B` and `C`
   * are.
   */
  given [A: Unscoped, B: Unscoped, C: Unscoped]: Unscoped[(A, B, C)] = new Unscoped[(A, B, C)] {}

  /**
   * Marks the tuple `(A, B, C, D)` as safe to leave a scope when `A`, `B`, `C`
   * and `D` are.
   */
  given [A: Unscoped, B: Unscoped, C: Unscoped, D: Unscoped]: Unscoped[(A, B, C, D)] =
    new Unscoped[(A, B, C, D)] {}

  // Maps with unscoped keys and values
  /**
   * Marks `Map[K, V]` as safe to leave a scope when its key type `K` and value
   * type `V` are.
   */
  given [K: Unscoped, V: Unscoped]: Unscoped[Map[K, V]] = new Unscoped[Map[K, V]] {}

  // Java time types (immutable value types)
  /** Marks `java.time.Instant` values as safe to leave a scope. */
  given Unscoped[java.time.Instant] = new Unscoped[java.time.Instant] {}

  /** Marks `java.time.LocalDate` values as safe to leave a scope. */
  given Unscoped[java.time.LocalDate] = new Unscoped[java.time.LocalDate] {}

  /** Marks `java.time.LocalTime` values as safe to leave a scope. */
  given Unscoped[java.time.LocalTime] = new Unscoped[java.time.LocalTime] {}

  /** Marks `java.time.LocalDateTime` values as safe to leave a scope. */
  given Unscoped[java.time.LocalDateTime] = new Unscoped[java.time.LocalDateTime] {}

  /** Marks `java.time.ZonedDateTime` values as safe to leave a scope. */
  given Unscoped[java.time.ZonedDateTime] = new Unscoped[java.time.ZonedDateTime] {}

  /** Marks `java.time.OffsetDateTime` values as safe to leave a scope. */
  given Unscoped[java.time.OffsetDateTime] = new Unscoped[java.time.OffsetDateTime] {}

  /** Marks `java.time.Duration` values as safe to leave a scope. */
  given unscopedJavaDuration: Unscoped[java.time.Duration] = new Unscoped[java.time.Duration] {}

  /** Marks `java.time.Period` values as safe to leave a scope. */
  given Unscoped[java.time.Period] = new Unscoped[java.time.Period] {}

  /** Marks `java.time.ZoneId` values as safe to leave a scope. */
  given Unscoped[java.time.ZoneId] = new Unscoped[java.time.ZoneId] {}

  /** Marks `java.time.ZoneOffset` values as safe to leave a scope. */
  given Unscoped[java.time.ZoneOffset] = new Unscoped[java.time.ZoneOffset] {}

  // Common Java types
  /** Marks `java.util.UUID` values as safe to leave a scope. */
  given Unscoped[java.util.UUID] = new Unscoped[java.util.UUID] {}

  // Scala duration
  /**
   * Marks `scala.concurrent.duration.Duration` values as safe to leave a scope.
   */
  given unscopedScalaDuration: Unscoped[scala.concurrent.duration.Duration] =
    new Unscoped[scala.concurrent.duration.Duration] {}

  /**
   * Marks `scala.concurrent.duration.FiniteDuration` values as safe to leave a
   * scope.
   */
  given Unscoped[scala.concurrent.duration.FiniteDuration] =
    new Unscoped[scala.concurrent.duration.FiniteDuration] {}

  // zio-blocks types
  /**
   * Marks `zio.blocks.chunk.Chunk[A]` as safe to leave a scope when its element
   * type `A` is.
   */
  given [A: Unscoped]: Unscoped[zio.blocks.chunk.Chunk[A]] = new Unscoped[zio.blocks.chunk.Chunk[A]] {}

}
