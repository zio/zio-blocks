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

object As extends IntoAsVersionSpecific {

  /**
   * Derives an `As[A, B]` instance using macro-based automatic derivation.
   *
   * The macro verifies bidirectional compatibility and generates both
   * conversion directions with consistent field mapping logic.
   *
   * ==Implementation==
   *
   * `As[A, B]` is implemented as a composition of two `Into` instances:
   *   - `Into[A, B]` for forward conversion (`into`)
   *   - `Into[B, A]` for reverse conversion (`from`)
   *
   * This ensures symmetric field mapping and maintains cross-platform
   * compatibility.
   *
   * ==Round-Trip Guarantees==
   *
   * `As` represents a "partial equivalence", not a perfect isomorphism.
   * Round-trip conversions may lose information in some cases:
   *   - Collection conversions: `List ↔ Set` loses order/duplicates
   *   - Numeric narrowing: `Long → Int → Long` may fail if value exceeds `Int`
   *     range
   *
   * These lossy conversions are acceptable and documented. Use `Into` for
   * unidirectional conversions when round-trip is not required.
   *
   * @example
   *   {{{case class PersonV1(name: String, age: Int) case class PersonV2(name:
   *   String, age: Int) val as = As.derived[PersonV1, PersonV2]
   *   as.into(PersonV1("Alice", 30)) // => Right(PersonV2("Alice", 30))
   *   as.from(PersonV2("Bob", 25)) // => Right(PersonV1("Bob", 25))}}}
   */
  inline def derived[A, B]: As[A, B] = ${ IntoAsVersionSpecificImpl.derivedAsImpl[A, B] }
}
