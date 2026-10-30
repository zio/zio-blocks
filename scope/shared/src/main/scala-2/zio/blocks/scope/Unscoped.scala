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
 *   - Tuples: up to 4-tuples (when elements are `Unscoped`)
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
 * 2, define an instance with `Unscoped.derived`, as below (this also compiles
 * on Scala 3). On Scala 3 the automatic instance is also given to a case class
 * that extends `AutoCloseable`, so a closed resource can leave its scope (a
 * known bug). Do not define instances for types that hold resources.
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
  // Nothing - can always escape (never actually returned)
  // Must be low priority since Nothing <: A for all A
  implicit val unscopedNothing: Unscoped[Nothing] = new Unscoped[Nothing] {}
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
  implicit val unscopedInt: Unscoped[Int]               = new Unscoped[Int] {}
  implicit val unscopedLong: Unscoped[Long]             = new Unscoped[Long] {}
  implicit val unscopedShort: Unscoped[Short]           = new Unscoped[Short] {}
  implicit val unscopedByte: Unscoped[Byte]             = new Unscoped[Byte] {}
  implicit val unscopedChar: Unscoped[Char]             = new Unscoped[Char] {}
  implicit val unscopedBoolean: Unscoped[Boolean]       = new Unscoped[Boolean] {}
  implicit val unscopedFloat: Unscoped[Float]           = new Unscoped[Float] {}
  implicit val unscopedDouble: Unscoped[Double]         = new Unscoped[Double] {}
  implicit val unscopedUnit: Unscoped[Unit]             = new Unscoped[Unit] {}
  implicit val unscopedString: Unscoped[String]         = new Unscoped[String] {}
  implicit val unscopedBigInt: Unscoped[BigInt]         = new Unscoped[BigInt] {}
  implicit val unscopedBigDecimal: Unscoped[BigDecimal] = new Unscoped[BigDecimal] {}

  // Collections of unscoped elements
  implicit def unscopedArray[A: Unscoped]: Unscoped[Array[A]]           = new Unscoped[Array[A]] {}
  implicit def unscopedList[A: Unscoped]: Unscoped[List[A]]             = new Unscoped[List[A]] {}
  implicit def unscopedVector[A: Unscoped]: Unscoped[Vector[A]]         = new Unscoped[Vector[A]] {}
  implicit def unscopedSet[A: Unscoped]: Unscoped[Set[A]]               = new Unscoped[Set[A]] {}
  implicit def unscopedOption[A: Unscoped]: Unscoped[Option[A]]         = new Unscoped[Option[A]] {}
  implicit def unscopedSeq[A: Unscoped]: Unscoped[Seq[A]]               = new Unscoped[Seq[A]] {}
  implicit def unscopedIndexedSeq[A: Unscoped]: Unscoped[IndexedSeq[A]] = new Unscoped[IndexedSeq[A]] {}
  implicit def unscopedIterable[A: Unscoped]: Unscoped[Iterable[A]]     = new Unscoped[Iterable[A]] {}

  // Either
  implicit def unscopedEither[A: Unscoped, B: Unscoped]: Unscoped[Either[A, B]] = new Unscoped[Either[A, B]] {}

  // Tuples of unscoped elements
  implicit def unscopedTuple2[A: Unscoped, B: Unscoped]: Unscoped[(A, B)] =
    new Unscoped[(A, B)] {}
  implicit def unscopedTuple3[A: Unscoped, B: Unscoped, C: Unscoped]: Unscoped[(A, B, C)] =
    new Unscoped[(A, B, C)] {}
  implicit def unscopedTuple4[A: Unscoped, B: Unscoped, C: Unscoped, D: Unscoped]: Unscoped[(A, B, C, D)] =
    new Unscoped[(A, B, C, D)] {}

  // Maps with unscoped keys and values
  implicit def unscopedMap[K: Unscoped, V: Unscoped]: Unscoped[Map[K, V]] = new Unscoped[Map[K, V]] {}

  // Java time types (immutable value types)
  implicit val unscopedInstant: Unscoped[java.time.Instant]               = new Unscoped[java.time.Instant] {}
  implicit val unscopedLocalDate: Unscoped[java.time.LocalDate]           = new Unscoped[java.time.LocalDate] {}
  implicit val unscopedLocalTime: Unscoped[java.time.LocalTime]           = new Unscoped[java.time.LocalTime] {}
  implicit val unscopedLocalDateTime: Unscoped[java.time.LocalDateTime]   = new Unscoped[java.time.LocalDateTime] {}
  implicit val unscopedZonedDateTime: Unscoped[java.time.ZonedDateTime]   = new Unscoped[java.time.ZonedDateTime] {}
  implicit val unscopedOffsetDateTime: Unscoped[java.time.OffsetDateTime] =
    new Unscoped[java.time.OffsetDateTime] {}
  implicit val unscopedJavaDuration: Unscoped[java.time.Duration] = new Unscoped[java.time.Duration] {}
  implicit val unscopedPeriod: Unscoped[java.time.Period]         = new Unscoped[java.time.Period] {}
  implicit val unscopedZoneId: Unscoped[java.time.ZoneId]         = new Unscoped[java.time.ZoneId] {}
  implicit val unscopedZoneOffset: Unscoped[java.time.ZoneOffset] = new Unscoped[java.time.ZoneOffset] {}

  // Common Java types
  implicit val unscopedUUID: Unscoped[java.util.UUID] = new Unscoped[java.util.UUID] {}

  // Scala duration
  implicit val unscopedScalaDuration: Unscoped[scala.concurrent.duration.Duration] =
    new Unscoped[scala.concurrent.duration.Duration] {}
  implicit val unscopedFiniteDuration: Unscoped[scala.concurrent.duration.FiniteDuration] =
    new Unscoped[scala.concurrent.duration.FiniteDuration] {}

  // zio-blocks types
  implicit def unscopedChunk[A: Unscoped]: Unscoped[zio.blocks.chunk.Chunk[A]] =
    new Unscoped[zio.blocks.chunk.Chunk[A]] {}

}
