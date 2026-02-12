package zio.blocks.scope

/**
 * Marker typeclass for types that can escape a scope unscoped.
 *
 * Types with an `Unscoped` instance are considered "safe data" - they don't
 * hold resources and can be freely extracted from a scope without tracking.
 *
 * Primitives, strings, and collections of unscoped types are unscoped by
 * default. Resource types (streams, connections, handles) should NOT have
 * instances.
 *
 * @example
 *   {{{
 *   // Primitives escape freely
 *   val n: Int = scope.use(stream)(_.read())  // Int is Unscoped
 *
 *   // Resources stay scoped
 *   val body: $[InputStream] = scope.use(request)(_.body)  // InputStream stays scoped
 *   }}}
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

  // zio-blocks Chunk
  implicit def unscopedChunk[A: Unscoped]: Unscoped[zio.blocks.chunk.Chunk[A]] =
    new Unscoped[zio.blocks.chunk.Chunk[A]] {}
}
