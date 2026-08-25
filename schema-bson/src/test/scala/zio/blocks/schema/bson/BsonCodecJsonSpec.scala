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

import org.bson._
import org.bson.io.BasicOutputBuffer
import org.bson.types.{Decimal128, ObjectId}
import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object BsonCodecJsonSpec extends SchemaBaseSpec {

  final case class Scope(name: String, description: String, payload: Json)

  object Scope {
    implicit val schema: Schema[Scope] = Schema.derived[Scope]
  }

  private val jsonCodec  = BsonSchemaCodec.bsonCodec(Schema[Json])
  private val scopeCodec = BsonSchemaCodec.bsonCodec(Schema[Scope])

  def spec = suite("BsonCodecJsonSpec")(
    test("encodes every Json kind as semantic BSON") {
      val json = Json.parseUnsafe(
        """{"string":"value","true":true,"false":false,"int":1,"long":2147483648,"double":1.25,"decimal":0.1,"null":null,"array":["item",2],"object":{"nested":true}}"""
      )
      val bson = jsonCodec.encoder.toBsonValue(json).asDocument()

      assertTrue(
        bson.getString("string").getValue == "value",
        bson.getBoolean("true").getValue,
        !bson.getBoolean("false").getValue,
        bson.get("int").isInt32,
        bson.get("long").isInt64,
        bson.get("double").isDouble,
        bson.get("decimal").isDecimal128,
        bson.get("null").isNull,
        bson.getArray("array") == new BsonArray(java.util.Arrays.asList(new BsonString("item"), new BsonInt32(2))),
        bson.getDocument("object") == new BsonDocument("nested", BsonBoolean.TRUE),
        !bson.containsKey("Object"),
        !bson.containsKey("value")
      )
    },
    test("selects lossless BSON number representations") {
      val values = List(
        Json.Number(Int.MinValue)                          -> BsonType.INT32,
        Json.Number(Int.MaxValue)                          -> BsonType.INT32,
        Json.Number(Int.MaxValue.toLong + 1L)              -> BsonType.INT64,
        Json.Number(Long.MinValue)                         -> BsonType.INT64,
        Json.Number(Long.MaxValue)                         -> BsonType.INT64,
        Json.Number(BigInt(Long.MaxValue) + 1)             -> BsonType.DECIMAL128,
        Json.Number(BigDecimal("1.0"))                     -> BsonType.DOUBLE,
        Json.Number(BigDecimal("1.25"))                    -> BsonType.DOUBLE,
        Json.Number(BigDecimal("0.1"))                     -> BsonType.DECIMAL128,
        Json.Number(BigDecimal("1.234567890123456789"))    -> BsonType.DECIMAL128,
        Json.Number(BigDecimal("9.999999999999999E+6144")) -> BsonType.DECIMAL128
      )

      assertTrue(values.forall { case (json, bsonType) =>
        jsonCodec.encoder.toBsonValue(json).getBsonType == bsonType
      })
    },
    test("rejects Json numbers that BSON cannot represent exactly") {
      val excessivePrecision = Json.Number(BigDecimal("1.12345678901234567890123456789012345"))
      val excessiveExponent  = Json.Number(BigDecimal("1E+6145"))

      val precisionResult = scala.util.Try(jsonCodec.encoder.toBsonValue(excessivePrecision)).failed.toOption
      val exponentResult  = scala.util.Try(jsonCodec.encoder.toBsonValue(excessiveExponent)).failed.toOption

      assertTrue(
        precisionResult.exists(_.getMessage.contains("cannot be represented exactly as BSON")),
        exponentResult.exists(_.getMessage.contains("cannot be represented exactly as BSON"))
      )
    },
    test("decodes every BSON type that has a semantic Json representation") {
      val values = List[(BsonValue, Json)](
        new BsonString("text")                                                          -> Json.String("text"),
        BsonBoolean.TRUE                                                                -> Json.True,
        new BsonInt32(42)                                                               -> Json.Number(42),
        new BsonInt64(1234567890123L)                                                   -> Json.Number(1234567890123L),
        new BsonDouble(1.25)                                                            -> Json.Number(1.25),
        new BsonDecimal128(Decimal128.parse("0.1"))                                     -> Json.Number(BigDecimal("0.1")),
        BsonNull.VALUE                                                                  -> Json.Null,
        new BsonArray(java.util.Arrays.asList(new BsonInt32(1), new BsonString("two"))) ->
          Json.Array(Json.Number(1), Json.String("two")),
        new BsonDocument("key", new BsonString("value")) -> Json.Object("key" -> Json.String("value"))
      )

      assertTrue(values.forall { case (bson, expected) =>
        jsonCodec.decoder.fromBsonValue(bson) == Right(expected)
      })
    },
    test("preserves exact BSON DOUBLE values through Json round-trip") {
      val values = List(new BsonDouble(0.1), new BsonDouble(1.25), new BsonDouble(1.0))

      assertTrue(values.forall { bson =>
        jsonCodec.decoder.fromBsonValue(bson).exists { json =>
          jsonCodec.encoder.toBsonValue(json) == bson
        }
      })
    },
    test("normalizes Decimal128 negative zero") {
      val values = List(
        new BsonDecimal128(Decimal128.NEGATIVE_ZERO),
        new BsonDecimal128(Decimal128.parse("-0.00"))
      )

      assertTrue(values.forall(jsonCodec.decoder.fromBsonValue(_) == Right(Json.Number(0))))
    },
    test("rejects duplicate Json object fields") {
      val json            = Json.Object("key" -> Json.Number(1), "key" -> Json.Number(2))
      val bsonValueResult = scala.util.Try(jsonCodec.encoder.toBsonValue(json)).failed.toOption
      val writerResult    = scala.util.Try {
        val buffer = new BasicOutputBuffer()
        try {
          val writer = new BsonBinaryWriter(buffer)
          try jsonCodec.encoder.encode(writer, json, BsonEncoder.EncoderContext.default)
          finally writer.close()
        } finally buffer.close()
      }.failed.toOption

      assertTrue(
        bsonValueResult.exists(_.getMessage.contains("duplicate field 'key'")),
        writerResult.exists(_.getMessage.contains("duplicate field 'key'"))
      )
    },
    test("rejects non-finite BSON numbers") {
      val values = List[BsonValue](
        new BsonDouble(Double.NaN),
        new BsonDouble(Double.PositiveInfinity),
        new BsonDouble(Double.NegativeInfinity),
        new BsonDecimal128(Decimal128.NaN),
        new BsonDecimal128(Decimal128.POSITIVE_INFINITY),
        new BsonDecimal128(Decimal128.NEGATIVE_INFINITY)
      )

      assertTrue(values.forall(jsonCodec.decoder.fromBsonValue(_).isLeft))
    },
    test("reports the path of an unsupported nested BSON value") {
      val result = jsonCodec.decoder.fromBsonValue(
        new BsonDocument("nested", new BsonArray(java.util.Arrays.asList(new BsonObjectId(new ObjectId()))))
      )

      assertTrue(
        result.isLeft,
        result.swap.exists(_.render == ".nested[0]: Unsupported BSON type for Json: OBJECT_ID")
      )
    },
    test("round-trips Json values through the BsonValue path") {
      val values = List[Json](
        Json.String("text"),
        Json.Number(Int.MinValue),
        Json.Number(Int.MaxValue),
        Json.Number(Int.MaxValue.toLong + 1L),
        Json.Number(Long.MinValue),
        Json.Number(Long.MaxValue),
        Json.Number(BigInt(Long.MaxValue) + 1),
        Json.Number(BigDecimal("1.0")),
        Json.Number(BigDecimal("1.25")),
        Json.Number(BigDecimal("0.1")),
        Json.Boolean(true),
        Json.Boolean(false),
        Json.Null,
        Json.Array(Json.Number(1), Json.String("two"), Json.Object("nested" -> Json.False)),
        Json.Object("key" -> Json.String("value"), "count" -> Json.Number(3))
      )

      assertTrue(values.forall(BsonTestHelpers.roundTripToBsonValueAs(_, jsonCodec)))
    },
    test("round-trips Json values through the BSON writer and reader path") {
      val values = List[Json](
        Json.String("text"),
        Json.Number(42),
        Json.Number(1234567890123L),
        Json.Number(BigDecimal("1.25")),
        Json.Number(BigDecimal("0.1")),
        Json.Boolean(false),
        Json.Null,
        Json.Array(Json.Object("name" -> Json.String("zio")), Json.Number(7)),
        Json.Object("flag" -> Json.True, "items" -> Json.Array(Json.Null, Json.Number(2)))
      )

      assertTrue(values.forall(BsonTestHelpers.roundTripWriterReader(_, jsonCodec, isDocument = false)))
    },
    test("encodes a Json field as semantic BSON and round-trips its enclosing record") {
      val entity = Scope(
        name = "test",
        description = "test scope",
        payload = Json.parseUnsafe("""{"key":"string_value"}""")
      )
      val expected = BsonDocument.parse(
        """{"name":"test","description":"test scope","payload":{"key":"string_value"}}"""
      )

      val bson    = scopeCodec.encoder.toBsonValue(entity).asDocument()
      val decoded = scopeCodec.decoder.fromBsonValue(bson)

      assertTrue(bson == expected, decoded == Right(entity))
    }
  )
}
