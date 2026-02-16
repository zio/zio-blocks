package zio.blocks.scope

/**
 * Marker typeclass for types that can escape a scope unscoped.
 *
 * Types with an `Unscoped` instance are considered "safe data" - they don't
 * hold resources and can be freely extracted from a scope without tracking.
 *
 * ==Default Instances==
 *
 * The following types have `Unscoped` instances by default:
 *   - Primitives: `Int`, `Long`, `Short`, `Byte`, `Char`, `Boolean`, `Float`,
 *     `Double`, `Unit`
 *   - Text: `String`
 *   - Numeric: `BigInt`, `BigDecimal`
 *   - Collections: `Array`, `List`, `Vector`, `Set`, `Seq`, `IndexedSeq`,
 *     `Iterable`, `Map` (when elements are `Unscoped`)
 *   - Containers: `Option`, `Either` (when elements are `Unscoped`)
 *   - Tuples: up to 4-tuples (when elements are `Unscoped`)
 *   - Time: `java.time.*` value types, `scala.concurrent.duration.*`
 *   - Other: `java.util.UUID`, [[zio.blocks.chunk.Chunk]]
 *
 * ==Resource Types==
 *
 * Types representing resources (streams, connections, handles, etc.) should NOT
 * have `Unscoped` instances. This ensures they remain tracked by the scope
 * system and cannot accidentally escape.
 *
 * @example
 *   {{{
 *   // Primitives escape freely
 *   Scope.global.scoped { s =>
 *     import s._
 *     use(stream)(_.read()) // returns s.$[Int], unwrapped to Int at the boundary
 *   }
 *
 *   // Resources stay scoped
 *   val body: $[InputStream] = scope.use(request)(_.body)  // InputStream stays scoped
 *   }}}
 *
 * @tparam A
 *   the type that can escape scopes
 */
trait Unscoped[A]

/**
 * Low-priority Unscoped instances to avoid ambiguity.
 */
private[scope] trait UnscopedLowPriority {
  // Nothing - can always escape (never actually returned)
  // Must be low priority since Nothing <: A for all A
  given given_Unscoped_Nothing: Unscoped[Nothing] = new Unscoped[Nothing] {}
}

/**
 * Companion object providing given instances for common types.
 *
 * ==Adding Custom Instances==
 *
 * To mark your own type as safe to escape scopes, define a given instance:
 *
 * {{{
 * case class UserId(value: Long)
 * object UserId {
 *   given Unscoped[UserId] = new Unscoped[UserId] {}
 * }
 * }}}
 *
 * Only add `Unscoped` instances for pure data types that don't hold resources.
 * Resource types (streams, connections, handles) should NOT have instances.
 */
object Unscoped extends UnscopedVersionSpecific with UnscopedLowPriority {
  // Primitives
  given Unscoped[Int]        = new Unscoped[Int] {}
  given Unscoped[Long]       = new Unscoped[Long] {}
  given Unscoped[Short]      = new Unscoped[Short] {}
  given Unscoped[Byte]       = new Unscoped[Byte] {}
  given Unscoped[Char]       = new Unscoped[Char] {}
  given Unscoped[Boolean]    = new Unscoped[Boolean] {}
  given Unscoped[Float]      = new Unscoped[Float] {}
  given Unscoped[Double]     = new Unscoped[Double] {}
  given Unscoped[Unit]       = new Unscoped[Unit] {}
  given Unscoped[String]     = new Unscoped[String] {}
  given Unscoped[BigInt]     = new Unscoped[BigInt] {}
  given Unscoped[BigDecimal] = new Unscoped[BigDecimal] {}

  // Collections of unscoped elements
  given [A: Unscoped]: Unscoped[Array[A]]      = new Unscoped[Array[A]] {}
  given [A: Unscoped]: Unscoped[List[A]]       = new Unscoped[List[A]] {}
  given [A: Unscoped]: Unscoped[Vector[A]]     = new Unscoped[Vector[A]] {}
  given [A: Unscoped]: Unscoped[Set[A]]        = new Unscoped[Set[A]] {}
  given [A: Unscoped]: Unscoped[Option[A]]     = new Unscoped[Option[A]] {}
  given [A: Unscoped]: Unscoped[Seq[A]]        = new Unscoped[Seq[A]] {}
  given [A: Unscoped]: Unscoped[IndexedSeq[A]] = new Unscoped[IndexedSeq[A]] {}
  given [A: Unscoped]: Unscoped[Iterable[A]]   = new Unscoped[Iterable[A]] {}

  // Either
  given [A: Unscoped, B: Unscoped]: Unscoped[Either[A, B]] = new Unscoped[Either[A, B]] {}

  // Tuples of unscoped elements
  given [A: Unscoped, B: Unscoped]: Unscoped[(A, B)]                                 = new Unscoped[(A, B)] {}
  given [A: Unscoped, B: Unscoped, C: Unscoped]: Unscoped[(A, B, C)]                 = new Unscoped[(A, B, C)] {}
  given [A: Unscoped, B: Unscoped, C: Unscoped, D: Unscoped]: Unscoped[(A, B, C, D)] =
    new Unscoped[(A, B, C, D)] {}

  // Maps with unscoped keys and values
  given [K: Unscoped, V: Unscoped]: Unscoped[Map[K, V]] = new Unscoped[Map[K, V]] {}

  // Java time types (immutable value types)
  given Unscoped[java.time.Instant]                        = new Unscoped[java.time.Instant] {}
  given Unscoped[java.time.LocalDate]                      = new Unscoped[java.time.LocalDate] {}
  given Unscoped[java.time.LocalTime]                      = new Unscoped[java.time.LocalTime] {}
  given Unscoped[java.time.LocalDateTime]                  = new Unscoped[java.time.LocalDateTime] {}
  given Unscoped[java.time.ZonedDateTime]                  = new Unscoped[java.time.ZonedDateTime] {}
  given Unscoped[java.time.OffsetDateTime]                 = new Unscoped[java.time.OffsetDateTime] {}
  given unscopedJavaDuration: Unscoped[java.time.Duration] = new Unscoped[java.time.Duration] {}
  given Unscoped[java.time.Period]                         = new Unscoped[java.time.Period] {}
  given Unscoped[java.time.ZoneId]                         = new Unscoped[java.time.ZoneId] {}
  given Unscoped[java.time.ZoneOffset]                     = new Unscoped[java.time.ZoneOffset] {}

  // Common Java types
  given Unscoped[java.util.UUID] = new Unscoped[java.util.UUID] {}

  // Scala duration
  given unscopedScalaDuration: Unscoped[scala.concurrent.duration.Duration] =
    new Unscoped[scala.concurrent.duration.Duration] {}
  given Unscoped[scala.concurrent.duration.FiniteDuration] =
    new Unscoped[scala.concurrent.duration.FiniteDuration] {}

  // zio-blocks types
  given [A: Unscoped]: Unscoped[zio.blocks.chunk.Chunk[A]] = new Unscoped[zio.blocks.chunk.Chunk[A]] {}

  // Resource descriptions (lazy, not live resources)
  given [A]: Unscoped[Resource[A]] = new Unscoped[Resource[A]] {}
}
