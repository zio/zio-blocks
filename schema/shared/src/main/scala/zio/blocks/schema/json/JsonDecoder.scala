package zio.blocks.schema.json

/**
 * A `JsonDecoder[A]` is a typeclass that can decode a value of type `A` from a
 * JSON representation.
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

  implicit def listDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[List[A]] =
    new JsonDecoder[List[A]] {
      def decode(json: Json): Either[JsonDecoderError, List[A]] = json match {
        case Json.Array(elements) =>
          elements.foldLeft[Either[JsonDecoderError, List[A]]](Right(Nil)) { (acc, elem) =>
            for {
              list <- acc
              item <- decoder.decode(elem)
            } yield list :+ item
          }
        case _ => Left(JsonDecoderError(s"Expected array, got $json"))
      }
    }

  implicit def vectorDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Vector[A]] =
    new JsonDecoder[Vector[A]] {
      def decode(json: Json): Either[JsonDecoderError, Vector[A]] = json match {
        case Json.Array(elements) =>
          elements.foldLeft[Either[JsonDecoderError, Vector[A]](Right(Vector.empty)) { (acc, elem) =>
            for {
              vector <- acc
              item <- decoder.decode(elem)
            } yield vector :+ item
          }
        case _ => Left(JsonDecoderError(s"Expected array, got $json"))
      }
    }

  implicit def mapDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Map[String, A]] =
    new JsonDecoder[Map[String, A]] {
      def decode(json: Json): Either[JsonDecoderError, Map[String, A]] = json match {
        case Json.Object(fields) =>
          fields.foldLeft[Either[JsonDecoderError, Map[String, A]](Right(Map.empty)) { (acc, field) =>
            for {
              map <- acc
              value <- decoder.decode(field._2)
            } yield map + (field._1 -> value)
          }
        case _ => Left(JsonDecoderError(s"Expected object, got $json"))
      }
    }
}
