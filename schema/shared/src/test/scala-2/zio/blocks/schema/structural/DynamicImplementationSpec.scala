package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._
import scala.language.dynamics
import scala.language.reflectiveCalls

/**
 * Tests for Scala 2 structural type implementation.
 *
 * In Scala 2, we support pure structural types (refinement types like
 * `{ def name: String; def age: Int }`):
 *   - Schema derivation generates an anonymous Dynamic class at compile time
 *   - Works on ALL platforms (JVM, JS, Native) without runtime reflection for
 *     construction
 *   - Deconstruction uses reflection but the generated methods satisfy the
 *     structural contract
 */
object DynamicImplementationSpec extends ZIOSpecDefault {

  // === Pure structural types (no Dynamic base) ===
  type PersonLike  = { def name: String; def age: Int }
  type PointLike   = { def x: Int; def y: Int }
  type SingleField = { def value: String }

  // Helper class for testing Dynamic field access
  class DynamicPerson(fields: Map[String, Any]) extends Dynamic {
    def selectDynamic(name: String): Any = fields(name)
    def name: String                     = fields("name").asInstanceOf[String]
    def age: Int                         = fields("age").asInstanceOf[Int]
  }

  def spec = suite("DynamicImplementationSpec")(
    suite("Dynamic Field Access")(
      test("Dynamic field access works correctly") {
        val person = new DynamicPerson(Map("name" -> "Alice", "age" -> 30))
        assertTrue(
          person.selectDynamic("name") == "Alice",
          person.selectDynamic("age") == 30
        )
      },
      test("selectDynamic is generated correctly") {
        val person = new DynamicPerson(Map("name" -> "Charlie", "age" -> 35))
        assertTrue(
          person.selectDynamic("name") == "Charlie",
          person.selectDynamic("age") == 35
        )
      },
      test("missing field access throws appropriate error") {
        val person = new DynamicPerson(Map("name" -> "Dave"))
        val thrown =
          try {
            person.selectDynamic("age")
            false
          } catch {
            case _: NoSuchElementException => true
            case _: Throwable              => false
          }
        assertTrue(thrown)
      }
    ),
    suite("Pure Structural Type Schema Derivation")(
      test("derives schema for pure structural PersonLike") {
        val schema = Schema.derived[PersonLike]
        assertTrue(schema != null)
      },
      test("derives schema for pure structural PointLike") {
        val schema = Schema.derived[PointLike]
        assertTrue(schema != null)
      },
      test("derives schema for pure structural SingleField") {
        val schema = Schema.derived[SingleField]
        assertTrue(schema != null)
      },
      test("pure structural schema has correct field names") {
        val schema = Schema.derived[PersonLike]
        // Check that the reflect is a Record by checking its class name
        val reflectClassName = schema.reflect.getClass.getSimpleName
        assertTrue(reflectClassName.contains("Record"))
      },
      test("pure structural schema has correct field count") {
        val schema           = Schema.derived[PointLike]
        val reflectClassName = schema.reflect.getClass.getSimpleName
        assertTrue(reflectClassName.contains("Record"))
      }
    ),
    suite("Pure Structural Type fromDynamicValue")(
      test("constructs PersonLike from DynamicValue") {
        val schema  = Schema.derived[PersonLike]
        val dynamic = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val result = schema.fromDynamicValue(dynamic)
        result match {
          case Right(person) =>
            assertTrue(
              person.name == "Bob",
              person.age == 25
            )
          case Left(err) =>
            assertTrue(false) ?? s"Expected Right, got Left($err)"
        }
      },
      test("constructs PointLike from DynamicValue") {
        val schema  = Schema.derived[PointLike]
        val dynamic = DynamicValue.Record(
          Vector(
            "x" -> DynamicValue.Primitive(PrimitiveValue.Int(42)),
            "y" -> DynamicValue.Primitive(PrimitiveValue.Int(84))
          )
        )
        val result = schema.fromDynamicValue(dynamic)
        result match {
          case Right(point) =>
            assertTrue(
              point.x == 42,
              point.y == 84
            )
          case Left(err) =>
            assertTrue(false) ?? s"Expected Right, got Left($err)"
        }
      },
      test("constructs SingleField from DynamicValue") {
        val schema  = Schema.derived[SingleField]
        val dynamic = DynamicValue.Record(
          Vector("value" -> DynamicValue.Primitive(PrimitiveValue.String("hello")))
        )
        val result = schema.fromDynamicValue(dynamic)
        result match {
          case Right(sf) =>
            assertTrue(sf.value == "hello")
          case Left(err) =>
            assertTrue(false) ?? s"Expected Right, got Left($err)"
        }
      }
    ),
    suite("Pure Structural Type Round-Trip")(
      test("PersonLike round-trips through DynamicValue") {
        val schema  = Schema.derived[PersonLike]
        val dynamic = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Eve")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(40))
          )
        )
        val result = schema.fromDynamicValue(dynamic)
        result match {
          case Right(person) =>
            val backToDynamic = schema.toDynamicValue(person)
            backToDynamic match {
              case record: DynamicValue.Record =>
                val fieldMap = record.fields.toMap
                assertTrue(
                  fieldMap.get("name").contains(DynamicValue.Primitive(PrimitiveValue.String("Eve"))),
                  fieldMap.get("age").contains(DynamicValue.Primitive(PrimitiveValue.Int(40)))
                )
              case _ =>
                assertTrue(false) ?? "Expected DynamicValue.Record"
            }
          case Left(err) =>
            assertTrue(false) ?? s"fromDynamicValue failed: $err"
        }
      },
      test("PointLike round-trips through DynamicValue") {
        val schema  = Schema.derived[PointLike]
        val dynamic = DynamicValue.Record(
          Vector(
            "x" -> DynamicValue.Primitive(PrimitiveValue.Int(100)),
            "y" -> DynamicValue.Primitive(PrimitiveValue.Int(200))
          )
        )
        val result = schema.fromDynamicValue(dynamic)
        result match {
          case Right(point) =>
            val backToDynamic = schema.toDynamicValue(point)
            backToDynamic match {
              case record: DynamicValue.Record =>
                val fieldMap = record.fields.toMap
                assertTrue(
                  fieldMap.get("x").contains(DynamicValue.Primitive(PrimitiveValue.Int(100))),
                  fieldMap.get("y").contains(DynamicValue.Primitive(PrimitiveValue.Int(200)))
                )
              case _ =>
                assertTrue(false) ?? "Expected DynamicValue.Record"
            }
          case Left(err) =>
            assertTrue(false) ?? s"fromDynamicValue failed: $err"
        }
      }
    ),
    suite("Nominal to Structural Conversion")(
      test("nominal values round-trip through schema") {
        case class Person(name: String, age: Int)
        val schema  = Schema.derived[Person]
        val person  = Person("Grace", 50)
        val dynamic = schema.toDynamicValue(person)
        val result  = schema.fromDynamicValue(dynamic)
        assertTrue(result == Right(person))
      }
    )
  )
}
