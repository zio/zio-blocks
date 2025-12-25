package zio.blocks.schema

import zio.blocks.schema.internal.IntoVersionSpecific

trait Into[-A, +B] { self =>
  def into(input: A): Either[SchemaError, B]

  def map[C](f: B => C): Into[A, C] = new Into[A, C] {
    def into(input: A): Either[SchemaError, C] = self.into(input).map(f)
  }
}

object Into extends IntoVersionSpecific with IntoLowPriority {
  def apply[A, B](implicit ev: Into[A, B]): Into[A, B] = ev

  def total[A, B](f: A => B): Into[A, B] = new Into[A, B] {
    def into(input: A): Either[SchemaError, B] = Right(f(input))
  }

  def instance[A, B](f: A => Either[SchemaError, B]): Into[A, B] = new Into[A, B] {
    def into(input: A): Either[SchemaError, B] = f(input)
  }

  // === Primitives ===
  implicit val identity: Into[Any, Any] = total(a => a)

  // Numeric Widening (Safe)
  implicit val intToLong: Into[Int, Long]         = total(_.toLong)
  implicit val intToDouble: Into[Int, Double]     = total(_.toDouble)
  implicit val floatToDouble: Into[Float, Double] = total(_.toDouble)

  // Numeric Narrowing (Validated)
  implicit val longToInt: Into[Long, Int] = instance { v =>
    if (v >= Int.MinValue.toLong && v <= Int.MaxValue.toLong) Right(v.toInt)
    else Left(SchemaError.numericOverflow(v, "Long", "Int"))
  }

  implicit val doubleToFloat: Into[Double, Float] = instance { v =>
    if (v.toFloat.isInfinite && !v.isInfinite) Left(SchemaError.numericOverflow(v, "Double", "Float"))
    else Right(v.toFloat)
  }

  // === Collections ===
  implicit def listInto[A, B](implicit ev: Into[A, B]): Into[List[A], List[B]] = instance { list =>
    val results = list.zipWithIndex.map { case (a, idx) =>
      ev.into(a).left.map(_.atPath(s"[$idx]"))
    }
    SchemaError.accumulateErrors(results).map(_.toList)
  }

  implicit def optionInto[A, B](implicit ev: Into[A, B]): Into[Option[A], Option[B]] = instance {
    case Some(a) => ev.into(a).map(Some(_))
    case None    => Right(None)
  }

  def optionToRequired[A, B](fieldName: String)(implicit ev: Into[A, B]): Into[Option[A], B] = instance {
    case Some(a) => ev.into(a)
    case None    => Left(SchemaError.missingField(fieldName, "required"))
  }
}

trait IntoLowPriority {
  implicit def subtype[A <: B, B]: Into[A, B] = Into.total(a => a)
}
