package zio.blocks.schema.bson

import zio.blocks.schema.Schema
import zio.test._

object BsonCodecGenericSpec extends ZIOSpecDefault {

  // Simple generic wrapper
  case class SimpleGeneric[T](value: T)

  // Generic recursive tree structure
  sealed trait GenericTree[T]
  object GenericTree {
    final case class Branch[T](left: GenericTree[T], right: GenericTree[T]) extends GenericTree[T]
    final case class Leaf[T](value: T)                                      extends GenericTree[T]

    // Generator for GenericTree[T]
    private def genLeafOf[R, A](gen: Gen[R, A]): Gen[R, GenericTree[A]] = gen.map(Leaf(_))

    def genOf[R, A](gen: Gen[R, A]): Gen[R with Sized, GenericTree[A]] = Gen.sized { size =>
      if (size >= 2)
        Gen.oneOf(
          genLeafOf(gen),
          Gen.suspend(genOf(gen).zipWith(genOf(gen))(Branch(_, _))).resize(size / 2)
        )
      else genLeafOf(gen)
    }
  }

  // Generic recursive type with Option
  case class GenericRec[T](t: T, next: Option[GenericRec[T]])
  object GenericRec {
    // Generator for GenericRec[T]
    def genOf[R, A](gen: Gen[R, A]): Gen[R with Sized, GenericRec[A]] = Gen.sized { size =>
      if (size <= 1) gen.map(a => GenericRec(a, None))
      else
        for {
          a    <- gen
          next <- Gen.oneOf(Gen.const(None), genOf(gen).resize(size - 1).map(Some(_)))
        } yield GenericRec(a, next)
    }
  }

  def spec = suite("BsonCodecGenericSpec")(
    suite("SimpleGeneric[T]")(
      test("encode/decode SimpleGeneric[String]") {
        val schema                       = Schema.derived[SimpleGeneric[String]]
        val value: SimpleGeneric[String] = SimpleGeneric("test")
        val codec                        = BsonSchemaCodec.bsonCodec(schema)

        val encoded = codec.encoder.toBsonValue(value)
        val decoded =
          codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == value)
      },
      test("encode/decode SimpleGeneric[Int]") {
        val schema                    = Schema.derived[SimpleGeneric[Int]]
        val value: SimpleGeneric[Int] = SimpleGeneric(42)
        val codec                     = BsonSchemaCodec.bsonCodec(schema)

        val encoded = codec.encoder.toBsonValue(value)
        val decoded =
          codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == value)
      },
      test("property-based: SimpleGeneric[String]") {
        val schema = Schema.derived[SimpleGeneric[String]]
        val codec  = BsonSchemaCodec.bsonCodec(schema)

        check(Gen.string) { str =>
          val value: SimpleGeneric[String] = SimpleGeneric(str)

          val encoded = codec.encoder.toBsonValue(value)
          val decoded =
            codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

          assertTrue(decoded == value)
        }
      },
      test("property-based: SimpleGeneric[Int]") {
        val schema = Schema.derived[SimpleGeneric[Int]]
        val codec  = BsonSchemaCodec.bsonCodec(schema)

        check(Gen.int) { n =>
          val value: SimpleGeneric[Int] = SimpleGeneric(n)

          val encoded = codec.encoder.toBsonValue(value)
          val decoded =
            codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

          assertTrue(decoded == value)
        }
      }
    ),
    suite("GenericTree[T]")(
      test("encode/decode Leaf") {
        val schema                 = Schema.derived[GenericTree[Int]]
        val tree: GenericTree[Int] = GenericTree.Leaf(42)
        val codec                  = BsonSchemaCodec.bsonCodec(schema)

        val encoded = codec.encoder.toBsonValue(tree)
        val decoded =
          codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == tree)
      },
      test("encode/decode Branch") {
        val schema                    = Schema.derived[GenericTree[String]]
        val tree: GenericTree[String] = GenericTree.Branch(
          GenericTree.Leaf("left"),
          GenericTree.Leaf("right")
        )
        val codec = BsonSchemaCodec.bsonCodec(schema)

        val encoded = codec.encoder.toBsonValue(tree)
        val decoded =
          codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == tree)
      },
      test("encode/decode nested tree") {
        val schema                 = Schema.derived[GenericTree[Int]]
        val tree: GenericTree[Int] = GenericTree.Branch(
          GenericTree.Branch(GenericTree.Leaf(1), GenericTree.Leaf(2)),
          GenericTree.Leaf(3)
        )
        val codec = BsonSchemaCodec.bsonCodec(schema)

        val encoded = codec.encoder.toBsonValue(tree)
        val decoded =
          codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == tree)
      },
      test("property-based: GenericTree[Int]") {
        val schema = Schema.derived[GenericTree[Int]]
        val codec  = BsonSchemaCodec.bsonCodec(schema)

        check(GenericTree.genOf(Gen.int)) { tree =>
          val encoded = codec.encoder.toBsonValue(tree)
          val decoded =
            codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

          assertTrue(decoded == tree)
        }
      },
      test("property-based: GenericTree[String]") {
        val schema = Schema.derived[GenericTree[String]]
        val codec  = BsonSchemaCodec.bsonCodec(schema)

        check(GenericTree.genOf(Gen.string)) { tree =>
          val encoded = codec.encoder.toBsonValue(tree)
          val decoded =
            codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

          assertTrue(decoded == tree)
        }
      }
    ),
    suite("GenericRec[T]")(
      test("encode/decode single element") {
        val schema                  = Schema.derived[GenericRec[String]]
        val rec: GenericRec[String] = GenericRec("first", None)
        val codec                   = BsonSchemaCodec.bsonCodec(schema)

        val encoded = codec.encoder.toBsonValue(rec)
        val decoded =
          codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == rec)
      },
      test("encode/decode chain") {
        val schema               = Schema.derived[GenericRec[Int]]
        val rec: GenericRec[Int] = GenericRec(1, Some(GenericRec(2, Some(GenericRec(3, None)))))
        val codec                = BsonSchemaCodec.bsonCodec(schema)

        val encoded = codec.encoder.toBsonValue(rec)
        val decoded =
          codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

        assertTrue(decoded == rec)
      },
      test("property-based: GenericRec[Int]") {
        val schema = Schema.derived[GenericRec[Int]]
        val codec  = BsonSchemaCodec.bsonCodec(schema)

        check(GenericRec.genOf(Gen.int)) { rec =>
          val encoded = codec.encoder.toBsonValue(rec)
          val decoded =
            codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

          assertTrue(decoded == rec)
        }
      },
      test("property-based: GenericRec[String]") {
        val schema = Schema.derived[GenericRec[String]]
        val codec  = BsonSchemaCodec.bsonCodec(schema)

        check(GenericRec.genOf(Gen.string)) { rec =>
          val encoded = codec.encoder.toBsonValue(rec)
          val decoded =
            codec.decoder.fromBsonValueUnsafe(encoded, Nil, zio.bson.BsonDecoder.BsonDecoderContext.default)

          assertTrue(decoded == rec)
        }
      }
    )
  )
}
