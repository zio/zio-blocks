package zio.blocks.schema.json

import zio.Chunk

trait JsonDecoder[A] { self =>
  def decode(json: Json): Either[JsonError, A]

  def map[B](f: A => B): JsonDecoder[B] = new JsonDecoder[B] {
    def decode(json: Json): Either[JsonError, B] = self.decode(json).map(f)
  }

  def flatMap[B](f: A => JsonDecoder[B]): JsonDecoder[B] = new JsonDecoder[B] {
    def decode(json: Json): Either[JsonError, B] =
      self.decode(json).flatMap(a => f(a).decode(json))
  }

  def orElse[A1 >: A](that: => JsonDecoder[A1]): JsonDecoder[A1] = new JsonDecoder[A1] {
    def decode(json: Json): Either[JsonError, A1] =
      self.decode(json).orElse(that.decode(json))
  }
}

object JsonDecoder {
  def apply[A](implicit decoder: JsonDecoder[A]): JsonDecoder[A] = decoder

  def instance[A](f: Json => Either[JsonError, A]): JsonDecoder[A] = new JsonDecoder[A] {
    def decode(json: Json): Either[JsonError, A] = f(json)
  }

  implicit val jsonDecoder: JsonDecoder[Json] = instance(Right(_))

  implicit val stringDecoder: JsonDecoder[String] = instance {
    case Json.Str(value) => Right(value)
    case other           => Left(JsonError.typeMismatch("String", other.getClass.getSimpleName))
  }

  implicit val booleanDecoder: JsonDecoder[Boolean] = instance {
    case Json.Bool(value) => Right(value)
    case other            => Left(JsonError.typeMismatch("Boolean", other.getClass.getSimpleName))
  }

  implicit val intDecoder: JsonDecoder[Int] = instance {
    case Json.Num(value) => Right(value.intValue)
    case other           => Left(JsonError.typeMismatch("Number", other.getClass.getSimpleName))
  }

  implicit val longDecoder: JsonDecoder[Long] = instance {
    case Json.Num(value) => Right(value.longValue)
    case other           => Left(JsonError.typeMismatch("Number", other.getClass.getSimpleName))
  }

  implicit val doubleDecoder: JsonDecoder[Double] = instance {
    case Json.Num(value) => Right(value.doubleValue)
    case other           => Left(JsonError.typeMismatch("Number", other.getClass.getSimpleName))
  }

  implicit val bigDecimalDecoder: JsonDecoder[java.math.BigDecimal] = instance {
    case Json.Num(value) => Right(value)
    case other           => Left(JsonError.typeMismatch("Number", other.getClass.getSimpleName))
  }

  implicit def optionDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Option[A]] = instance {
    case Json.Null => Right(None)
    case json      => decoder.decode(json).map(Some(_))
  }

  implicit def chunkDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Chunk[A]] = instance {
    case Json.Arr(elements) =>
      elements.zipWithIndex.foldLeft[Either[JsonError, Chunk[A]]](Right(Chunk.empty)) {
        case (Right(acc), (elem, idx)) =>
          decoder.decode(elem) match {
            case Right(a)    => Right(acc :+ a)
            case Left(error) => Left(error.atIndex(idx))
          }
        case (left, _) => left
      }
    case other => Left(JsonError.typeMismatch("Array", other.getClass.getSimpleName))
  }

  implicit def listDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[List[A]] =
    chunkDecoder[A].map(_.toList)

  implicit def vectorDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Vector[A]] =
    chunkDecoder[A].map(_.toVector)

  implicit def mapDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Map[String, A]] = instance {
    case Json.Obj(fields) =>
      fields.foldLeft[Either[JsonError, Map[String, A]]](Right(Map.empty)) {
        case (Right(acc), (key, value)) =>
          decoder.decode(value) match {
            case Right(a)    => Right(acc + (key -> a))
            case Left(error) => Left(error.atKey(key))
          }
        case (left, _) => left
      }
    case other => Left(JsonError.typeMismatch("Object", other.getClass.getSimpleName))
  }
}

trait JsonEncoder[A] { self =>
  def encode(value: A): Json

  def contramap[B](f: B => A): JsonEncoder[B] = new JsonEncoder[B] {
    def encode(value: B): Json = self.encode(f(value))
  }
}

object JsonEncoder {
  def apply[A](implicit encoder: JsonEncoder[A]): JsonEncoder[A] = encoder

  def instance[A](f: A => Json): JsonEncoder[A] = new JsonEncoder[A] {
    def encode(value: A): Json = f(value)
  }

  implicit val jsonEncoder: JsonEncoder[Json] = instance(identity)

  implicit val stringEncoder: JsonEncoder[String] = instance(Json.Str(_))

  implicit val booleanEncoder: JsonEncoder[Boolean] = instance(Json.Bool(_))

  implicit val intEncoder: JsonEncoder[Int] = instance(n => Json.num(n))

  implicit val longEncoder: JsonEncoder[Long] = instance(n => Json.num(n))

  implicit val doubleEncoder: JsonEncoder[Double] = instance(n => Json.num(n))

  implicit val bigDecimalEncoder: JsonEncoder[java.math.BigDecimal] = instance(Json.Num(_))

  implicit def optionEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Option[A]] = instance {
    case Some(value) => encoder.encode(value)
    case None        => Json.Null
  }

  implicit def chunkEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Chunk[A]] = instance { chunk =>
    Json.Arr(chunk.map(encoder.encode))
  }

  implicit def listEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[List[A]] =
    chunkEncoder[A].contramap(Chunk.fromIterable(_))

  implicit def vectorEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Vector[A]] =
    chunkEncoder[A].contramap(Chunk.fromIterable(_))

  implicit def mapEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Map[String, A]] = instance { m =>
    Json.Obj(Chunk.fromIterable(m.map { case (k, v) => (k, encoder.encode(v)) }))
  }
}
