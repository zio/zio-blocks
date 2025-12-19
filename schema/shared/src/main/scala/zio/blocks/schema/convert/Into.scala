package zio.blocks.schema.convert

import zio.blocks.schema.SchemaError

import scala.collection.Factory

trait Into[-A, +B] {
  def into(a: A): Either[SchemaError, B]
}

object Into extends IntoVersionSpecific with IntoPrimitiveInstances with IntoContainerInstances {
  def apply[A, B](implicit ev: Into[A, B]): Into[A, B] = ev

  /** Identity instance - any type converts to itself */
  implicit def identity[A]: Into[A, A] = (a: A) => Right(a)
}

trait IntoPrimitiveInstances {

  // === Numeric Widening (Lossless) ===

  implicit val byteToShort: Into[Byte, Short]   = (a: Byte) => Right(a.toShort)
  implicit val byteToInt: Into[Byte, Int]       = (a: Byte) => Right(a.toInt)
  implicit val byteToLong: Into[Byte, Long]     = (a: Byte) => Right(a.toLong)
  implicit val byteToFloat: Into[Byte, Float]   = (a: Byte) => Right(a.toFloat)
  implicit val byteToDouble: Into[Byte, Double] = (a: Byte) => Right(a.toDouble)

  implicit val shortToInt: Into[Short, Int]       = (a: Short) => Right(a.toInt)
  implicit val shortToLong: Into[Short, Long]     = (a: Short) => Right(a.toLong)
  implicit val shortToFloat: Into[Short, Float]   = (a: Short) => Right(a.toFloat)
  implicit val shortToDouble: Into[Short, Double] = (a: Short) => Right(a.toDouble)

  implicit val intToLong: Into[Int, Long]     = (a: Int) => Right(a.toLong)
  implicit val intToFloat: Into[Int, Float]   = (a: Int) => Right(a.toFloat)
  implicit val intToDouble: Into[Int, Double] = (a: Int) => Right(a.toDouble)

  implicit val longToFloat: Into[Long, Float]   = (a: Long) => Right(a.toFloat)
  implicit val longToDouble: Into[Long, Double] = (a: Long) => Right(a.toDouble)

  implicit val floatToDouble: Into[Float, Double] = (a: Float) => Right(a.toDouble)

  // === Numeric Narrowing (with Runtime Validation) ===

  implicit val shortToByte: Into[Short, Byte] = (a: Short) =>
    if (a >= Byte.MinValue && a <= Byte.MaxValue) Right(a.toByte)
    else
      Left(SchemaError.conversionFailed(Nil, s"Value $a is out of range for Byte [${Byte.MinValue}, ${Byte.MaxValue}]"))

  implicit val intToByte: Into[Int, Byte] = (a: Int) =>
    if (a >= Byte.MinValue && a <= Byte.MaxValue) Right(a.toByte)
    else
      Left(SchemaError.conversionFailed(Nil, s"Value $a is out of range for Byte [${Byte.MinValue}, ${Byte.MaxValue}]"))

  implicit val intToShort: Into[Int, Short] = (a: Int) =>
    if (a >= Short.MinValue && a <= Short.MaxValue) Right(a.toShort)
    else
      Left(
        SchemaError.conversionFailed(Nil, s"Value $a is out of range for Short [${Short.MinValue}, ${Short.MaxValue}]")
      )

  implicit val longToByte: Into[Long, Byte] = (a: Long) =>
    if (a >= Byte.MinValue && a <= Byte.MaxValue) Right(a.toByte)
    else
      Left(SchemaError.conversionFailed(Nil, s"Value $a is out of range for Byte [${Byte.MinValue}, ${Byte.MaxValue}]"))

  implicit val longToShort: Into[Long, Short] = (a: Long) =>
    if (a >= Short.MinValue && a <= Short.MaxValue) Right(a.toShort)
    else
      Left(
        SchemaError.conversionFailed(Nil, s"Value $a is out of range for Short [${Short.MinValue}, ${Short.MaxValue}]")
      )

  implicit val longToInt: Into[Long, Int] = (a: Long) =>
    if (a >= Int.MinValue && a <= Int.MaxValue) Right(a.toInt)
    else Left(SchemaError.conversionFailed(Nil, s"Value $a is out of range for Int [${Int.MinValue}, ${Int.MaxValue}]"))

  implicit val doubleToFloat: Into[Double, Float] = (a: Double) =>
    if (a >= -Float.MaxValue && a <= Float.MaxValue) Right(a.toFloat)
    else Left(SchemaError.conversionFailed(Nil, s"Value $a is out of range for Float"))

  implicit val floatToInt: Into[Float, Int] = (a: Float) =>
    if (a >= Int.MinValue && a <= Int.MaxValue && a == a.toInt.toFloat) Right(a.toInt)
    else Left(SchemaError.conversionFailed(Nil, s"Value $a cannot be precisely converted to Int"))

  implicit val floatToLong: Into[Float, Long] = (a: Float) =>
    if (a >= Long.MinValue && a <= Long.MaxValue && a == a.toLong.toFloat) Right(a.toLong)
    else Left(SchemaError.conversionFailed(Nil, s"Value $a cannot be precisely converted to Long"))

  implicit val doubleToInt: Into[Double, Int] = (a: Double) =>
    if (a >= Int.MinValue && a <= Int.MaxValue && a == a.toInt.toDouble) Right(a.toInt)
    else Left(SchemaError.conversionFailed(Nil, s"Value $a cannot be precisely converted to Int"))

  implicit val doubleToLong: Into[Double, Long] = (a: Double) =>
    if (a >= Long.MinValue && a <= Long.MaxValue && a == a.toLong.toDouble) Right(a.toLong)
    else Left(SchemaError.conversionFailed(Nil, s"Value $a cannot be precisely converted to Long"))
}

trait IntoContainerInstances extends IntoContainerInstancesLowPriority {

  // === Option ===

  implicit def optionInto[A, B](implicit into: Into[A, B]): Into[Option[A], Option[B]] = {
    case Some(value) => into.into(value).map(Some(_))
    case None        => Right(None)
  }

  // === Either ===

  implicit def eitherInto[L1, R1, L2, R2](implicit
    leftInto: Into[L1, L2],
    rightInto: Into[R1, R2]
  ): Into[Either[L1, R1], Either[L2, R2]] = {
    case Left(l)  => leftInto.into(l).map(Left(_))
    case Right(r) => rightInto.into(r).map(Right(_))
  }

  // === List ===

  implicit def listInto[A, B](implicit into: Into[A, B]): Into[List[A], List[B]] =
    iterableInto[A, B, List]

  // === Vector ===

  implicit def vectorInto[A, B](implicit into: Into[A, B]): Into[Vector[A], Vector[B]] =
    iterableInto[A, B, Vector]

  // === Set ===

  implicit def setInto[A, B](implicit into: Into[A, B]): Into[Set[A], Set[B]] =
    iterableInto[A, B, Set]

  // === Seq ===

  implicit def seqInto[A, B](implicit into: Into[A, B]): Into[Seq[A], Seq[B]] =
    iterableInto[A, B, Seq]

  // === Map ===

  implicit def mapInto[K1, V1, K2, V2](implicit
    keyInto: Into[K1, K2],
    valueInto: Into[V1, V2]
  ): Into[Map[K1, V1], Map[K2, V2]] = { (a: Map[K1, V1]) =>
    val results = a.toList.map { case (k, v) =>
      for {
        k2 <- keyInto.into(k)
        v2 <- valueInto.into(v)
      } yield (k2, v2)
    }
    sequence(results).map(_.toMap)
  }

  /** Helper to convert Iterable with a Factory */
  protected def iterableInto[A, B, F[_]](implicit
    intoAB: Into[A, B],
    factory: Factory[B, F[B]]
  ): Into[F[A], F[B]] = { (a: F[A]) =>
    val iterable = a.asInstanceOf[Iterable[A]]
    val results  = iterable.map(intoAB.into).toList
    sequence(results).map { list =>
      val builder = factory.newBuilder
      builder ++= list
      builder.result()
    }
  }

  /**
   * Sequence a list of Eithers into an Either of list, short-circuiting on
   * first error
   */
  protected def sequence[E, A](list: List[Either[E, A]]): Either[E, List[A]] = {
    val builder = List.newBuilder[A]
    val iter    = list.iterator
    while (iter.hasNext) {
      iter.next() match {
        case Right(a) => builder += a
        case Left(e)  => return Left(e)
      }
    }
    Right(builder.result())
  }
}

trait IntoContainerInstancesLowPriority {
  // Low priority instances can go here if needed
}
