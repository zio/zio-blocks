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

import org.bson.{BsonDocument, BsonDouble, BsonObjectId}
import org.bson.types.ObjectId
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
    test("encodes Json object to semantic BSON") {
      val json = Json.parseUnsafe("""{"key":"string_value","nested":{"flag":true},"items":[1,null]}""")
      val bson = jsonCodec.encoder.toBsonValue(json).asDocument()

      assertTrue(
        bson.getString("key").getValue == "string_value",
        bson.getDocument("nested").getBoolean("flag").getValue,
        bson.getArray("items").get(0).asInt32().getValue == 1,
        bson.getArray("items").get(1).isNull,
        !bson.containsKey("Object"),
        !bson.containsKey("value")
      )
    },
    test("encodes Json numbers using BSON numeric types") {
      val intBson     = jsonCodec.encoder.toBsonValue(Json.Number(42))
      val longBson    = jsonCodec.encoder.toBsonValue(Json.Number(1234567890123L))
      val doubleBson  = jsonCodec.encoder.toBsonValue(Json.Number(BigDecimal("1.25")))
      val decimalBson = jsonCodec.encoder.toBsonValue(Json.Number(BigDecimal("0.1")))

      assertTrue(
        intBson.isInt32,
        longBson.isInt64,
        doubleBson.isDouble,
        decimalBson.isDecimal128
      )
    },
    test("preserves BSON DOUBLE values through Json round-trip") {
      val bsonValues = List[BsonDouble](
        new BsonDouble(1.25),
        new BsonDouble(1.0)
      )

      assertTrue(
        bsonValues.forall { bsonValue =>
          val json      = jsonCodec.decoder.fromBsonValueUnsafe(bsonValue, Nil, BsonDecoder.BsonDecoderContext.default)
          val reencoded = jsonCodec.encoder.toBsonValue(json)
          reencoded.isDouble && reencoded == bsonValue
        }
      )
    },
    test("round-trips Json values through toBsonValue/as path") {
      val values = List[Json](
        Json.String("text"),
        Json.Number(42),
        Json.Number(1234567890123L),
        Json.Number(BigDecimal("1.25")),
        Json.Number(BigDecimal("0.1")),
        Json.Boolean(true),
        Json.Null,
        Json.Array(Json.Number(1), Json.String("two"), Json.Object("nested" -> Json.False)),
        Json.Object("key" -> Json.String("value"), "count" -> Json.Number(3))
      )

      assertTrue(values.forall(value => BsonTestHelpers.roundTripToBsonValueAs(value, jsonCodec)))
    },
    test("round-trips Json values through writer/reader path") {
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

      assertTrue(values.forall(value => BsonTestHelpers.roundTripWriterReader(value, jsonCodec, isDocument = false)))
    },
    test("reported bsonbug payload test uses semantic BSON and round-trips") {
      val entity = Scope(
        name = "test",
        description = "test scope",
        payload = Json.parseUnsafe("""{"key": "string_value"}""")
      )
      val expected = BsonDocument.parse(
        """
          {
            "name": "test",
            "description": "test scope",
            "payload": {
              "key": "string_value"
            }
          }
        """
      )

      val bson    = scopeCodec.encoder.toBsonValue(entity).asDocument()
      val decoded = scopeCodec.decoder.fromBsonValueUnsafe(bson, Nil, BsonDecoder.BsonDecoderContext.default)

      assertTrue(
        bson == expected,
        decoded == entity
      )
    },
    test("decoding unsupported native BSON type to Json fails") {
      val result = jsonCodec.decoder.fromBsonValue(new BsonObjectId(new ObjectId()))

      assertTrue(result.isLeft)
    }
  )
}
