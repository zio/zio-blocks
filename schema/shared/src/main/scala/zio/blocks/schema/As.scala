package zio.blocks.schema

/**
 * A type class for bidirectional conversion establishing a partial equivalence
 * between types `A` and `B`.
 *
 * `As[A, B]` provides type-safe bidirectional conversion where conversion can
 * fail in either direction due to runtime validation. It establishes a partial
 * equivalence where both conversions use compatible mapping logic and can
 * round-trip (subject to runtime validation).
 *
 * @example
 *   {{{
 *   case class PersonV1(name: String, age: Int)
 *   case class PersonV2(fullName: String, yearsOld: Int)
 *
 *   val as: As[PersonV1, PersonV2] = As.derived
 *   as.into(PersonV1("Alice", 30))  // => Right(PersonV2("Alice", 30))
 *   as.from(PersonV2("Bob", 25))    // => Right(PersonV1("Bob", 25))
 *   }}}
 */
trait As[A, B] {
  def into(a: A): Either[SchemaError, B]
  def from(b: B): Either[SchemaError, A]
}

object As extends AsVersionSpecific {
  // derived is now available via AsVersionSpecific trait
}
