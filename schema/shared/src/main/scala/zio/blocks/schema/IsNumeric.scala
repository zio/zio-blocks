package zio.blocks.schema

sealed trait IsNumeric[A] {
  def numeric: Numeric[A]

  def primitiveType: PrimitiveType[A]

  def schema: Schema[A] = Schema.fromPrimitiveType(primitiveType)
}

object IsNumeric {
  implicit val IsByte: IsNumeric[Byte] = new IsNumeric[scala.Byte] {
    def primitiveType: PrimitiveType[scala.Byte] = PrimitiveType.Byte(Validation.None)

    def numeric: Numeric[scala.Byte] = implicitly[Numeric[scala.Byte]]
  }

  implicit val IsShort: IsNumeric[Short] = new IsNumeric[scala.Short] {
    def primitiveType: PrimitiveType[scala.Short] = PrimitiveType.Short(Validation.None)

    def numeric: Numeric[scala.Short] = implicitly[Numeric[scala.Short]]
  }

  implicit val IsInt: IsNumeric[Int] = new IsNumeric[scala.Int] {
    def primitiveType: PrimitiveType[scala.Int] = PrimitiveType.Int(Validation.None)

    def numeric: Numeric[scala.Int] = implicitly[Numeric[scala.Int]]
  }

  implicit val IsLong: IsNumeric[Long] = new IsNumeric[scala.Long] {
    def primitiveType: PrimitiveType[scala.Long] = PrimitiveType.Long(Validation.None)

    def numeric: Numeric[scala.Long] = implicitly[Numeric[scala.Long]]
  }

  implicit val IsFloat: IsNumeric[Float] = new IsNumeric[scala.Float] {
    def primitiveType: PrimitiveType[scala.Float] = PrimitiveType.Float(Validation.None)

    def numeric: Numeric[scala.Float] = implicitly[Numeric[scala.Float]]
  }

  implicit val IsDouble: IsNumeric[Double] = new IsNumeric[scala.Double] {
    def primitiveType: PrimitiveType[scala.Double] = PrimitiveType.Double(Validation.None)

    def numeric: Numeric[scala.Double] = implicitly[Numeric[scala.Double]]
  }

  implicit val IsBigInt: IsNumeric[BigInt] = new IsNumeric[scala.BigInt] {
    def primitiveType: PrimitiveType[scala.BigInt] = PrimitiveType.BigInt(Validation.None)

    def numeric: Numeric[scala.BigInt] = implicitly[Numeric[scala.BigInt]]
  }

  implicit val IsBigDecimal: IsNumeric[BigDecimal] = new IsNumeric[scala.BigDecimal] {
    def primitiveType: PrimitiveType[scala.BigDecimal] = PrimitiveType.BigDecimal(Validation.None)

    def numeric: Numeric[scala.BigDecimal] = implicitly[Numeric[scala.BigDecimal]]
  }
}
