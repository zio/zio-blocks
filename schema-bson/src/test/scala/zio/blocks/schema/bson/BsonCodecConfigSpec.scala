package zio.blocks.schema.bson

import zio.blocks.schema._
import zio.bson._
import zio.bson.BsonBuilder._
import zio.test._

object BsonCodecConfigSpec extends ZIOSpecDefault {

  // Test cases for extra field validation
  case class SimpleRecord(a: String)

  object SimpleRecord {
    val schema: Schema[SimpleRecord] = Schema.derived[SimpleRecord]
  }

  case class RecordWithDefault(a: String, b: Int = 42)

  object RecordWithDefault {
    val schema: Schema[RecordWithDefault] = Schema.derived[RecordWithDefault]
  }

  def spec: Spec[Any, Any] = suite("BsonCodecConfigSpec")(
    suite("extra field validation")(
      test("allow extra fields by default") {
        val bson    = doc("a" -> str("test"), "extra" -> str("ignored"))
        val codec   = BsonSchemaCodec.bsonCodec(SimpleRecord.schema, BsonSchemaCodec.Config)
        val decoded = bson.as[SimpleRecord](codec.decoder)
        assertTrue(decoded == Right(SimpleRecord("test")))
      },
      test("allow extra fields when configured") {
        val bson    = doc("a" -> str("test"), "extra" -> str("ignored"))
        val config  = BsonSchemaCodec.Config.withIgnoreExtraFields(true)
        val codec   = BsonSchemaCodec.bsonCodec(SimpleRecord.schema, config)
        val decoded = bson.as[SimpleRecord](codec.decoder)
        assertTrue(decoded == Right(SimpleRecord("test")))
      },
      test("reject extra fields when configured") {
        val bson    = doc("a" -> str("test"), "extra" -> str("unexpected"))
        val config  = BsonSchemaCodec.Config.withIgnoreExtraFields(false)
        val codec   = BsonSchemaCodec.bsonCodec(SimpleRecord.schema, config)
        val decoded = bson.as[SimpleRecord](codec.decoder)
        assertTrue(decoded.isLeft && decoded.left.exists(_.contains("extra")))
      },
      test("DiscriminatorField mode works with strict validation") {
        sealed trait StrictSum
        case class StrictCase(a: Int) extends StrictSum
        object StrictSum { val schema: Schema[StrictSum] = Schema.derived[StrictSum] }

        val value = StrictCase(123)
        // Configure: Use "mk_type" as discriminator, and FAIL on extra fields
        val config = BsonSchemaCodec.Config
          .withSumTypeHandling(BsonSchemaCodec.SumTypeHandling.DiscriminatorField("mk_type"))
          .withIgnoreExtraFields(false)

        val codec = BsonSchemaCodec.bsonCodec(StrictSum.schema, config)

        // When we encode, we get { "mk_type": "StrictCase", "a": 123 }
        val encoded = codec.encoder.toBsonValue(value)
        val decoded = encoded.as[StrictSum](codec.decoder)

        assertTrue(decoded == Right(value))
      }
    ),
    suite("field defaults")(
      test("use default value when field is missing") {
        val bson    = doc("a" -> str("test"))
        val codec   = BsonSchemaCodec.bsonCodec(RecordWithDefault.schema, BsonSchemaCodec.Config)
        val decoded = bson.as[RecordWithDefault](codec.decoder)
        assertTrue(decoded == Right(RecordWithDefault("test", 42)))
      },
      test("override default value when field is present") {
        val bson    = doc("a" -> str("test"), "b" -> int(100))
        val codec   = BsonSchemaCodec.bsonCodec(RecordWithDefault.schema, BsonSchemaCodec.Config)
        val decoded = bson.as[RecordWithDefault](codec.decoder)
        assertTrue(decoded == Right(RecordWithDefault("test", 100)))
      }
    ),
    suite("discriminator modes")(
      test("NoDiscriminator mode") {
        sealed trait Payment
        case class CreditCard(number: String, cvc: Int) extends Payment
        case class PayPal(email: String)                extends Payment
        object Payment {
          val schema: Schema[Payment] = Schema.derived[Payment]
        }

        val cc = CreditCard("1234", 123)
        val pp = PayPal("user@example.com")

        val config = BsonSchemaCodec.Config.withSumTypeHandling(BsonSchemaCodec.SumTypeHandling.NoDiscriminator)
        val codec  = BsonSchemaCodec.bsonCodec(Payment.schema, config)

        val encodedCC = codec.encoder.toBsonValue(cc)

        val decodedCC = encodedCC.as[Payment](codec.decoder)

        val encodedPP = codec.encoder.toBsonValue(pp)
        val decodedPP = encodedPP.as[Payment](codec.decoder)

        assertTrue(
          encodedCC.asDocument().containsKey("number"),
          !encodedCC.asDocument().containsKey("CreditCard"),
          !encodedCC.asDocument().containsKey("type"),
          decodedCC == Right(cc),
          decodedPP == Right(pp)
        )
      }
    ),
    suite("modifiers")(
      test("rename field") {
        case class RenamedField(@Modifier.rename("new_name") field: String)
        object RenamedField { val schema: Schema[RenamedField] = Schema.derived[RenamedField] }

        val obj   = RenamedField("value")
        val codec = BsonSchemaCodec.bsonCodec(RenamedField.schema, BsonSchemaCodec.Config)
        val bson  = codec.encoder.toBsonValue(obj)

        assertTrue(
          bson.asDocument().containsKey("new_name"),
          !bson.asDocument().containsKey("field")
        )
      },
      test("transient field") {
        case class TransientField(@Modifier.transient field: String = "default", keep: Int)
        object TransientField { val schema: Schema[TransientField] = Schema.derived[TransientField] }

        val obj     = TransientField("hidden", 10)
        val codec   = BsonSchemaCodec.bsonCodec(TransientField.schema, BsonSchemaCodec.Config)
        val bson    = codec.encoder.toBsonValue(obj)
        val decoded = bson.as[TransientField](codec.decoder)

        assertTrue(
          !bson.asDocument().containsKey("field"),
          bson.asDocument().containsKey("keep"),
          decoded == Right(TransientField("default", 10))
        )
      }
    ),
    suite("class name mapping")(
      test("custom class name mapping") {
        sealed trait Payment
        case class CreditCard(number: String) extends Payment
        object Payment { val schema: Schema[Payment] = Schema.derived[Payment] }

        val cc     = CreditCard("1234")
        val config = BsonSchemaCodec.Config.withClassNameMapping(name => name.toUpperCase)
        val codec  = BsonSchemaCodec.bsonCodec(Payment.schema, config)

        val encoded = codec.encoder.toBsonValue(cc)
        val decoded = encoded.as[Payment](codec.decoder)

        assertTrue(
          encoded.asDocument().containsKey("CREDITCARD"),
          !encoded.asDocument().containsKey("CreditCard"),
          decoded == Right(cc)
        )
      }
    ),
    suite("case name aliases")(
      test("decode using alias on case") {
        sealed trait Status
        @Modifier.alias("Alive")
        case class Active(since: Int) extends Status
        object Status { val schema: Schema[Status] = Schema.derived[Status] }

        val active = Active(2022)
        val codec  = BsonSchemaCodec.bsonCodec(Status.schema, BsonSchemaCodec.Config)

        // Construct BSON using alias "Alive"
        val bson = doc("Alive" -> doc("since" -> int(2022)))

        val decoded = bson.as[Status](codec.decoder)

        assertTrue(decoded == Right(active))
      }
    ),
    suite("transient cases")(
      test("transient case is encoded as empty document") {
        sealed trait Cache
        case class InMemory(data: String) extends Cache
        @Modifier.transient
        case class Disk(path: String) extends Cache
        object Cache { val schema: Schema[Cache] = Schema.derived[Cache] }

        val disk  = Disk("/tmp")
        val mem   = InMemory("foo")
        val codec = BsonSchemaCodec.bsonCodec(Cache.schema, BsonSchemaCodec.Config)

        val encodedDisk = codec.encoder.toBsonValue(disk)
        val encodedMem  = codec.encoder.toBsonValue(mem)

        val memDoc = encodedMem.asDocument()

        assertTrue(
          encodedDisk == new org.bson.BsonDocument(),
          memDoc.containsKey("InMemory"),
          memDoc.get("InMemory").asDocument().getString("data").getValue == "foo"
        )
      }
    )
  )
}
