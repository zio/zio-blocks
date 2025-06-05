package zio.blocks.schema

sealed trait IsNumeric[A] {
  def numeric: Numeric[A]

  def primitiveType: PrimitiveType[A]

  def schema: Schema[A] = Schema.fromPrimitiveType(primitiveType)
}
object IsNumeric {
  case object IsByte extends IsNumeric[scala.Byte] {
    def primitiveType: PrimitiveType[scala.Byte] = PrimitiveType.Byte(Validation.None)

    def numeric: Numeric[scala.Byte] = implicitly[Numeric[scala.Byte]]
  }

  case object IsShort extends IsNumeric[scala.Short] {
    def primitiveType: PrimitiveType[scala.Short] = PrimitiveType.Short(Validation.None)

    def numeric: Numeric[scala.Short] = implicitly[Numeric[scala.Short]]
  }

  case object IsInt extends IsNumeric[scala.Int] {
    def primitiveType: PrimitiveType[scala.Int] = PrimitiveType.Int(Validation.None)

    def numeric: Numeric[scala.Int] = implicitly[Numeric[scala.Int]]
  }

  case object IsLong extends IsNumeric[scala.Long] {
    def primitiveType: PrimitiveType[scala.Long] = PrimitiveType.Long(Validation.None)

    def numeric: Numeric[scala.Long] = implicitly[Numeric[scala.Long]]
  }

  case object IsFloat extends IsNumeric[scala.Float] {
    def primitiveType: PrimitiveType[scala.Float] = PrimitiveType.Float(Validation.None)

    def numeric: Numeric[scala.Float] = implicitly[Numeric[scala.Float]]
  }

  case object IsDouble extends IsNumeric[scala.Double] {
    def primitiveType: PrimitiveType[scala.Double] = PrimitiveType.Double(Validation.None)

    def numeric: Numeric[scala.Double] = implicitly[Numeric[scala.Double]]
  }

  case object IsBigInt extends IsNumeric[scala.BigInt] {
    def primitiveType: PrimitiveType[scala.BigInt] = PrimitiveType.BigInt(Validation.None)

    def numeric: Numeric[scala.BigInt] = implicitly[Numeric[scala.BigInt]]
  }

  case object IsBigDecimal extends IsNumeric[scala.BigDecimal] {
    def primitiveType: PrimitiveType[scala.BigDecimal] = PrimitiveType.BigDecimal(Validation.None)

    def numeric: Numeric[scala.BigDecimal] = implicitly[Numeric[scala.BigDecimal]]
  }
}
