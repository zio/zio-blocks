package zio.blocks.schema

import zio.test.Assertion._
import zio.test._

object SchemaVersionSpecificSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("SchemaVersionSpecificSpec")(
    test("compiles when explicit type annotation is used with schema.derive(Format)") {
      typeCheck {
        """
        import zio.blocks.schema._
        import zio.blocks.schema.json.{JsonBinaryCodec, JsonFormat}

        case class Person(name: String, age: Int)

        object Person {
          implicit val schema: Schema[Person] = Schema.derived[Person]
          implicit val jsonCodec: JsonBinaryCodec[Person] = schema.derive(JsonFormat)
        }
        """
      }.map(result => assert(result)(isRight))
    },
    test("compiles when explicit type annotation is used with schema.derive(deriver)") {
      typeCheck {
        """
        import zio.blocks.schema._
        import zio.blocks.schema.json.{JsonBinaryCodec, JsonBinaryCodecDeriver}

        case class Person(name: String, age: Int)

        object Person {
          implicit val schema: Schema[Person] = Schema.derived[Person]
          implicit val jsonCodec: JsonBinaryCodec[Person] = schema.derive(JsonBinaryCodecDeriver)
        }
        """
      }.map(result => assert(result)(isRight))
    },
    test("compiles without explicit type annotation using schema.derive(Format)") {
      typeCheck {
        """
        import zio.blocks.schema._
        import zio.blocks.schema.json.JsonFormat

        case class Person(name: String, age: Int)

        object Person {
          implicit val schema: Schema[Person] = Schema.derived[Person]
          val jsonCodec = schema.derive(JsonFormat)
        }
        """
      }.map(result => assert(result)(isRight))
    },
    test("doesn't generate schema for unsupported classes") {
      typeCheck {
        "Schema.derived[scala.concurrent.duration.Duration]"
      }.map(assert(_)(isLeft(containsString("Cannot find a primary constructor for 'Infinite.this.<local child>'"))))
    },
    test("doesn't generate schema for unsupported collections") {
      typeCheck {
        "Schema.derived[scala.collection.mutable.CollisionProofHashMap[String, Int]]"
      }.map(
        assert(_)(
          isLeft(
            containsString("Cannot derive schema for 'scala.collection.mutable.CollisionProofHashMap[String,Int]'.")
          )
        )
      )
    }
  )
}
