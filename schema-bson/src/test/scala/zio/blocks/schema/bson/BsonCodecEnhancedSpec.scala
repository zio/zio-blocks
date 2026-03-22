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

/**
 * Enhanced comprehensive tests demonstrating the full test infrastructure.
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
object BsonCodecEnhancedSpec extends SchemaBaseSpec {

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
      test("encodes true") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.boolean)
        val bson  = codec.encoder.toBsonValue(true)
        assertTrue(bson.asBoolean().getValue)
      },
      test("encodes false") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.boolean)
        val bson  = codec.encoder.toBsonValue(false)
        assertTrue(!bson.asBoolean().getValue)
      },
      test("round-trip true - toBsonValue/as path") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.boolean)
        assertTrue(BsonTestHelpers.roundTripToBsonValueAs(true, codec))
      },
      test("round-trip true - writer/reader path") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.boolean)
        assertTrue(BsonTestHelpers.roundTripWriterReader(true, codec, isDocument = false))
      },
      test("round-trip false - toBsonValue/as path") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.boolean)
        assertTrue(BsonTestHelpers.roundTripToBsonValueAs(false, codec))
      },
      test("round-trip false - writer/reader path") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.boolean)
        assertTrue(BsonTestHelpers.roundTripWriterReader(false, codec, isDocument = false))
      },
      test("round-trip property test - toBsonValue/as") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.boolean)
        check(Gen.boolean) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value, codec))
        }
      },
      test("round-trip property test - writer/reader") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.boolean)
        check(Gen.boolean) { value =>
          assertTrue(BsonTestHelpers.roundTripWriterReader(value, codec, isDocument = false))
        }
      }
    ),
    suite("Primitives - enhanced")(
      test("Int - toBsonValue/as path") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.int)
        check(Gen.int) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value, codec))
        }
      },
      test("Int - writer/reader path") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.int)
        check(Gen.int) { value =>
          assertTrue(BsonTestHelpers.roundTripWriterReader(value, codec, isDocument = false))
        }
      },
      test("String - toBsonValue/as path") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.string)
        check(Gen.alphaNumericStringBounded(0, 50)) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value, codec))
        }
      },
      test("String - writer/reader path") {
        val codec = BsonSchemaCodec.bsonCodec(Schema.string)
        check(Gen.alphaNumericStringBounded(0, 50)) { value =>
          assertTrue(BsonTestHelpers.roundTripWriterReader(value, codec, isDocument = false))
        }
      },
      test("BigDecimal - toBsonValue/as path") {
        val codec         = BsonSchemaCodec.bsonCodec(Schema.bigDecimal)
        val genBigDecimal = Gen.double.map(d => BigDecimal(d))
        check(genBigDecimal) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value, codec))
        }
      },
      test("BigDecimal - writer/reader path") {
        val codec         = BsonSchemaCodec.bsonCodec(Schema.bigDecimal)
        val genBigDecimal = Gen.double.map(d => BigDecimal(d))
        check(genBigDecimal) { value =>
          assertTrue(BsonTestHelpers.roundTripWriterReader(value, codec, isDocument = false))
        }
      }
    ),
    suite("Records - enhanced")(
      test("Person - toBsonValue/as path") {
        val codec     = BsonSchemaCodec.bsonCodec(Person.schema)
        val genPerson = for {
          name <- Gen.alphaNumericStringBounded(3, 20)
          age  <- Gen.int(0, 120)
        } yield Person(name, age)
        check(genPerson) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value, codec))
        }
      },
      test("Person - writer/reader path") {
        val codec     = BsonSchemaCodec.bsonCodec(Person.schema)
        val genPerson = for {
          name <- Gen.alphaNumericStringBounded(3, 20)
          age  <- Gen.int(0, 120)
        } yield Person(name, age)
        check(genPerson) { value =>
          assertTrue(BsonTestHelpers.roundTripWriterReader(value, codec, isDocument = true))
        }
      }
    ),
    suite("Variants - enhanced")(
      test("Color enum - toBsonValue/as path") {
        val codec    = BsonSchemaCodec.bsonCodec(Color.schema)
        val genColor = Gen.elements[Color](Red, Green, Blue)
        check(genColor) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value, codec))
        }
      },
      test("Color enum - writer/reader path") {
        val codec    = BsonSchemaCodec.bsonCodec(Color.schema)
        val genColor = Gen.elements[Color](Red, Green, Blue)
        check(genColor) { value =>
          assertTrue(BsonTestHelpers.roundTripWriterReader(value, codec, isDocument = false))
        }
      }
    ),
    suite("Sequences - enhanced")(
      test("List[Int] - toBsonValue/as path") {
        val genList       = Gen.listOfBounded(0, 10)(Gen.int)
        val listIntSchema = Schema[List[Int]]
        val codec         = BsonSchemaCodec.bsonCodec(listIntSchema)
        check(genList) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value, codec))
        }
      },
      test("List[Int] - writer/reader path") {
        val genList       = Gen.listOfBounded(0, 10)(Gen.int)
        val listIntSchema = Schema[List[Int]]
        val codec         = BsonSchemaCodec.bsonCodec(listIntSchema)
        check(genList) { value =>
          assertTrue(BsonTestHelpers.roundTripWriterReader(value, codec, isDocument = false))
        }
      },
      test("Vector[String] - toBsonValue/as path") {
        val genVector          = Gen.listOfBounded(0, 10)(Gen.alphaNumericStringBounded(1, 20)).map(_.toVector)
        val vectorStringSchema = Schema[Vector[String]]
        val codec              = BsonSchemaCodec.bsonCodec(vectorStringSchema)
        check(genVector) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value, codec))
        }
      },
      test("Vector[String] - writer/reader path") {
        val genVector          = Gen.listOfBounded(0, 10)(Gen.alphaNumericStringBounded(1, 20)).map(_.toVector)
        val vectorStringSchema = Schema[Vector[String]]
        val codec              = BsonSchemaCodec.bsonCodec(vectorStringSchema)
        check(genVector) { value =>
          assertTrue(BsonTestHelpers.roundTripWriterReader(value, codec, isDocument = false))
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
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value, codec))
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
          assertTrue(BsonTestHelpers.roundTripWriterReader(value, codec, isDocument = true))
        }
      }
    ),
    suite("Options - enhanced")(
      test("Option[Int] - toBsonValue/as path") {
        val genOption    = Gen.option(Gen.int)
        val optionSchema = Schema[Option[Int]]
        val codec        = BsonSchemaCodec.bsonCodec(optionSchema)
        check(genOption) { value =>
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value, codec))
        }
      },
      test("Option[Int] - writer/reader path") {
        val genOption    = Gen.option(Gen.int)
        val optionSchema = Schema[Option[Int]]
        val codec        = BsonSchemaCodec.bsonCodec(optionSchema)
        check(genOption) { value =>
          assertTrue(BsonTestHelpers.roundTripWriterReader(value, codec, isDocument = false))
        }
      }
    ),
    suite("Recursive types - enhanced")(
      test("Tree - toBsonValue/as path") {
        val codec = BsonSchemaCodec.bsonCodec(Tree.schema)

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
          assertTrue(BsonTestHelpers.roundTripToBsonValueAs(value, codec))
        }
      },
      test("Tree - writer/reader path") {
        val codec = BsonSchemaCodec.bsonCodec(Tree.schema)

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
          assertTrue(BsonTestHelpers.roundTripWriterReader(value, codec, isDocument = true))
        }
      }
    )
  )
}
