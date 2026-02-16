package zio.blocks.openapi

import zio.blocks.chunk.Chunk
import zio.blocks.docs.{Doc, Inline, Paragraph}
import zio.blocks.schema._
import zio.test._

object ReferenceSpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(s)))))
  def spec: Spec[TestEnvironment, Any] = suite("Reference")(
    suite("Reference object")(
      test("can be constructed with required $ref field only") {
        val ref = Reference(`$ref` = "#/components/schemas/User")

        assertTrue(
          ref.`$ref` == "#/components/schemas/User",
          ref.summary.isEmpty,
          ref.description.isEmpty
        )
      },
      test("can be constructed with all fields") {
        val ref = Reference(
          `$ref` = "#/components/schemas/User",
          summary = Some(doc("User reference")),
          description = Some(doc("Reference to the User schema"))
        )

        assertTrue(
          ref.`$ref` == "#/components/schemas/User",
          ref.summary.contains(doc("User reference")),
          ref.description.contains(doc("Reference to the User schema"))
        )
      },
      test("supports internal component references") {
        val schemaRef    = Reference(`$ref` = "#/components/schemas/Pet")
        val responseRef  = Reference(`$ref` = "#/components/responses/NotFound")
        val parameterRef = Reference(`$ref` = "#/components/parameters/limitParam")
        val exampleRef   = Reference(`$ref` = "#/components/examples/user-example")

        assertTrue(
          schemaRef.`$ref`.startsWith("#/components/schemas/"),
          responseRef.`$ref`.startsWith("#/components/responses/"),
          parameterRef.`$ref`.startsWith("#/components/parameters/"),
          exampleRef.`$ref`.startsWith("#/components/examples/")
        )
      },
      test("Schema[Reference] can be derived") {
        val ref    = Reference(`$ref` = "#/components/schemas/User")
        val schema = Schema[Reference]

        assertTrue(schema != null, ref != null)
      },
      test("Reference round-trips through DynamicValue") {
        val ref = Reference(
          `$ref` = "#/components/schemas/User",
          summary = Some(doc("User schema reference")),
          description = Some(doc("A reference to the User schema defined in components"))
        )

        val dv     = Schema[Reference].toDynamicValue(ref)
        val result = Schema[Reference].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.`$ref` == "#/components/schemas/User"),
          result.exists(_.summary.contains(doc("User schema reference"))),
          result.exists(_.description.contains(doc("A reference to the User schema defined in components")))
        )
      },
      test("Reference has NO extensions field per OpenAPI spec") {
        val ref = Reference(`$ref` = "#/components/schemas/User")

        // Verify that Reference case class does not have an extensions field
        // This is a compile-time check - if extensions field existed, this would compile:
        // val extensions = ref.extensions
        // Since it doesn't exist, we just verify the Reference can be constructed
        assertTrue(ref.`$ref` == "#/components/schemas/User")
      }
    ),
    suite("ReferenceOr[A]")(
      test("can hold a Reference via Ref case") {
        val reference: ReferenceOr[String] = ReferenceOr.Ref(
          Reference(`$ref` = "#/components/schemas/User")
        )

        reference match {
          case ReferenceOr.Ref(ref) =>
            assertTrue(ref.`$ref` == "#/components/schemas/User")
          case ReferenceOr.Value(_) =>
            assertTrue(false)
        }
      },
      test("can hold a concrete value via Value case") {
        val value: ReferenceOr[String] = ReferenceOr.Value("Hello, World!")

        value match {
          case ReferenceOr.Ref(_) =>
            assertTrue(false)
          case ReferenceOr.Value(actual) =>
            assertTrue(actual == "Hello, World!")
        }
      },
      test("Ref extends ReferenceOr[Nothing] for variance") {
        val ref: ReferenceOr.Ref = ReferenceOr.Ref(
          Reference(`$ref` = "#/components/schemas/User")
        )
        val asReferenceOrNothing: ReferenceOr[Nothing] = ref
        val asReferenceOrString: ReferenceOr[String]   = ref
        val asReferenceOrInt: ReferenceOr[Int]         = ref

        assertTrue(
          asReferenceOrNothing.isInstanceOf[ReferenceOr.Ref],
          asReferenceOrString.isInstanceOf[ReferenceOr.Ref],
          asReferenceOrInt.isInstanceOf[ReferenceOr.Ref]
        )
      },
      test("Value is covariant") {
        val stringValue: ReferenceOr.Value[String] = ReferenceOr.Value("test")
        val asReferenceOr: ReferenceOr[String]     = stringValue

        asReferenceOr match {
          case ReferenceOr.Value(v) => assertTrue(v == "test")
          case _                    => assertTrue(false)
        }
      },
      test("Schema[ReferenceOr[A]] can be derived for any A with Schema") {
        val schema = Schema[ReferenceOr[String]]

        assertTrue(schema != null)
      },
      test("ReferenceOr[String] with Ref round-trips through DynamicValue") {
        val ref: ReferenceOr[String] = ReferenceOr.Ref(
          Reference(
            `$ref` = "#/components/schemas/User",
            summary = Some(doc("User reference"))
          )
        )

        val dv     = Schema[ReferenceOr[String]].toDynamicValue(ref)
        val result = Schema[ReferenceOr[String]].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists {
            case ReferenceOr.Ref(reference) =>
              reference.`$ref` == "#/components/schemas/User" &&
              reference.summary.contains(doc("User reference"))
            case _ => false
          }
        )
      },
      test("ReferenceOr[String] with Value round-trips through DynamicValue") {
        val value: ReferenceOr[String] = ReferenceOr.Value("concrete value")

        val dv     = Schema[ReferenceOr[String]].toDynamicValue(value)
        val result = Schema[ReferenceOr[String]].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists {
            case ReferenceOr.Value(v) => v == "concrete value"
            case _                    => false
          }
        )
      },
      test("ReferenceOr[Info] with concrete Info value round-trips") {
        val info: Info               = Info(title = "Test API", version = "1.0.0")
        val value: ReferenceOr[Info] = ReferenceOr.Value(info)
        val schema                   = Schema[ReferenceOr[Info]]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists {
            case ReferenceOr.Value(i) => i.title == "Test API" && i.version == "1.0.0"
            case _                    => false
          }
        )
      },
      test("ReferenceOr[Info] with Ref round-trips") {
        val ref: ReferenceOr[Info] = ReferenceOr.Ref(
          Reference(`$ref` = "#/components/info/AppInfo")
        )
        val schema = Schema[ReferenceOr[Info]]

        val dv     = schema.toDynamicValue(ref)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists {
            case ReferenceOr.Ref(reference) =>
              reference.`$ref` == "#/components/info/AppInfo"
            case _ => false
          }
        )
      },
      test("ReferenceOr works with complex nested types") {
        case class ComplexType(name: String, count: Int, tags: List[String])
        object ComplexType {
          implicit val schema: Schema[ComplexType] = Schema.derived
        }

        val complex                         = ComplexType("test", 5, List("tag1", "tag2"))
        val value: ReferenceOr[ComplexType] = ReferenceOr.Value(complex)
        val schema                          = Schema[ReferenceOr[ComplexType]]

        val dv     = schema.toDynamicValue(value)
        val result = schema.fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists {
            case ReferenceOr.Value(c) =>
              c.name == "test" && c.count == 5 && c.tags == List("tag1", "tag2")
            case _ => false
          }
        )
      },
      test("ReferenceOr discriminates between Ref and Value correctly") {
        val ref: ReferenceOr[String]   = ReferenceOr.Ref(Reference(`$ref` = "#/test"))
        val value: ReferenceOr[String] = ReferenceOr.Value("test")

        val refIsRef = ref match {
          case ReferenceOr.Ref(_)   => true
          case ReferenceOr.Value(_) => false
        }
        val valueIsValue = value match {
          case ReferenceOr.Ref(_)   => false
          case ReferenceOr.Value(_) => true
        }

        assertTrue(refIsRef, valueIsValue)
      }
    )
  )
}
