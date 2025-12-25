package zio.blocks.schema

/**
 * A type class for one-way conversion from type `A` to type `B` with runtime
 * validation.
 *
 * `Into[A, B]` provides a type-safe way to convert values from source type `A`
 * to target type `B`, potentially failing at runtime when validation
 * constraints cannot be satisfied.
 *
 * @example
 *   {{{
 *   case class PersonV1(name: String, age: Int)
 *   case class PersonV2(fullName: String, age: Int)
 *
 *   val into: Into[PersonV1, PersonV2] = Into.derived
 *   into.into(PersonV1("Alice", 30)) // => Right(PersonV2("Alice", 30))
 *   }}}
 */
trait Into[-A, +B] {
  def into(a: A): Either[SchemaError, B]
}

object Into extends IntoAsVersionSpecific {
  // derived is defined in version-specific files:
  // - scala-2: IntoVersionSpecific.scala (macro)
  // - scala-3: IntoVersionSpecific.scala (inline macro)

  /**
   * Derives an `Into[A, B]` instance using macro-based automatic derivation.
   *
   * The macro maps fields by name, position, or type, with
   * support for validation, coercion, and schema evolution patterns.
   *
   * ==Supported Conversions==
   *
   * ===Products (Case Classes)===
   *   - Field mapping by name (supports schema evolution with renamed fields)
   *   - Recursive field conversion
   *   - Type coercion (primitives, collections, nested structures)
   *
   * ===Collections===
   *   - Container conversion: `List[A] → Vector[A]`, `Seq[A] → Set[A]`, etc.
   *   - Element conversion: `List[Int] → List[Long]` (with primitive
   *     widening/narrowing)
   *   - Combined: `List[Int] → Vector[Long]` (both container and element
   *     conversion)
   *   - Note: Some conversions are lossy (e.g., `List ↔ Set` loses
   *     order/duplicates)
   *
   * ===Coproducts (Sealed Traits / Enums)===
   *   - Subtype matching by exact name (e.g., `Status.Active → State.Active`)
   *   - Recursive conversion for case class subtypes
   *   - Singleton support (enum values, case objects)
   *   - Limitation: Subtypes must have matching names. Different names (e.g.,
   *     `RedColor` vs `RedHue`) will fail at compile-time. Workaround: rename
   *     subtypes or use wrapper types.
   *
   * ===Primitives===
   *   - Widening: `Int → Long`, `Int → Double`, `Float → Double` (always
   *     succeeds)
   *   - Narrowing: `Long → Int`, `Double → Float` (with runtime validation for
   *     overflow)
   *
   * @example
   *   {{{case class PersonV1(name: String, age: Int) case class PersonV2(name:
   *   String, age: Int) val into = Into.derived[PersonV1, PersonV2]}}}
   */
  /**
   * Identity conversion: converts a value to itself.
   */
  implicit def identity[A]: Into[A, A] = new Into[A, A] {
    def into(a: A): Either[SchemaError, A] = Right(a)
  }

  /**
   * Primitive conversions: safe widening conversions (always succeed).
   */
  implicit val intToLong: Into[Int, Long] = new Into[Int, Long] {
    def into(a: Int): Either[SchemaError, Long] = Right(a.toLong)
  }

  implicit val intToDouble: Into[Int, Double] = new Into[Int, Double] {
    def into(a: Int): Either[SchemaError, Double] = Right(a.toDouble)
  }

  implicit val intToFloat: Into[Int, Float] = new Into[Int, Float] {
    def into(a: Int): Either[SchemaError, Float] = Right(a.toFloat)
  }

  implicit val longToDouble: Into[Long, Double] = new Into[Long, Double] {
    def into(a: Long): Either[SchemaError, Double] = Right(a.toDouble)
  }

  implicit val floatToDouble: Into[Float, Double] = new Into[Float, Double] {
    def into(a: Float): Either[SchemaError, Double] = Right(a.toDouble)
  }

  /**
   * Primitive conversions: narrowing conversions (require validation).
   */
  implicit val longToInt: Into[Long, Int] = new Into[Long, Int] {
    def into(a: Long): Either[SchemaError, Int] =
      if (a >= Int.MinValue && a <= Int.MaxValue) {
        Right(a.toInt)
      } else {
        Left(
          SchemaError.expectationMismatch(
            Nil,
            s"Long value $a cannot be safely converted to Int (out of range [${Int.MinValue}, ${Int.MaxValue}])"
          )
        )
      }
  }

  implicit val doubleToFloat: Into[Double, Float] = new Into[Double, Float] {
    def into(a: Double): Either[SchemaError, Float] = {
      val floatValue = a.toFloat
      // Check if conversion is lossless (within Float range)
      if (floatValue.isInfinite && !a.isInfinite) {
        Left(SchemaError.expectationMismatch(Nil, s"Double value $a is too large for Float"))
      } else {
        Right(floatValue)
      }
    }
  }

  implicit val doubleToLong: Into[Double, Long] = new Into[Double, Long] {
    def into(a: Double): Either[SchemaError, Long] =
      if (a.isWhole && a >= Long.MinValue && a <= Long.MaxValue) {
        Right(a.toLong)
      } else {
        val reason =
          if (!a.isWhole) s"Double value $a is not a whole number"
          else s"Double value $a is out of range [${Long.MinValue}, ${Long.MaxValue}]"
        Left(SchemaError.expectationMismatch(Nil, s"Cannot convert Double to Long: $reason"))
      }
  }

  implicit val doubleToInt: Into[Double, Int] = new Into[Double, Int] {
    def into(a: Double): Either[SchemaError, Int] =
      if (a.isWhole && a >= Int.MinValue && a <= Int.MaxValue) {
        Right(a.toInt)
      } else {
        val reason =
          if (!a.isWhole) s"Double value $a is not a whole number"
          else s"Double value $a is out of range [${Int.MinValue}, ${Int.MaxValue}]"
        Left(SchemaError.expectationMismatch(Nil, s"Cannot convert Double to Int: $reason"))
      }
  }
}
