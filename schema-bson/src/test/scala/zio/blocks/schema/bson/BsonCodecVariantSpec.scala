package zio.blocks.schema.bson

import org.bson.BsonDocument
import zio.blocks.schema.{Modifier, Schema}
import zio.test._

object BsonCodecVariantSpec extends ZIOSpecDefault {

  // Test data types - Simple enum (case objects)
  sealed trait Color
  object Color {
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color

    implicit val schema: Schema[Color] = Schema.derived[Color]
  }

  // Test data types - Variant with data
  sealed trait Shape
  object Shape {
    final case class Circle(radius: Double)                   extends Shape
    final case class Rectangle(width: Double, height: Double) extends Shape
    final case class Triangle(base: Double, height: Double)   extends Shape

    implicit val schema: Schema[Shape] = Schema.derived[Shape]
  }

  // Test data types - Variant with nested data
  sealed trait Result
  object Result {
    final case class Success(value: String, timestamp: Long) extends Result
    final case class Failure(error: String, code: Int)       extends Result

    implicit val schema: Schema[Result] = Schema.derived[Result]
  }

  // Test data types - Variant with renamed cases
  sealed trait Status
  object Status {
    @Modifier.rename("ok")
    final case class Success(message: String) extends Status
    @Modifier.rename("err")
    final case class Error(message: String) extends Status

    implicit val schema: Schema[Status] = Schema.derived[Status]
  }

  def spec = suite("BsonCodecVariantSpec")(
    suite("WrapperWithClassNameField mode")(
      test("encode/decode Circle") {
        val shape: Shape = Shape.Circle(5.0)
        val codec        = BsonSchemaCodec.bsonCodec(Schema[Shape])

        val encoded = codec.encoder.toBsonValue(shape)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == shape)
      },
      test("encode Circle produces wrapper document") {
        val shape: Shape = Shape.Circle(5.0)
        val codec        = BsonSchemaCodec.bsonCodec(Schema[Shape])

        val encoded = codec.encoder.toBsonValue(shape).asDocument()
        val keys    = scala.collection.mutable.Set.empty[String]
        val iter    = encoded.entrySet().iterator()
        while (iter.hasNext()) {
          keys.add(iter.next().getKey())
        }

        assertTrue(
          keys.contains("Circle"),
          encoded.get("Circle").isDocument()
        )
      },
      test("encode/decode Rectangle") {
        val shape: Shape = Shape.Rectangle(10.0, 20.0)
        val codec        = BsonSchemaCodec.bsonCodec(Schema[Shape])

        val encoded = codec.encoder.toBsonValue(shape)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == shape)
      },
      test("encode/decode Triangle") {
        val shape: Shape = Shape.Triangle(8.0, 6.0)
        val codec        = BsonSchemaCodec.bsonCodec(Schema[Shape])

        val encoded = codec.encoder.toBsonValue(shape)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == shape)
      },
      test("encode/decode Result Success") {
        val result: Result = Result.Success("OK", 123456789L)
        val codec          = BsonSchemaCodec.bsonCodec(Schema[Result])

        val encoded = codec.encoder.toBsonValue(result)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == result)
      },
      test("encode/decode Result Failure") {
        val result: Result = Result.Failure("Error message", 404)
        val codec          = BsonSchemaCodec.bsonCodec(Schema[Result])

        val encoded = codec.encoder.toBsonValue(result)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == result)
      },
      test("decode unknown case name fails") {
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Shape])
        val badDoc = BsonDocument.parse("""{"UnknownShape": {"radius": 5.0}}""")

        val result = scala.util.Try {
          codec.decoder.fromBsonValueUnsafe(badDoc, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)
        }

        assertTrue(result.isFailure)
      },
      test("decode empty document fails") {
        val codec    = BsonSchemaCodec.bsonCodec(Schema[Shape])
        val emptyDoc = new BsonDocument()

        val result = scala.util.Try {
          codec.decoder.fromBsonValueUnsafe(emptyDoc, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)
        }

        assertTrue(result.isFailure)
      }
    ),
    suite("DiscriminatorField mode")(
      test("encode/decode Circle with discriminator") {
        val shape: Shape = Shape.Circle(5.0)
        val config       = BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("type")
        )
        val codec = BsonSchemaCodec.bsonCodec(Schema[Shape], config)

        val encoded = codec.encoder.toBsonValue(shape)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == shape)
      },
      test("encode Circle with discriminator produces flat document") {
        val shape: Shape = Shape.Circle(5.0)
        val config       = BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("type")
        )
        val codec = BsonSchemaCodec.bsonCodec(Schema[Shape], config)

        val encoded     = codec.encoder.toBsonValue(shape).asDocument()
        val typeField   = encoded.get("type")
        val radiusField = encoded.get("radius")

        assertTrue(
          typeField != null && typeField.isString() && typeField.asString().getValue() == "Circle",
          radiusField != null && radiusField.isDouble() && radiusField.asDouble().getValue() == 5.0
        )
      },
      test("encode/decode Rectangle with discriminator") {
        val shape: Shape = Shape.Rectangle(10.0, 20.0)
        val config       = BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("kind")
        )
        val codec = BsonSchemaCodec.bsonCodec(Schema[Shape], config)

        val encoded = codec.encoder.toBsonValue(shape)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == shape)
      },
      test("encode Rectangle with custom discriminator field name") {
        val shape: Shape = Shape.Rectangle(10.0, 20.0)
        val config       = BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("_type")
        )
        val codec = BsonSchemaCodec.bsonCodec(Schema[Shape], config)

        val encoded   = codec.encoder.toBsonValue(shape).asDocument()
        val typeField = encoded.get("_type")

        assertTrue(
          typeField != null && typeField.isString() && typeField.asString().getValue() == "Rectangle"
        )
      },
      test("decode missing discriminator field fails") {
        val config = BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("type")
        )
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Shape], config)
        val badDoc = BsonDocument.parse("""{"radius": 5.0}""")

        val result = scala.util.Try {
          codec.decoder.fromBsonValueUnsafe(badDoc, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)
        }

        assertTrue(result.isFailure)
      },
      test("decode unknown discriminator value fails") {
        val config = BsonSchemaCodec.Config.withSumTypeHandling(
          BsonSchemaCodec.SumTypeHandling.DiscriminatorField("type")
        )
        val codec  = BsonSchemaCodec.bsonCodec(Schema[Shape], config)
        val badDoc = BsonDocument.parse("""{"type": "UnknownShape", "radius": 5.0}""")

        val result = scala.util.Try {
          codec.decoder.fromBsonValueUnsafe(badDoc, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)
        }

        assertTrue(result.isFailure)
      }
    ),
    suite("renamed cases")(
      test("encode/decode with renamed cases") {
        val status: Status = Status.Success("All good")
        val codec          = BsonSchemaCodec.bsonCodec(Schema[Status])

        val encoded = codec.encoder.toBsonValue(status)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == status)
      },
      test("encode uses renamed case name") {
        val status: Status = Status.Success("All good")
        val codec          = BsonSchemaCodec.bsonCodec(Schema[Status])

        val encoded = codec.encoder.toBsonValue(status).asDocument()
        val keys    = scala.collection.mutable.Set.empty[String]
        val iter    = encoded.entrySet().iterator()
        while (iter.hasNext()) {
          keys.add(iter.next().getKey())
        }

        assertTrue(
          keys.contains("ok"),
          !keys.contains("Success")
        )
      }
    ),
    suite("class name mapping")(
      test("encode/decode with class name mapper") {
        val shape: Shape = Shape.Circle(5.0)
        val config       = BsonSchemaCodec.Config.withClassNameMapping(_.toUpperCase())
        val codec        = BsonSchemaCodec.bsonCodec(Schema[Shape], config)

        val encoded = codec.encoder.toBsonValue(shape)
        val decoded = codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == shape)
      },
      test("encode applies class name mapper") {
        val shape: Shape = Shape.Circle(5.0)
        val config       = BsonSchemaCodec.Config.withClassNameMapping(_.toLowerCase())
        val codec        = BsonSchemaCodec.bsonCodec(Schema[Shape], config)

        val encoded = codec.encoder.toBsonValue(shape).asDocument()
        val keys    = scala.collection.mutable.Set.empty[String]
        val iter    = encoded.entrySet().iterator()
        while (iter.hasNext()) {
          keys.add(iter.next().getKey())
        }

        assertTrue(
          keys.contains("circle"),
          !keys.contains("Circle")
        )
      }
    )
  )
}
