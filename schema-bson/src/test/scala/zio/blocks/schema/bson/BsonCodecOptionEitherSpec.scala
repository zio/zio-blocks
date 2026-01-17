package zio.blocks.schema.bson

import zio.blocks.schema.Schema
import zio.test._

object BsonCodecOptionEitherSpec extends ZIOSpecDefault {

  // Test data types
  final case class Person(name: String, age: Option[Int])
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
  }

  final case class Config(host: String, port: Option[Int], ssl: Option[Boolean])
  object Config {
    implicit val schema: Schema[Config] = Schema.derived[Config]
  }

  sealed trait ApiResponse
  object ApiResponse {
    final case class Success(data: String)             extends ApiResponse
    final case class Error(message: String, code: Int) extends ApiResponse

    implicit val schema: Schema[ApiResponse] = Schema.derived[ApiResponse]
  }

  def spec = suite("BsonCodecOptionEitherSpec")(
    suite("Option encoding/decoding")(
      test("encode/decode Some(value)") {
        val person = Person("Alice", Some(30))
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Person])

        val encoded = codec.encoder.toBsonValue(person)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == person)
      },
      test("encode/decode None") {
        val person = Person("Bob", None)
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Person])

        val encoded = codec.encoder.toBsonValue(person)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == person)
      },
      test("encode/decode multiple Option fields") {
        val config = Config("localhost", Some(8080), None)
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Config])

        val encoded = codec.encoder.toBsonValue(config)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == config)
      },
      test("encode/decode all None") {
        val config = Config("localhost", None, None)
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Config])

        val encoded = codec.encoder.toBsonValue(config)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == config)
      },
      test("encode/decode all Some") {
        val config = Config("localhost", Some(443), Some(true))
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Config])

        val encoded = codec.encoder.toBsonValue(config)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == config)
      }
    ),
    suite("Option + Variant combination")(
      test("encode/decode ApiResponse in Option - Some(Success)") {
        val response: Option[ApiResponse] = Some(ApiResponse.Success("data"))
        val codec                         = BsonSchemaCodec.bsonCodec(Schema[Option[ApiResponse]])

        val encoded = codec.encoder.toBsonValue(response)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == response)
      },
      test("encode/decode ApiResponse in Option - Some(Error)") {
        val response: Option[ApiResponse] = Some(ApiResponse.Error("Not found", 404))
        val codec                         = BsonSchemaCodec.bsonCodec(Schema[Option[ApiResponse]])

        val encoded = codec.encoder.toBsonValue(response)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == response)
      },
      test("encode/decode ApiResponse in Option - None") {
        val response: Option[ApiResponse] = None
        val codec                         = BsonSchemaCodec.bsonCodec(Schema[Option[ApiResponse]])

        val encoded = codec.encoder.toBsonValue(response)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == response)
      }
    )
  )
}
