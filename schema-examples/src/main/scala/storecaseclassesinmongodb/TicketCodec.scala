package storecaseclassesinmongodb

import org.bson._
import zio.blocks.schema._
import zio.blocks.schema.bson._
import zio.blocks.typeid.TypeId

/**
 * How to Store Case Classes in MongoDB — Store Optional Fields
 *
 * A codec for `Option[String]` that stores `None` as BSON null and `Some` as a
 * plain string, and the ticket codec that uses it.
 */
object TicketCodec {
  val nullableString: BsonCodec[Option[String]] = BsonCodec(
    new BsonEncoder[Option[String]] {
      def encode(writer: BsonWriter, value: Option[String], ctx: BsonEncoder.EncoderContext): Unit =
        value match {
          case Some(text) => writer.writeString(text)
          case None       => writer.writeNull()
        }

      def toBsonValue(value: Option[String]): BsonValue =
        value.fold[BsonValue](BsonNull.VALUE)(text => new BsonString(text))
    },
    new BsonDecoder[Option[String]] {
      def decodeUnsafe(
        reader: BsonReader,
        trace: List[BsonTrace],
        ctx: BsonDecoder.BsonDecoderContext
      ): Option[String] =
        reader.getCurrentBsonType match {
          case BsonType.NULL   => reader.readNull(); None
          case BsonType.STRING => Some(reader.readString())
          case other           => throw BsonDecoder.Error(trace, s"Expected STRING or NULL but got $other")
        }

      def fromBsonValueUnsafe(
        value: BsonValue,
        trace: List[BsonTrace],
        ctx: BsonDecoder.BsonDecoderContext
      ): Option[String] =
        if (value.isNull) None
        else if (value.isString) Some(value.asString().getValue)
        else throw BsonDecoder.Error(trace, s"Expected STRING or NULL but got ${value.getBsonType}")
    }
  )

  val nullableTicketCodec: BsonCodec[Ticket] =
    Schema[Ticket]
      .deriving(BsonCodecDeriver)
      .instance(TypeId.of[Option[String]], nullableString)
      .derive
}
