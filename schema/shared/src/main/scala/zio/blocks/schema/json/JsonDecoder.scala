package zio.blocks.schema.json

/**
 * A `JsonDecoder[A]` is a typeclass that can decode a value of type `A` from
 * a JSON representation.
 */
trait JsonDecoder[A] {
  def decode(json: Json): Either[JsonDecoderError, A]
}

object JsonDecoder {
  def apply[A](implicit decoder: JsonDecoder[A]): JsonDecoder[A] = decoder

  implicit val stringDecoder: JsonDecoder[String] = new JsonDecoder[String] {
    def decode(json: Json): Either[JsonDecoderError, String] = json match {
      case Json.String(value) => Right(value)
      case _                  => Left(JsonDecoderError(s"Expected string, got $json"))
    }
  }

  implicit val intDecoder: JsonDecoder[Int] = new JsonDecoder[Int] {
    def decode(json: Json): Either[JsonDecoderError, Int] = json match {
      case Json.Number(value) =>
        if (value.isValidInt) Right(value.toInt)
        else Left(JsonDecoderError(s"Number $value is not a valid integer"))
      case _ => Left(JsonDecoderError(s"Expected number, got $json"))
    }
  }

  implicit val booleanDecoder: JsonDecoder[Boolean] = new JsonDecoder[Boolean] {
    def decode(json: Json): Either[JsonDecoderError, Boolean] = json match {
      case Json.Boolean(value) => Right(value)
      case _                   => Left(JsonDecoderError(s"Expected boolean, got $json"))
    }
  }

  implicit val bigDecimalDecoder: JsonDecoder[BigDecimal] = new JsonDecoder[BigDecimal] {
    def decode(json: Json): Either[JsonDecoderError, BigDecimal] = json match {
      case Json.Number(value) => Right(value)
      case _                  => Left(JsonDecoderError(s"Expected number, got $json"))
    }
  }

  implicit def optionDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Option[A]] =
    new JsonDecoder[Option[A]] {
      def decode(json: Json): Either[JsonDecoderError, Option[A]] = json match {
        case Json.Null => Right(None)
        case other     => decoder.decode(other).map(Some(_))
      }
    }
}
