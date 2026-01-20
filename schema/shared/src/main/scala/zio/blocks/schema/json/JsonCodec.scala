package zio.blocks.schema.json

import zio.blocks.schema.Schema
import scala.collection.immutable.ListMap

// ===========================================================================
// JsonDecoder
// ===========================================================================

trait JsonDecoder[A] {
  def decode(json: Json): Either[JsonError, A]
  
  def map[B](f: A => B): JsonDecoder[B] = (json: Json) => decode(json).map(f)
  
  def flatMap[B](f: A => JsonDecoder[B]): JsonDecoder[B] = (json: Json) => decode(json).flatMap(a => f(a).decode(json))
}

trait JsonDecoderLowPriority {
  /**
   * Low-priority implicit: derive decoder from Schema.
   * This will be used only if no explicit decoder is found.
   */
  @annotation.nowarn("msg=never used")
  implicit def fromSchema[A](implicit _schema: Schema[A]): JsonDecoder[A] = 
    JsonDecoder.from(_ => Left(JsonError("Schema-based decoding not yet implemented")))
}

object JsonDecoder extends JsonDecoderLowPriority {
  def apply[A](implicit decoder: JsonDecoder[A]): JsonDecoder[A] = decoder

  def from[A](f: Json => Either[JsonError, A]): JsonDecoder[A] = (json: Json) => f(json)

  // ===========================================================================
  // High-priority implicits: Primitives
  // ===========================================================================

  implicit val json: JsonDecoder[Json] = from(Right(_))

  implicit val string: JsonDecoder[String] = from {
    case Json.String(s) => Right(s)
    case _              => Left(JsonError("Expected String"))
  }

  implicit val boolean: JsonDecoder[Boolean] = from {
    case Json.Boolean(b) => Right(b)
    case _               => Left(JsonError("Expected Boolean"))
  }

  implicit val int: JsonDecoder[Int] = from {
    case Json.Number(s) => 
      try Right(s.toInt) 
      catch { case _: NumberFormatException => Left(JsonError("Expected Int")) }
    case _ => Left(JsonError("Expected Int"))
  }

  implicit val long: JsonDecoder[Long] = from {
    case Json.Number(s) =>
      try Right(s.toLong)
      catch { case _: NumberFormatException => Left(JsonError("Expected Long")) }
    case _ => Left(JsonError("Expected Long"))
  }
  
  implicit val double: JsonDecoder[Double] = from {
    case Json.Number(s) =>
      try Right(s.toDouble)
      catch { case _: NumberFormatException => Left(JsonError("Expected Double")) }
    case _ => Left(JsonError("Expected Double"))
  }
  
  implicit val float: JsonDecoder[Float] = from {
    case Json.Number(s) =>
      try Right(s.toFloat)
      catch { case _: NumberFormatException => Left(JsonError("Expected Float")) }
    case _ => Left(JsonError("Expected Float"))
  }

  implicit val bigInt: JsonDecoder[BigInt] = from {
    case Json.Number(s) =>
      try Right(BigInt(s))
      catch { case _: NumberFormatException => Left(JsonError("Expected BigInt")) }
    case _ => Left(JsonError("Expected BigInt"))
  }

  implicit val bigDecimal: JsonDecoder[BigDecimal] = from {
    case Json.Number(s) =>
      try Right(BigDecimal(s))
      catch { case _: NumberFormatException => Left(JsonError("Expected BigDecimal")) }
    case _ => Left(JsonError("Expected BigDecimal"))
  }

  // ===========================================================================
  // High-priority implicits: Collections
  // ===========================================================================

  implicit def option[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Option[A]] = from {
    case Json.Null => Right(None)
    case json      => decoder.decode(json).map(Some(_))
  }

  implicit def seq[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Seq[A]] = from {
    case Json.Array(elements) =>
      elements.foldLeft[Either[JsonError, Vector[A]]](Right(Vector.empty)) { (acc, json) =>
        acc.flatMap(vec => decoder.decode(json).map(vec :+ _))
      }
    case _ => Left(JsonError("Expected Array"))
  }
  
  implicit def list[A](implicit decoder: JsonDecoder[A]): JsonDecoder[List[A]] = seq[A].map(_.toList)
  
  implicit def vector[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Vector[A]] = seq[A].map(_.toVector)

  implicit def map[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Map[String, A]] = from {
    case Json.Object(fields) =>
      fields.foldLeft[Either[JsonError, Map[String, A]]](Right(ListMap.empty)) { (acc, field) =>
        val (key, value) = field
        acc.flatMap(m => decoder.decode(value).map(v => m + (key -> v)))
      }
    case _ => Left(JsonError("Expected Object"))
  }
}

// ===========================================================================
// JsonEncoder
// ===========================================================================

trait JsonEncoder[A] {
  def encode(value: A): Json
  
  def contramap[B](f: B => A): JsonEncoder[B] = (value: B) => encode(f(value))
}

trait JsonEncoderLowPriority {
  /**
   * Low-priority implicit: derive encoder from Schema.
   * This will be used only if no explicit encoder is found.
   */
  @annotation.nowarn("msg=never used")
  implicit def fromSchema[A](implicit _schema: Schema[A]): JsonEncoder[A] = 
    JsonEncoder.from(_ => Json.Null) // Stub: Schema-based encoding not yet implemented
}

object JsonEncoder extends JsonEncoderLowPriority {
  def apply[A](implicit encoder: JsonEncoder[A]): JsonEncoder[A] = encoder
  
  def from[A](f: A => Json): JsonEncoder[A] = (value: A) => f(value)

  // ===========================================================================
  // High-priority implicits: Primitives
  // ===========================================================================

  implicit val json: JsonEncoder[Json] = from(identity)

  implicit val string: JsonEncoder[String] = from(Json.String(_))
  
  implicit val boolean: JsonEncoder[Boolean] = from(Json.Boolean(_))
  
  implicit val int: JsonEncoder[Int] = from(n => Json.Number(n.toString))
  
  implicit val long: JsonEncoder[Long] = from(n => Json.Number(n.toString))
  
  implicit val double: JsonEncoder[Double] = from(n => Json.Number(n.toString))
  
  implicit val float: JsonEncoder[Float] = from(n => Json.Number(n.toString))
  
  implicit val bigInt: JsonEncoder[BigInt] = from(n => Json.Number(n.toString))
  
  implicit val bigDecimal: JsonEncoder[BigDecimal] = from(n => Json.Number(n.toString))

  // ===========================================================================
  // High-priority implicits: Collections
  // ===========================================================================

  implicit def option[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Option[A]] = from {
    case Some(a) => encoder.encode(a)
    case None    => Json.Null
  }

  implicit def seq[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Seq[A]] = from { seq =>
    Json.Array(seq.map(encoder.encode).toVector)
  }
  
  implicit def list[A](implicit encoder: JsonEncoder[A]): JsonEncoder[List[A]] = seq[A].contramap(identity)
  
  implicit def vector[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Vector[A]] = seq[A].contramap(identity)

  implicit def map[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Map[String, A]] = from { map =>
    Json.Object(map.map { case (k, v) => k -> encoder.encode(v) }.toVector)
  }
}
