package zio.blocks.schema.convert

import zio.blocks.schema.{Schema, SchemaError}

/**
 * A type class representing a one-way conversion from type A to type B.
 *
 * `Into[A, B]` enables schema-driven data transformation where the source type
 * A can be converted to the target type B with runtime validation.
 *
 * Use cases include:
 *   - Schema migration between different versions of a type
 *   - Data transformation between structurally similar types
 *   - Converting external data formats to internal representations
 *
 * @tparam A
 *   The source type to convert from
 * @tparam B
 *   The target type to convert to
 */
trait Into[-A, +B] {

  /**
   * Converts a value of type A to type B.
   *
   * @param input
   *   The source value to convert
   * @return
   *   Either a SchemaError if the conversion fails, or the converted value
   */
  def into(input: A): Either[SchemaError, B]
}

object Into extends IntoLowPriority {

  /**
   * Summons an implicit Into instance for types A and B.
   */
  def apply[A, B](implicit instance: Into[A, B]): Into[A, B] = instance

  /**
   * Creates an Into instance from a function.
   */
  def fromFunction[A, B](f: A => Either[SchemaError, B]): Into[A, B] =
    new Into[A, B] {
      def into(input: A): Either[SchemaError, B] = f(input)
    }

  /**
   * Creates an Into instance from a total function (always succeeds).
   */
  def fromTotal[A, B](f: A => B): Into[A, B] =
    new Into[A, B] {
      def into(input: A): Either[SchemaError, B] = Right(f(input))
    }

  /**
   * Identity conversion - every type can be converted to itself.
   */
  implicit def identity[A]: Into[A, A] = new Into[A, A] {
    def into(input: A): Either[SchemaError, A] = Right(input)
  }

  // Primitive widening conversions (lossless)
  implicit val byteToShort: Into[Byte, Short]   = fromTotal(_.toShort)
  implicit val byteToInt: Into[Byte, Int]       = fromTotal(_.toInt)
  implicit val byteToLong: Into[Byte, Long]     = fromTotal(_.toLong)
  implicit val byteToFloat: Into[Byte, Float]   = fromTotal(_.toFloat)
  implicit val byteToDouble: Into[Byte, Double] = fromTotal(_.toDouble)

  implicit val shortToInt: Into[Short, Int]       = fromTotal(_.toInt)
  implicit val shortToLong: Into[Short, Long]     = fromTotal(_.toLong)
  implicit val shortToFloat: Into[Short, Float]   = fromTotal(_.toFloat)
  implicit val shortToDouble: Into[Short, Double] = fromTotal(_.toDouble)

  implicit val intToLong: Into[Int, Long]     = fromTotal(_.toLong)
  implicit val intToDouble: Into[Int, Double] = fromTotal(_.toDouble)

  implicit val longToFloat: Into[Long, Float]   = fromTotal(_.toFloat)
  implicit val longToDouble: Into[Long, Double] = fromTotal(_.toDouble)

  implicit val floatToDouble: Into[Float, Double] = fromTotal(_.toDouble)

  implicit val charToInt: Into[Char, Int]   = fromTotal(_.toInt)
  implicit val charToLong: Into[Char, Long] = fromTotal(_.toLong)

  // Primitive narrowing conversions (with validation)
  implicit val longToInt: Into[Long, Int] = fromFunction { value =>
    if (value >= Int.MinValue && value <= Int.MaxValue)
      Right(value.toInt)
    else
      Left(SchemaError.expectationMismatch(Nil, s"Value $value cannot be narrowed to Int"))
  }

  implicit val longToShort: Into[Long, Short] = fromFunction { value =>
    if (value >= Short.MinValue && value <= Short.MaxValue)
      Right(value.toShort)
    else
      Left(SchemaError.expectationMismatch(Nil, s"Value $value cannot be narrowed to Short"))
  }

  implicit val longToByte: Into[Long, Byte] = fromFunction { value =>
    if (value >= Byte.MinValue && value <= Byte.MaxValue)
      Right(value.toByte)
    else
      Left(SchemaError.expectationMismatch(Nil, s"Value $value cannot be narrowed to Byte"))
  }

  implicit val intToShort: Into[Int, Short] = fromFunction { value =>
    if (value >= Short.MinValue && value <= Short.MaxValue)
      Right(value.toShort)
    else
      Left(SchemaError.expectationMismatch(Nil, s"Value $value cannot be narrowed to Short"))
  }

  implicit val intToByte: Into[Int, Byte] = fromFunction { value =>
    if (value >= Byte.MinValue && value <= Byte.MaxValue)
      Right(value.toByte)
    else
      Left(SchemaError.expectationMismatch(Nil, s"Value $value cannot be narrowed to Byte"))
  }

  implicit val shortToByte: Into[Short, Byte] = fromFunction { value =>
    if (value >= Byte.MinValue && value <= Byte.MaxValue)
      Right(value.toByte)
    else
      Left(SchemaError.expectationMismatch(Nil, s"Value $value cannot be narrowed to Byte"))
  }

  implicit val doubleToFloat: Into[Double, Float] = fromFunction { value =>
    if (value >= -Float.MaxValue && value <= Float.MaxValue)
      Right(value.toFloat)
    else
      Left(SchemaError.expectationMismatch(Nil, s"Value $value cannot be narrowed to Float"))
  }

  implicit val doubleToLong: Into[Double, Long] = fromFunction { value =>
    val longValue = value.toLong
    if (value >= Long.MinValue && value <= Long.MaxValue && value == longValue.toDouble)
      Right(longValue)
    else
      Left(SchemaError.expectationMismatch(Nil, s"Value $value cannot be converted to Long without loss"))
  }

  implicit val doubleToInt: Into[Double, Int] = fromFunction { value =>
    val intValue = value.toInt
    if (value >= Int.MinValue && value <= Int.MaxValue && value == intValue.toDouble)
      Right(intValue)
    else
      Left(SchemaError.expectationMismatch(Nil, s"Value $value cannot be converted to Int without loss"))
  }

  // Option conversions
  implicit def optionInto[A, B](implicit into: Into[A, B]): Into[Option[A], Option[B]] =
    fromFunction {
      case Some(a) => into.into(a).map(Some(_))
      case None    => Right(None)
    }

  // Collection conversions
  implicit def listInto[A, B](implicit into: Into[A, B]): Into[List[A], List[B]] =
    fromFunction { list =>
      list.foldRight[Either[SchemaError, List[B]]](Right(Nil)) { (a, acc) =>
        for {
          tail <- acc
          b    <- into.into(a)
        } yield b :: tail
      }
    }

  implicit def vectorInto[A, B](implicit into: Into[A, B]): Into[Vector[A], Vector[B]] =
    fromFunction { vector =>
      vector.foldRight[Either[SchemaError, Vector[B]]](Right(Vector.empty)) { (a, acc) =>
        for {
          tail <- acc
          b    <- into.into(a)
        } yield b +: tail
      }
    }

  implicit def setInto[A, B](implicit into: Into[A, B]): Into[Set[A], Set[B]] =
    fromFunction { set =>
      set.foldLeft[Either[SchemaError, Set[B]]](Right(Set.empty)) { (acc, a) =>
        for {
          current <- acc
          b       <- into.into(a)
        } yield current + b
      }
    }

  // List <-> Vector conversions
  implicit def listToVector[A]: Into[List[A], Vector[A]] = fromTotal(_.toVector)
  implicit def vectorToList[A]: Into[Vector[A], List[A]] = fromTotal(_.toList)
  implicit def listToSet[A]: Into[List[A], Set[A]]       = fromTotal(_.toSet)
  implicit def vectorToSet[A]: Into[Vector[A], Set[A]]   = fromTotal(_.toSet)
  implicit def setToList[A]: Into[Set[A], List[A]]       = fromTotal(_.toList)
  implicit def setToVector[A]: Into[Set[A], Vector[A]]   = fromTotal(_.toVector)
}

/**
 * Low priority implicits for Into. These provide fallback derivation using
 * Schema.
 */
trait IntoLowPriority {

  /**
   * Derives an Into instance using Schema-based conversion via DynamicValue.
   * This is a fallback for types that have Schema instances but no direct
   * conversion.
   */
  implicit def fromSchemas[A, B](implicit schemaA: Schema[A], schemaB: Schema[B]): Into[A, B] =
    new Into[A, B] {
      def into(input: A): Either[SchemaError, B] = {
        val dynamicValue = schemaA.toDynamicValue(input)
        schemaB.fromDynamicValue(dynamicValue)
      }
    }
}
