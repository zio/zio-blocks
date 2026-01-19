package zio.blocks.schema.json

/**
 * A `JsonEncoder[A]` is a typeclass that can encode a value of type `A` into a
 * JSON representation.
 */
trait JsonEncoder[A] {
  def encode(a: A): Json
}

object JsonEncoder {
  def apply[A](implicit encoder: JsonEncoder[A]): JsonEncoder[A] = encoder

  implicit val stringEncoder: JsonEncoder[String] = new JsonEncoder[String] {
    def encode(a: String): Json = Json.String(a)
  }

  implicit val intEncoder: JsonEncoder[Int] = new JsonEncoder[Int] {
    def encode(a: Int): Json = Json.Number(BigDecimal(a))
  }

  implicit val booleanEncoder: JsonEncoder[Boolean] = new JsonEncoder[Boolean] {
    def encode(a: Boolean): Json = Json.Boolean(a)
  }

  implicit val bigDecimalEncoder: JsonEncoder[BigDecimal] = new JsonEncoder[BigDecimal] {
    def encode(a: BigDecimal): Json = Json.Number(a)
  }

  implicit def optionEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Option[A]] =
    new JsonEncoder[Option[A]] {
      def encode(a: Option[A]): Json = a match {
        case Some(value) => encoder.encode(value)
        case None        => Json.Null
      }
    }

  implicit def listEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[List[A]] =
    new JsonEncoder[List[A]] {
      def encode(a: List[A]): Json = Json.Array(a.map(encoder.encode).toVector)
    }

  implicit def vectorEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Vector[A]] =
    new JsonEncoder[Vector[A]] {
      def encode(a: Vector[A]): Json = Json.Array(a.map(encoder.encode))
    }

  implicit def mapEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Map[String, A]] =
    new JsonEncoder[Map[String, A]] {
      def encode(a: Map[String, A]): Json = 
        Json.Object(a.map { case (k, v) => (k, encoder.encode(v)) }.toVector)
    }
}
