package zio.blocks.schema

import org.bson.BsonValue

package object bson {

  /**
   * Extension methods for BsonValue to enable decoding.
   */
  implicit class BsonDecoderOps(private val value: BsonValue) extends AnyVal {
    def as[A](implicit decoder: BsonDecoder[A]): Either[BsonDecoder.Error, A] =
      decoder.fromBsonValue(value)
  }
}
