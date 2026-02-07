package zio.blocks.openapi

import zio.blocks.schema._
import zio.blocks.schema.json.JsonSchema
import zio.test._

object SchemaToOpenAPISpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Schema[A].toOpenAPISchema")(
    suite("primitive types")(
      test("converts Schema[String] to OpenAPI string schema") {
        val schema        = Schema[String]
        val openAPISchema = schema.toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.toJsonSchema.exists(_.isInstanceOf[JsonSchema.Object]),
          openAPISchema.discriminator.isEmpty
        )
      },
      test("converts Schema[Int] to OpenAPI integer schema") {
        val schema        = Schema[Int]
        val openAPISchema = schema.toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.toJsonSchema.exists(_.isInstanceOf[JsonSchema.Object]),
          openAPISchema.discriminator.isEmpty
        )
      },
      test("converts Schema[Boolean] to OpenAPI boolean schema") {
        val schema        = Schema[Boolean]
        val openAPISchema = schema.toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.discriminator.isEmpty
        )
      },
      test("converts Schema[Double] to OpenAPI number schema") {
        val schema        = Schema[Double]
        val openAPISchema = schema.toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.discriminator.isEmpty
        )
      }
    ),
    suite("case classes")(
      test("converts simple case class to OpenAPI object schema") {
        case class Person(name: String, age: Int)
        object Person { implicit val schema: Schema[Person] = Schema.derived }

        val openAPISchema = Schema[Person].toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.toJsonSchema.exists(_.isInstanceOf[JsonSchema.Object]),
          openAPISchema.discriminator.isEmpty
        )
      },
      test("converts nested case class to OpenAPI object schema") {
        case class Address(street: String, city: String)
        case class Person(name: String, address: Address)
        object Address { implicit val schema: Schema[Address] = Schema.derived }
        object Person  { implicit val schema: Schema[Person] = Schema.derived  }

        val openAPISchema = Schema[Person].toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.toJsonSchema.exists(_.isInstanceOf[JsonSchema.Object]),
          openAPISchema.discriminator.isEmpty
        )
      },
      test("converts case class with optional fields to OpenAPI schema") {
        case class User(name: String, email: Option[String])
        object User { implicit val schema: Schema[User] = Schema.derived }

        val openAPISchema = Schema[User].toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.toJsonSchema.exists(_.isInstanceOf[JsonSchema.Object]),
          openAPISchema.discriminator.isEmpty
        )
      }
    ),
    suite("collections")(
      test("converts Schema[List[A]] to OpenAPI array schema") {
        val schema        = Schema[List[String]]
        val openAPISchema = schema.toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.toJsonSchema.exists(_.isInstanceOf[JsonSchema.Object]),
          openAPISchema.discriminator.isEmpty
        )
      },
      test("converts Schema[Vector[A]] to OpenAPI array schema") {
        val schema        = Schema[Vector[Int]]
        val openAPISchema = schema.toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.discriminator.isEmpty
        )
      },
      test("converts Schema[Set[A]] to OpenAPI array schema") {
        val schema        = Schema[Set[String]]
        val openAPISchema = schema.toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.discriminator.isEmpty
        )
      }
    ),
    suite("sealed traits")(
      test("converts sealed trait to OpenAPI oneOf schema with discriminator") {
        sealed trait Animal
        case class Dog(name: String, breed: String)   extends Animal
        case class Cat(name: String, indoor: Boolean) extends Animal

        object Animal { implicit val schema: Schema[Animal] = Schema.derived }
        object Dog    { implicit val schema: Schema[Dog] = Schema.derived    }
        object Cat    { implicit val schema: Schema[Cat] = Schema.derived    }

        val openAPISchema = Schema[Animal].toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.toJsonSchema.exists(_.isInstanceOf[JsonSchema.Object])
        )
      },
      test("converts sealed trait with single case to OpenAPI schema") {
        sealed trait Result
        case class Success(value: String) extends Result

        object Result  { implicit val schema: Schema[Result] = Schema.derived  }
        object Success { implicit val schema: Schema[Success] = Schema.derived }

        val openAPISchema = Schema[Result].toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.discriminator.isEmpty
        )
      }
    ),
    suite("enums")(
      test("converts Scala 3 enum to OpenAPI string schema with enum values") {
        enum Status {
          case Active, Inactive, Pending
        }

        implicit val statusSchema: Schema[Status] = Schema.derived

        val openAPISchema = Schema[Status].toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.discriminator.isEmpty
        )
      }
    ),
    suite("round-trip conversions")(
      test("Schema -> OpenAPI -> JsonSchema preserves structure") {
        case class Product(id: Long, name: String, price: Double)
        object Product { implicit val schema: Schema[Product] = Schema.derived }

        val openAPISchema = Schema[Product].toOpenAPISchema
        val jsonSchema    = openAPISchema.toJsonSchema

        assertTrue(
          jsonSchema.isRight,
          jsonSchema.exists(_.isInstanceOf[JsonSchema.Object])
        )
      },
      test("round-trip through DynamicValue works") {
        case class Item(name: String, quantity: Int)
        object Item { implicit val schema: Schema[Item] = Schema.derived }

        val original      = Item("Widget", 42)
        val openAPISchema = Schema[Item].toOpenAPISchema

        val dv     = Schema[Item].toDynamicValue(original)
        val result = Schema[Item].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.name == "Widget"),
          result.exists(_.quantity == 42),
          openAPISchema.toJsonSchema.isRight
        )
      }
    ),
    suite("complex types")(
      test("converts Schema[Option[A]] to OpenAPI schema") {
        val schema        = Schema[Option[String]]
        val openAPISchema = schema.toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.discriminator.isEmpty
        )
      },
      test("converts Schema[Either[A, B]] to OpenAPI oneOf schema") {
        val schema        = Schema[Either[String, Int]]
        val openAPISchema = schema.toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.toJsonSchema.exists(_.isInstanceOf[JsonSchema.Object])
        )
      },
      test("converts Schema[Map[String, A]] to OpenAPI object schema") {
        val schema        = Schema[Map[String, Int]]
        val openAPISchema = schema.toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.discriminator.isEmpty
        )
      }
    ),
    suite("integration with existing SchemaObject")(
      test("toOpenAPISchema produces same result as manual conversion") {
        case class Book(title: String, author: String, year: Int)
        object Book { implicit val schema: Schema[Book] = Schema.derived }

        val viaExtension = Schema[Book].toOpenAPISchema
        val viaManual    = SchemaObject.fromJsonSchema(Schema[Book].toJsonSchema)

        assertTrue(
          viaExtension.toJsonSchema == viaManual.toJsonSchema,
          viaExtension.discriminator == viaManual.discriminator,
          viaExtension.xml == viaManual.xml,
          viaExtension.externalDocs == viaManual.externalDocs,
          viaExtension.example == viaManual.example,
          viaExtension.extensions == viaManual.extensions
        )
      }
    ),
    suite("edge cases")(
      test("converts generic schema to OpenAPI schema") {
        case class Container[A](value: A)
        object Container {
          implicit def schema[A: Schema]: Schema[Container[A]] = Schema.derived
        }

        val openAPISchema = Schema[Container[String]].toOpenAPISchema

        assertTrue(
          openAPISchema.toJsonSchema.isRight,
          openAPISchema.discriminator.isEmpty
        )
      },
      test("preserves OpenAPI-specific vocabulary when not set by toOpenAPISchema") {
        case class SimpleData(id: Int)
        object SimpleData { implicit val schema: Schema[SimpleData] = Schema.derived }

        val openAPISchema = Schema[SimpleData].toOpenAPISchema

        assertTrue(
          openAPISchema.discriminator.isEmpty,
          openAPISchema.xml.isEmpty,
          openAPISchema.externalDocs.isEmpty,
          openAPISchema.example.isEmpty,
          openAPISchema.extensions.isEmpty
        )
      }
    )
  )
}
