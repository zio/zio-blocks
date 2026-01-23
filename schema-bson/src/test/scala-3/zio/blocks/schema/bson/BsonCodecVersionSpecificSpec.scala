package zio.blocks.schema.bson

import zio.blocks.schema.Schema
import zio.test._

/**
 * Scala 3-specific tests for BSON codec. Either.
 */
object BsonCodecVersionSpecificSpec extends ZIOSpecDefault {

  final case class Result(value: Either[String, Int])
  object Result {
    implicit val schema: Schema[Result] = Schema.derived[Result]
  }

  def spec = suite("BsonCodecScala3Spec")(
    suite("Either encoding/decoding")(
      test("encode/decode Left") {
        val result = Result(Left("error message"))
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Result])

        val encoded = codec.encoder.toBsonValue(result)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == result)
      },
      test("encode/decode Right") {
        val result = Result(Right(42))
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Result])

        val encoded = codec.encoder.toBsonValue(result)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == result)
      }
    )
  )
}
