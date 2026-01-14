package zio.blocks.schema.convert

import zio.blocks.schema.{Schema, SchemaError}

/**
 * A type class representing a bidirectional conversion between types A and B.
 *
 * `As[A, B]` extends `Into[A, B]` with the additional guarantee that the
 * conversion is reversible - values can be converted from A to B and back to A
 * without loss of information (round-trip guarantee).
 *
 * Use cases include:
 *   - Isomorphic type conversions (e.g., case class to tuple)
 *   - Lossless format transformations
 *   - Schema-driven bidirectional mapping between equivalent types
 *
 * @tparam A
 *   The first type in the bidirectional conversion
 * @tparam B
 *   The second type in the bidirectional conversion
 */
trait As[A, B] extends Into[A, B] {

  /**
   * Converts a value of type B back to type A.
   *
   * @param input
   *   The value to convert back
   * @return
   *   Either a SchemaError if the conversion fails, or the converted value
   */
  def from(input: B): Either[SchemaError, A]

  /**
   * Returns the reverse conversion from B to A.
   */
  def reverse: As[B, A] = As.reverse(this)
}

object As extends AsLowPriority {

  /**
   * Summons an implicit As instance for types A and B.
   */
  def apply[A, B](implicit instance: As[A, B]): As[A, B] = instance

  /**
   * Creates an As instance from two functions.
   */
  def fromFunctions[A, B](
    forward: A => Either[SchemaError, B],
    backward: B => Either[SchemaError, A]
  ): As[A, B] =
    new As[A, B] {
      def into(input: A): Either[SchemaError, B] = forward(input)
      def from(input: B): Either[SchemaError, A] = backward(input)
    }

  /**
   * Creates an As instance from two total functions (always succeed).
   */
  def fromTotal[A, B](forward: A => B, backward: B => A): As[A, B] =
    new As[A, B] {
      def into(input: A): Either[SchemaError, B] = Right(forward(input))
      def from(input: B): Either[SchemaError, A] = Right(backward(input))
    }

  /**
   * Creates an As instance from an Into in both directions.
   */
  def fromIntos[A, B](intoAB: Into[A, B], intoBA: Into[B, A]): As[A, B] =
    new As[A, B] {
      def into(input: A): Either[SchemaError, B] = intoAB.into(input)
      def from(input: B): Either[SchemaError, A] = intoBA.into(input)
    }

  /**
   * Creates the reverse of an As instance.
   */
  private[convert] def reverse[A, B](as: As[A, B]): As[B, A] =
    new As[B, A] {
      def into(input: B): Either[SchemaError, A] = as.from(input)
      def from(input: A): Either[SchemaError, B] = as.into(input)
    }

  /**
   * Identity conversion - every type is bidirectionally convertible to itself.
   */
  implicit def identity[A]: As[A, A] = new As[A, A] {
    def into(input: A): Either[SchemaError, A] = Right(input)
    def from(input: A): Either[SchemaError, A] = Right(input)
  }

  // Symmetric primitive conversions (where both directions are valid with validation)
  implicit val byteShort: As[Byte, Short] = fromFunctions(
    b => Right(b.toShort),
    s =>
      if (s >= Byte.MinValue && s <= Byte.MaxValue) Right(s.toByte)
      else Left(SchemaError.expectationMismatch(Nil, s"Value $s cannot be narrowed to Byte"))
  )

  implicit val byteInt: As[Byte, Int] = fromFunctions(
    b => Right(b.toInt),
    i =>
      if (i >= Byte.MinValue && i <= Byte.MaxValue) Right(i.toByte)
      else Left(SchemaError.expectationMismatch(Nil, s"Value $i cannot be narrowed to Byte"))
  )

  implicit val byteLong: As[Byte, Long] = fromFunctions(
    b => Right(b.toLong),
    l =>
      if (l >= Byte.MinValue && l <= Byte.MaxValue) Right(l.toByte)
      else Left(SchemaError.expectationMismatch(Nil, s"Value $l cannot be narrowed to Byte"))
  )

  implicit val shortInt: As[Short, Int] = fromFunctions(
    s => Right(s.toInt),
    i =>
      if (i >= Short.MinValue && i <= Short.MaxValue) Right(i.toShort)
      else Left(SchemaError.expectationMismatch(Nil, s"Value $i cannot be narrowed to Short"))
  )

  implicit val shortLong: As[Short, Long] = fromFunctions(
    s => Right(s.toLong),
    l =>
      if (l >= Short.MinValue && l <= Short.MaxValue) Right(l.toShort)
      else Left(SchemaError.expectationMismatch(Nil, s"Value $l cannot be narrowed to Short"))
  )

  implicit val intLong: As[Int, Long] = fromFunctions(
    i => Right(i.toLong),
    l =>
      if (l >= Int.MinValue && l <= Int.MaxValue) Right(l.toInt)
      else Left(SchemaError.expectationMismatch(Nil, s"Value $l cannot be narrowed to Int"))
  )

  implicit val floatDouble: As[Float, Double] = fromFunctions(
    f => Right(f.toDouble),
    d =>
      if (d >= -Float.MaxValue && d <= Float.MaxValue) Right(d.toFloat)
      else Left(SchemaError.expectationMismatch(Nil, s"Value $d cannot be narrowed to Float"))
  )

  implicit val charInt: As[Char, Int] = fromFunctions(
    c => Right(c.toInt),
    i =>
      if (i >= Char.MinValue.toInt && i <= Char.MaxValue.toInt) Right(i.toChar)
      else Left(SchemaError.expectationMismatch(Nil, s"Value $i cannot be converted to Char"))
  )

  // Option conversions
  implicit def optionAs[A, B](implicit as: As[A, B]): As[Option[A], Option[B]] =
    fromFunctions(
      {
        case Some(a) => as.into(a).map(Some(_))
        case None    => Right(None)
      },
      {
        case Some(b) => as.from(b).map(Some(_))
        case None    => Right(None)
      }
    )

  // Collection conversions with element conversion
  implicit def listAs[A, B](implicit as: As[A, B]): As[List[A], List[B]] =
    fromFunctions(
      list =>
        list.foldRight[Either[SchemaError, List[B]]](Right(Nil)) { (a, acc) =>
          for {
            tail <- acc
            b    <- as.into(a)
          } yield b :: tail
        },
      list =>
        list.foldRight[Either[SchemaError, List[A]]](Right(Nil)) { (b, acc) =>
          for {
            tail <- acc
            a    <- as.from(b)
          } yield a :: tail
        }
    )

  implicit def vectorAs[A, B](implicit as: As[A, B]): As[Vector[A], Vector[B]] =
    fromFunctions(
      vector =>
        vector.foldRight[Either[SchemaError, Vector[B]]](Right(Vector.empty)) { (a, acc) =>
          for {
            tail <- acc
            b    <- as.into(a)
          } yield b +: tail
        },
      vector =>
        vector.foldRight[Either[SchemaError, Vector[A]]](Right(Vector.empty)) { (b, acc) =>
          for {
            tail <- acc
            a    <- as.from(b)
          } yield a +: tail
        }
    )

  // List <-> Vector is always bidirectional (no data loss)
  implicit def listVectorAs[A]: As[List[A], Vector[A]] =
    fromTotal(_.toVector, _.toList)

  // String <-> List[Char] is always bidirectional
  implicit val stringCharList: As[String, List[Char]] =
    fromTotal(_.toList, _.mkString)

  implicit val stringCharVector: As[String, Vector[Char]] =
    fromTotal(_.toVector, _.mkString)
}

/**
 * Low priority implicits for As. These provide fallback derivation using
 * Schema.
 */
trait AsLowPriority {

  /**
   * Derives an As instance using Schema-based conversion via DynamicValue. This
   * is a fallback for types that have Schema instances but no direct
   * conversion.
   *
   * Note: This creates a bidirectional conversion, but round-trip guarantees
   * depend on the schema compatibility. Use with care.
   */
  implicit def fromSchemas[A, B](implicit schemaA: Schema[A], schemaB: Schema[B]): As[A, B] =
    new As[A, B] {
      def into(input: A): Either[SchemaError, B] = {
        val dynamicValue = schemaA.toDynamicValue(input)
        schemaB.fromDynamicValue(dynamicValue)
      }

      def from(input: B): Either[SchemaError, A] = {
        val dynamicValue = schemaB.toDynamicValue(input)
        schemaA.fromDynamicValue(dynamicValue)
      }
    }
}
