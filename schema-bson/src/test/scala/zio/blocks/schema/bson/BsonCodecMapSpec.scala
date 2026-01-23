package zio.blocks.schema.bson

import org.bson.{BsonDocument, BsonInt32, BsonString}
import zio.bson._
import zio.blocks.schema._
import zio.test._

object BsonCodecMapSpec extends ZIOSpecDefault {

  case class RecordWithMap(data: Map[String, Int])

  object RecordWithMap {
    val schema: Schema[RecordWithMap]   = Schema.derived[RecordWithMap]
    val codec: BsonCodec[RecordWithMap] = BsonSchemaCodec.bsonCodec(schema)
  }

  case class RecordWithStringMap(labels: Map[String, String])

  object RecordWithStringMap {
    val schema: Schema[RecordWithStringMap]   = Schema.derived[RecordWithStringMap]
    val codec: BsonCodec[RecordWithStringMap] = BsonSchemaCodec.bsonCodec(schema)
  }

  def spec = suite("BsonCodecMapSpec")(
    suite("Map[String, Int]")(
      test("encode map with string keys") {
        val value     = RecordWithMap(Map("a" -> 1, "b" -> 2, "c" -> 3))
        val bsonValue = RecordWithMap.codec.encoder.toBsonValue(value)

        val dataDoc = bsonValue.asDocument().getDocument("data")
        assertTrue(dataDoc.getInt32("a").getValue() == 1) &&
        assertTrue(dataDoc.getInt32("b").getValue() == 2) &&
        assertTrue(dataDoc.getInt32("c").getValue() == 3)
      },
      test("decode map with string keys") {
        val dataDoc = new BsonDocument()
        dataDoc.put("x", new BsonInt32(10))
        dataDoc.put("y", new BsonInt32(20))

        val doc = new BsonDocument()
        doc.put("data", dataDoc)

        val decoded = RecordWithMap.codec.decoder.fromBsonValueUnsafe(doc, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded.data == Map("x" -> 10, "y" -> 20))
      },
      test("round trip map") {
        val original = RecordWithMap(Map("foo" -> 100, "bar" -> 200))
        val encoded  = RecordWithMap.codec.encoder.toBsonValue(original)
        val decoded  =
          RecordWithMap.codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == original)
      },
      test("empty map") {
        val original = RecordWithMap(Map.empty)
        val encoded  = RecordWithMap.codec.encoder.toBsonValue(original)
        val decoded  =
          RecordWithMap.codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == original)
      }
    ),
    suite("Map[String, String]")(
      test("encode map with string values") {
        val value     = RecordWithStringMap(Map("name" -> "Alice", "city" -> "NYC"))
        val bsonValue = RecordWithStringMap.codec.encoder.toBsonValue(value)

        val labelsDoc = bsonValue.asDocument().getDocument("labels")
        assertTrue(labelsDoc.getString("name").getValue() == "Alice") &&
        assertTrue(labelsDoc.getString("city").getValue() == "NYC")
      },
      test("decode map with string values") {
        val labelsDoc = new BsonDocument()
        labelsDoc.put("color", new BsonString("blue"))
        labelsDoc.put("size", new BsonString("large"))

        val doc = new BsonDocument()
        doc.put("labels", labelsDoc)

        val decoded =
          RecordWithStringMap.codec.decoder.fromBsonValueUnsafe(doc, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded.labels == Map("color" -> "blue", "size" -> "large"))
      },
      test("round trip map with string values") {
        val original = RecordWithStringMap(Map("key1" -> "value1", "key2" -> "value2"))
        val encoded  = RecordWithStringMap.codec.encoder.toBsonValue(original)
        val decoded  =
          RecordWithStringMap.codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == original)
      }
    )
  )
}
