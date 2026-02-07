package zio.blocks.schema

/**
 * Type class for numeric types that support arithmetic operations.
 *
 * Note: Consider using NumericPrimitiveType directly for new code, which is
 * part of the sealed PrimitiveType hierarchy and fully serializable.
 */
sealed trait IsNumeric[A] {
  def numericPrimitiveType: NumericPrimitiveType[A]

  def primitiveType: PrimitiveType[A] = numericPrimitiveType

  def numeric: Numeric[A] = numericPrimitiveType.numeric

  def schema: Schema[A]
}

object IsNumeric {
  implicit val IsByte: IsNumeric[Byte] = new IsNumeric[Byte] {
    def numericPrimitiveType: NumericPrimitiveType[Byte] = PrimitiveType.Byte(Validation.None)

    def schema: Schema[Byte] = Schema[Byte]
  }

  implicit val IsShort: IsNumeric[Short] = new IsNumeric[Short] {
    def numericPrimitiveType: NumericPrimitiveType[Short] = PrimitiveType.Short(Validation.None)

    def schema: Schema[Short] = Schema[Short]
  }

  implicit val IsInt: IsNumeric[Int] = new IsNumeric[Int] {
    def numericPrimitiveType: NumericPrimitiveType[Int] = PrimitiveType.Int(Validation.None)

    def schema: Schema[Int] = Schema[Int]
  }

  implicit val IsLong: IsNumeric[Long] = new IsNumeric[Long] {
    def numericPrimitiveType: NumericPrimitiveType[Long] = PrimitiveType.Long(Validation.None)

    def schema: Schema[Long] = Schema[Long]
  }

  implicit val IsFloat: IsNumeric[Float] = new IsNumeric[Float] {
    def numericPrimitiveType: NumericPrimitiveType[Float] = PrimitiveType.Float(Validation.None)

    def schema: Schema[Float] = Schema[Float]
  }

  implicit val IsDouble: IsNumeric[Double] = new IsNumeric[Double] {
    def numericPrimitiveType: NumericPrimitiveType[Double] = PrimitiveType.Double(Validation.None)

    def schema: Schema[Double] = Schema[Double]
  }

  implicit val IsBigInt: IsNumeric[BigInt] = new IsNumeric[BigInt] {
    def numericPrimitiveType: NumericPrimitiveType[BigInt] = PrimitiveType.BigInt(Validation.None)

    def schema: Schema[BigInt] = Schema[BigInt]
  }

  implicit val IsBigDecimal: IsNumeric[BigDecimal] = new IsNumeric[BigDecimal] {
    def numericPrimitiveType: NumericPrimitiveType[BigDecimal] = PrimitiveType.BigDecimal(Validation.None)

    def schema: Schema[BigDecimal] = Schema[BigDecimal]
  }
}
