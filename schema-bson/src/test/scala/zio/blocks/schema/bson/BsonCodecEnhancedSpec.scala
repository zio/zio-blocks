package zio.blocks.schema.bson

import zio.blocks.schema.Schema
import zio.test._

/**
 * Enhanced comprehensive tests demonstrating the full test infrastructure. We
 * skip basic roundtrip tests as they are covered in the rest of the tests.
 * Tests both toBsonValue/as path AND BsonWriter/BsonReader path for all major
 * type categories:
 *   - Boolean
 *   - Primitives (Int, String, BigDecimal)
 *   - Records (case classes)
 *   - Variants (sealed traits)
 *   - Sequences (List, Vector)
 *   - Maps
 *   - Options
 *   - Recursive types
 */
object BsonCodecEnhancedSpec extends ZIOSpecDefault {

  // Test data types
  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  sealed trait Color
  case object Red   extends Color
  case object Green extends Color
  case object Blue  extends Color
  object Color {
    implicit val schema: Schema[Color] = Schema.derived
  }

  sealed trait Tree
  case class Branch(left: Tree, right: Tree) extends Tree
  case class Leaf(value: Int)                extends Tree
  object Tree {
    implicit val schema: Schema[Tree] = Schema.derived
  }

  def spec = suite("BsonCodecEnhancedSpec")(
    suite("Boolean type - enhanced testing")(
      // Original simple tests
      test("encodes true") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.boolean)
        val bson  = codec.encoder.toBsonValue(true)
        assertTrue(bson.asBoolean().getValue == true)
      },
      test("encodes false") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.boolean)
        val bson  = codec.encoder.toBsonValue(false)
        assertTrue(bson.asBoolean().getValue == false)
      },

      // Enhanced: test both paths for true
      test("round-trip true - toBsonValue/as path") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.boolean)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        assertTrue(BsonTestHelpers.roundTripToBsonValueAs(true))
      },
      test("round-trip true - writer/reader path") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.boolean)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        // Booleans are not documents, so isDocument = false
        assertZIO(BsonTestHelpers.roundTripWriterReader(true, isDocument = false))(
          Assertion.isTrue
        )
      },

      // Enhanced: test both paths for false
      test("round-trip false - toBsonValue/as path") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.boolean)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        assertTrue(BsonTestHelpers.roundTripToBsonValueAs(false))
      },
      test("round-trip false - writer/reader path") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.boolean)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        assertZIO(BsonTestHelpers.roundTripWriterReader(false, isDocument = false))(
          Assertion.isTrue
        )
      },

      // Property-based test using both paths
      test("round-trip property test - toBsonValue/as") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.boolean)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        check(Gen.boolean) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value))
        }
      },
      test("round-trip property test - writer/reader") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.boolean)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        check(Gen.boolean) { value =>
          assertZIO(BsonTestHelpers.roundTripWriterReader(value, isDocument = false))(
            Assertion.isTrue
          )
        }
      }
    ),
    suite("Primitives - enhanced")(
      test("Int - toBsonValue/as path") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.int)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        check(Gen.int) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value))
        }
      },
      test("Int - writer/reader path") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.int)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        check(Gen.int) { value =>
          assertZIO(BsonTestHelpers.roundTripWriterReader(value, isDocument = false))(
            Assertion.isTrue
          )
        }
      },
      test("String - toBsonValue/as path") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.string)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        check(Gen.alphaNumericStringBounded(0, 50)) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value))
        }
      },
      test("String - writer/reader path") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.string)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        check(Gen.alphaNumericStringBounded(0, 50)) { value =>
          assertZIO(BsonTestHelpers.roundTripWriterReader(value, isDocument = false))(
            Assertion.isTrue
          )
        }
      },
      test("BigDecimal - toBsonValue/as path") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.bigDecimal)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        val genBigDecimal    = Gen.double.map(d => BigDecimal(d))
        check(genBigDecimal) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value))
        }
      },
      test("BigDecimal - writer/reader path") {
        val codec            = BsonSchemaCodec.bsonCodec(Schema.bigDecimal)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder
        val genBigDecimal    = Gen.double.map(d => BigDecimal(d))
        check(genBigDecimal) { value =>
          assertZIO(BsonTestHelpers.roundTripWriterReader(value, isDocument = false))(
            Assertion.isTrue
          )
        }
      }
    ),
    suite("Records - enhanced")(
      test("Person - toBsonValue/as path") {
        val codec            = BsonSchemaCodec.bsonCodec(Person.schema)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder

        val genPerson = for {
          name <- Gen.alphaNumericStringBounded(3, 20)
          age  <- Gen.int(0, 120)
        } yield Person(name, age)

        check(genPerson) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value))
        }
      },
      test("Person - writer/reader path") {
        val codec            = BsonSchemaCodec.bsonCodec(Person.schema)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder

        val genPerson = for {
          name <- Gen.alphaNumericStringBounded(3, 20)
          age  <- Gen.int(0, 120)
        } yield Person(name, age)

        check(genPerson) { value =>
          assertZIO(BsonTestHelpers.roundTripWriterReader(value, isDocument = true))(
            Assertion.isTrue
          )
        }
      }
    ),
    suite("Variants - enhanced")(
      test("Color enum - toBsonValue/as path") {
        val codec            = BsonSchemaCodec.bsonCodec(Color.schema)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder

        val genColor = Gen.elements(Red, Green, Blue)

        check(genColor) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value))
        }
      },
      test("Color enum - writer/reader path") {
        val codec            = BsonSchemaCodec.bsonCodec(Color.schema)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder

        val genColor = Gen.elements(Red, Green, Blue)

        check(genColor) { value =>
          assertZIO(BsonTestHelpers.roundTripWriterReader(value, isDocument = false))(
            Assertion.isTrue
          )
        }
      }
    ),
    suite("Sequences - enhanced")(
      test("List[Int] - toBsonValue/as path") {
        val genList       = Gen.listOfBounded(0, 10)(Gen.int)
        val listIntSchema = Schema[List[Int]]
        val codec         = BsonSchemaCodec.bsonCodec(listIntSchema)

        check(genList) { value =>
          implicit val encoder = codec.encoder
          implicit val decoder = codec.decoder
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value))
        }
      },
      test("List[Int] - writer/reader path") {
        val genList       = Gen.listOfBounded(0, 10)(Gen.int)
        val listIntSchema = Schema[List[Int]]
        val codec         = BsonSchemaCodec.bsonCodec(listIntSchema)

        check(genList) { value =>
          implicit val encoder = codec.encoder
          implicit val decoder = codec.decoder
          assertZIO(BsonTestHelpers.roundTripWriterReader(value, isDocument = false))(
            Assertion.isTrue
          )
        }
      },
      test("Vector[String] - toBsonValue/as path") {
        val genVector          = Gen.listOfBounded(0, 10)(Gen.alphaNumericStringBounded(1, 20)).map(_.toVector)
        val vectorStringSchema = Schema[Vector[String]]
        val codec              = BsonSchemaCodec.bsonCodec(vectorStringSchema)

        check(genVector) { value =>
          implicit val encoder = codec.encoder
          implicit val decoder = codec.decoder
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value))
        }
      },
      test("Vector[String] - writer/reader path") {
        val genVector          = Gen.listOfBounded(0, 10)(Gen.alphaNumericStringBounded(1, 20)).map(_.toVector)
        val vectorStringSchema = Schema[Vector[String]]
        val codec              = BsonSchemaCodec.bsonCodec(vectorStringSchema)

        check(genVector) { value =>
          implicit val encoder = codec.encoder
          implicit val decoder = codec.decoder
          assertZIO(BsonTestHelpers.roundTripWriterReader(value, isDocument = false))(
            Assertion.isTrue
          )
        }
      }
    ),
    suite("Maps - enhanced")(
      test("Map[String, Int] - toBsonValue/as path") {
        val genMap = Gen
          .listOfBounded(0, 5) {
            for {
              key   <- Gen.alphaNumericStringBounded(1, 10)
              value <- Gen.int
            } yield key -> value
          }
          .map(_.toMap)
        val mapSchema = Schema[Map[String, Int]]
        val codec     = BsonSchemaCodec.bsonCodec(mapSchema)

        check(genMap) { value =>
          implicit val encoder = codec.encoder
          implicit val decoder = codec.decoder
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value))
        }
      },
      test("Map[String, Int] - writer/reader path") {
        val genMap = Gen
          .listOfBounded(0, 5) {
            for {
              key   <- Gen.alphaNumericStringBounded(1, 10)
              value <- Gen.int
            } yield key -> value
          }
          .map(_.toMap)
        val mapSchema = Schema[Map[String, Int]]
        val codec     = BsonSchemaCodec.bsonCodec(mapSchema)

        check(genMap) { value =>
          implicit val encoder = codec.encoder
          implicit val decoder = codec.decoder
          assertZIO(BsonTestHelpers.roundTripWriterReader(value, isDocument = true))(
            Assertion.isTrue
          )
        }
      }
    ),
    suite("Options - enhanced")(
      test("Option[Int] - toBsonValue/as path") {
        val genOption    = Gen.option(Gen.int)
        val optionSchema = Schema[Option[Int]]
        val codec        = BsonSchemaCodec.bsonCodec(optionSchema)

        check(genOption) { value =>
          implicit val encoder = codec.encoder
          implicit val decoder = codec.decoder
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value))
        }
      },
      test("Option[Int] - writer/reader path") {
        val genOption    = Gen.option(Gen.int)
        val optionSchema = Schema[Option[Int]]
        val codec        = BsonSchemaCodec.bsonCodec(optionSchema)

        check(genOption) { value =>
          implicit val encoder = codec.encoder
          implicit val decoder = codec.decoder
          assertZIO(BsonTestHelpers.roundTripWriterReader(value, isDocument = false))(
            Assertion.isTrue
          )
        }
      }
    ),
    suite("Recursive types - enhanced")(
      test("Tree - toBsonValue/as path") {
        val codec            = BsonSchemaCodec.bsonCodec(Tree.schema)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder

        def genTree(depth: Int): Gen[Any, Tree] =
          if (depth <= 0) Gen.int.map(Leaf(_))
          else
            Gen.oneOf(
              Gen.int.map(Leaf(_)),
              for {
                left  <- genTree(depth - 1)
                right <- genTree(depth - 1)
              } yield Branch(left, right)
            )

        check(genTree(3)) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value))
        }
      },
      test("Tree - writer/reader path") {
        val codec            = BsonSchemaCodec.bsonCodec(Tree.schema)
        implicit val encoder = codec.encoder
        implicit val decoder = codec.decoder

        def genTree(depth: Int): Gen[Any, Tree] =
          if (depth <= 0) Gen.int.map(Leaf(_))
          else
            Gen.oneOf(
              Gen.int.map(Leaf(_)),
              for {
                left  <- genTree(depth - 1)
                right <- genTree(depth - 1)
              } yield Branch(left, right)
            )

        check(genTree(3)) { value =>
          assertZIO(BsonTestHelpers.roundTripWriterReader(value, isDocument = true))(
            Assertion.isTrue
          )
        }
      }
    )
  )
}
