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

import org.bson.{BsonArray, BsonDocument, BsonInt32, BsonString}
import zio.blocks.schema._
import zio.test._

object BsonCodecSequenceSpec extends SchemaBaseSpec {

  case class RecordWithList(items: List[String])

  object RecordWithList {
    val schema: Schema[RecordWithList]   = Schema.derived[RecordWithList]
    val codec: BsonCodec[RecordWithList] = BsonSchemaCodec.bsonCodec(schema)
  }

  case class RecordWithVector(numbers: Vector[Int])

  object RecordWithVector {
    val schema: Schema[RecordWithVector]   = Schema.derived[RecordWithVector]
    val codec: BsonCodec[RecordWithVector] = BsonSchemaCodec.bsonCodec(schema)
  }

  def spec = suite("BsonCodecSequenceSpec")(
    suite("List")(
      test("encode list of strings") {
        val value     = RecordWithList(List("a", "b", "c"))
        val bsonValue = RecordWithList.codec.encoder.toBsonValue(value)

        val array = bsonValue.asDocument().getArray("items")
        assertTrue(array.size() == 3) &&
        assertTrue(array.get(0).asString().getValue() == "a") &&
        assertTrue(array.get(1).asString().getValue() == "b") &&
        assertTrue(array.get(2).asString().getValue() == "c")
      },
      test("decode list of strings") {
        val array = new BsonArray()
        array.add(new BsonString("x"))
        array.add(new BsonString("y"))

        val doc = new BsonDocument()
        doc.put("items", array)

        val decoded = RecordWithList.codec.decoder.fromBsonValueUnsafe(doc, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded.items == List("x", "y"))
      },
      test("round trip list") {
        val original = RecordWithList(List("foo", "bar", "baz"))
        val encoded  = RecordWithList.codec.encoder.toBsonValue(original)
        val decoded  =
          RecordWithList.codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == original)
      },
      test("empty list") {
        val original = RecordWithList(List.empty)
        val encoded  = RecordWithList.codec.encoder.toBsonValue(original)
        val decoded  =
          RecordWithList.codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == original)
      }
    ),
    suite("Vector")(
      test("encode vector of ints") {
        val value     = RecordWithVector(Vector(1, 2, 3))
        val bsonValue = RecordWithVector.codec.encoder.toBsonValue(value)

        val array = bsonValue.asDocument().getArray("numbers")
        assertTrue(array.size() == 3) &&
        assertTrue(array.get(0).asInt32().getValue() == 1) &&
        assertTrue(array.get(1).asInt32().getValue() == 2) &&
        assertTrue(array.get(2).asInt32().getValue() == 3)
      },
      test("decode vector of ints") {
        val array = new BsonArray()
        array.add(new BsonInt32(10))
        array.add(new BsonInt32(20))
        array.add(new BsonInt32(30))

        val doc = new BsonDocument()
        doc.put("numbers", array)

        val decoded =
          RecordWithVector.codec.decoder.fromBsonValueUnsafe(doc, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded.numbers == Vector(10, 20, 30))
      },
      test("round trip vector") {
        val original = RecordWithVector(Vector(100, 200, 300))
        val encoded  = RecordWithVector.codec.encoder.toBsonValue(original)
        val decoded  =
          RecordWithVector.codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == original)
      }
    )
  )
}
