/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.bson

import zio.blocks.schema._
import zio.test._

object BsonCodecOptionEitherSpec extends SchemaBaseSpec {

  // Test data types
  final case class Person(name: String, age: Option[Int])
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  final case class Config(host: String, port: Option[Int], ssl: Option[Boolean])
  object Config {
    implicit val schema: Schema[Config] = Schema.derived
  }

  sealed trait ApiResponse
  object ApiResponse {
    final case class Success(data: String)             extends ApiResponse
    final case class Error(message: String, code: Int) extends ApiResponse

    implicit val schema: Schema[ApiResponse] = Schema.derived
  }

  def spec = suite("BsonCodecOptionEitherSpec")(
    suite("Option encoding/decoding")(
      test("encode/decode Some(value)") {
        val person = Person("Alice", Some(30))
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Person])

        val encoded = codec.encoder.toBsonValue(person)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == person)
      },
      test("encode/decode None") {
        val person = Person("Bob", None)
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Person])

        val encoded = codec.encoder.toBsonValue(person)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == person)
      },
      test("encode/decode multiple Option fields") {
        val config = Config("localhost", Some(8080), None)
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Config])

        val encoded = codec.encoder.toBsonValue(config)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == config)
      },
      test("encode/decode all None") {
        val config = Config("localhost", None, None)
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Config])

        val encoded = codec.encoder.toBsonValue(config)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == config)
      },
      test("encode/decode all Some") {
        val config = Config("localhost", Some(443), Some(true))
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Config])

        val encoded = codec.encoder.toBsonValue(config)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == config)
      }
    ),
    suite("Option + Variant combination")(
      test("encode/decode ApiResponse in Option - Some(Success)") {
        val response: Option[ApiResponse] = Some(ApiResponse.Success("data"))
        val codec                         = BsonSchemaCodec.bsonCodec(Schema[Option[ApiResponse]])

        val encoded = codec.encoder.toBsonValue(response)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == response)
      },
      test("encode/decode ApiResponse in Option - Some(Error)") {
        val response: Option[ApiResponse] = Some(ApiResponse.Error("Not found", 404))
        val codec                         = BsonSchemaCodec.bsonCodec(Schema[Option[ApiResponse]])

        val encoded = codec.encoder.toBsonValue(response)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == response)
      },
      test("encode/decode ApiResponse in Option - None") {
        val response: Option[ApiResponse] = None
        val codec                         = BsonSchemaCodec.bsonCodec(Schema[Option[ApiResponse]])

        val encoded = codec.encoder.toBsonValue(response)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == response)
      }
    ),
    suite("Either encoding/decoding")(
      test("encode/decode Left") {
        final case class Result(value: Either[String, Int])

        val result = Result(Left("error message"))
        val codec  = BsonSchemaCodec.bsonCodec(Schema.derived[Result])

        val encoded = codec.encoder.toBsonValue(result)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == result)
      },
      test("encode/decode Right") {
        final case class Result(value: Either[String, Int])

        val result = Result(Right(42))
        val codec  = BsonSchemaCodec.bsonCodec(Schema.derived[Result])

        val encoded = codec.encoder.toBsonValue(result)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == result)
      }
    )
  )
}
