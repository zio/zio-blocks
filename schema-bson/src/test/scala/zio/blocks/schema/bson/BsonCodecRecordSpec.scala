package zio.blocks.schema.bson

import org.bson.BsonDocument
import zio.bson._
import zio.blocks.schema._
import zio.test._

object BsonCodecRecordSpec extends ZIOSpecDefault {

  case class SimpleRecord(name: String, age: Int)

  object SimpleRecord {
    val schema: Schema[SimpleRecord]   = Schema.derived[SimpleRecord]
    val codec: BsonCodec[SimpleRecord] = BsonSchemaCodec.bsonCodec(schema)
  }

  case class NestedRecord(id: Int, simple: SimpleRecord)

  object NestedRecord {
    val schema: Schema[NestedRecord]   = Schema.derived[NestedRecord]
    val codec: BsonCodec[NestedRecord] = BsonSchemaCodec.bsonCodec(schema)
  }

  def spec = suite("BsonCodecRecordSpec")(
    suite("simple record")(
      test("encode simple record") {
        val value     = SimpleRecord("Alice", 30)
        val bsonValue = SimpleRecord.codec.encoder.toBsonValue(value)

        assertTrue(bsonValue.isDocument()) &&
        assertTrue(bsonValue.asDocument().getString("name").getValue() == "Alice") &&
        assertTrue(bsonValue.asDocument().getInt32("age").getValue() == 30)
      },
      test("decode simple record") {
        val doc = new BsonDocument()
        doc.put("name", new org.bson.BsonString("Bob"))
        doc.put("age", new org.bson.BsonInt32(25))

        val decoded = SimpleRecord.codec.decoder.fromBsonValueUnsafe(doc, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded.name == "Bob") &&
        assertTrue(decoded.age == 25)
      },
      test("round trip simple record") {
        val original = SimpleRecord("Charlie", 35)
        val encoded  = SimpleRecord.codec.encoder.toBsonValue(original)
        val decoded  =
          SimpleRecord.codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == original)
      }
    ),
    suite("nested record")(
      test("encode nested record") {
        val value     = NestedRecord(1, SimpleRecord("Dave", 40))
        val bsonValue = NestedRecord.codec.encoder.toBsonValue(value)

        assertTrue(bsonValue.isDocument()) &&
        assertTrue(bsonValue.asDocument().getInt32("id").getValue() == 1) &&
        assertTrue(bsonValue.asDocument().getDocument("simple").getString("name").getValue() == "Dave") &&
        assertTrue(bsonValue.asDocument().getDocument("simple").getInt32("age").getValue() == 40)
      },
      test("decode nested record") {
        val simpleDoc = new BsonDocument()
        simpleDoc.put("name", new org.bson.BsonString("Eve"))
        simpleDoc.put("age", new org.bson.BsonInt32(28))

        val doc = new BsonDocument()
        doc.put("id", new org.bson.BsonInt32(2))
        doc.put("simple", simpleDoc)

        val decoded = NestedRecord.codec.decoder.fromBsonValueUnsafe(doc, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded.id == 2) &&
        assertTrue(decoded.simple.name == "Eve") &&
        assertTrue(decoded.simple.age == 28)
      },
      test("round trip nested record") {
        val original = NestedRecord(3, SimpleRecord("Frank", 45))
        val encoded  = NestedRecord.codec.encoder.toBsonValue(original)
        val decoded  =
          NestedRecord.codec.decoder.fromBsonValueUnsafe(encoded, Nil, BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == original)
      }
    )
  )
}
