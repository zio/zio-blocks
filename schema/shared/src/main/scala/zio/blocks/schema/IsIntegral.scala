package zio.blocks.schema

/**
 * Type class for integral types that support bitwise operations. Only Byte,
 * Short, Int, and Long are supported.
 */
sealed trait IsIntegral[A] {
  def schema: Schema[A]
}

object IsIntegral {
  implicit val IsByte: IsIntegral[Byte] = new IsIntegral[Byte] {
    def schema: Schema[Byte] = Schema[Byte]
  }

  implicit val IsShort: IsIntegral[Short] = new IsIntegral[Short] {
    def schema: Schema[Short] = Schema[Short]
  }

  implicit val IsInt: IsIntegral[Int] = new IsIntegral[Int] {
    def schema: Schema[Int] = Schema[Int]
  }

  implicit val IsLong: IsIntegral[Long] = new IsIntegral[Long] {
    def schema: Schema[Long] = Schema[Long]
  }
}
