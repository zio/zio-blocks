package zio.blocks.schema.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Scala 3 Selectable-based structural type Schema derivation.
 *
 * This spec tests deriving Schema instances for structural types based on
 * Selectable. A Selectable type must have:
 *   1. A constructor taking Map[String, Any], or
 *   2. A companion object with apply(Map[String, Any])
 *
 * These requirements enable the Schema to construct instances from DynamicValue
 * and deconstruct instances to DynamicValue.
 */
object SelectableImplementationSpec extends ZIOSpecDefault {

  // === Custom Selectable implementations ===

  /** A simple record-like class that extends Selectable with Map constructor */
  case class Record(fields: Map[String, Any]) extends Selectable {
    def selectDynamic(name: String): Any = fields(name)
  }

  /** A Selectable class that uses companion apply instead of constructor */
  case class RecordWithApply(fields: List[(String, Any)]) extends Selectable {
    private val fieldsMap: Map[String, Any] = fields.toMap
    def selectDynamic(name: String): Any    = fieldsMap(name)
  }

  object RecordWithApply {
    def apply(map: Map[String, Any]): RecordWithApply = RecordWithApply(map.toList)
  }

  // === Type aliases for structural types ===
  type PersonLike      = Record { def name: String; def age: Int }
  type PointLike       = Record { def x: Int; def y: Int }
  type PersonLikeApply = RecordWithApply { def name: String; def age: Int }

  // === Helper functions ===
  def makePerson(name: String, age: Int): PersonLike =
    Record(Map("name" -> name, "age" -> age)).asInstanceOf[PersonLike]

  def makePoint(x: Int, y: Int): PointLike =
    Record(Map("x" -> x, "y" -> y)).asInstanceOf[PointLike]

  def makePersonApply(name: String, age: Int): PersonLikeApply =
    RecordWithApply(Map("name" -> name, "age" -> age)).asInstanceOf[PersonLikeApply]

  def spec = suite("SelectableImplementationSpec")(
    suite("Selectable Field Access Basics")(
      test("selectDynamic provides field access via Record") {
        val person = makePerson("Alice", 30)
        assertTrue(
          person.name == "Alice",
          person.age == 30
        )
      },
      test("selectDynamic provides field access via RecordWithApply") {
        val person = makePersonApply("Bob", 25)
        assertTrue(
          person.name == "Bob",
          person.age == 25
        )
      }
    ),
    suite("Schema Derivation for Selectable Types")(
      test("derives schema for Record-based PersonLike") {
        val schema = Schema.derived[PersonLike]
        assertTrue(schema != null)
      },
      test("derives schema for RecordWithApply-based PersonLikeApply") {
        val schema = Schema.derived[PersonLikeApply]
        assertTrue(schema != null)
      },
      test("derives schema for Record-based PointLike") {
        val schema = Schema.derived[PointLike]
        assertTrue(schema != null)
      },
      test("derived schema has correct field names") {
        val schema = Schema.derived[PersonLike]
        schema.reflect match {
          case record: Reflect.Record[_, _] =>
            val fieldNames = record.fields.map(_.name).toSet
            assertTrue(
              fieldNames.contains("name"),
              fieldNames.contains("age"),
              fieldNames.size == 2
            )
          case _ =>
            assertTrue(false) ?? "Expected Record reflect"
        }
      },
      test("derived schema for Point has correct fields") {
        val schema = Schema.derived[PointLike]
        schema.reflect match {
          case record: Reflect.Record[_, _] =>
            val fieldNames = record.fields.map(_.name).toSet
            assertTrue(
              fieldNames.contains("x"),
              fieldNames.contains("y"),
              fieldNames.size == 2
            )
          case _ =>
            assertTrue(false) ?? "Expected Record reflect"
        }
      }
    ),
    suite("Schema toDynamicValue")(
      test("converts Record-based structural to DynamicValue") {
        val schema  = Schema.derived[PersonLike]
        val person  = makePerson("Carol", 28)
        val dynamic = schema.toDynamicValue(person)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("name").contains(DynamicValue.Primitive(PrimitiveValue.String("Carol"))),
              fieldMap.get("age").contains(DynamicValue.Primitive(PrimitiveValue.Int(28)))
            )
          case _ =>
            assertTrue(false) ?? "Expected DynamicValue.Record"
        }
      },
      test("converts RecordWithApply-based structural to DynamicValue") {
        val schema  = Schema.derived[PersonLikeApply]
        val person  = makePersonApply("Dave", 35)
        val dynamic = schema.toDynamicValue(person)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("name").contains(DynamicValue.Primitive(PrimitiveValue.String("Dave"))),
              fieldMap.get("age").contains(DynamicValue.Primitive(PrimitiveValue.Int(35)))
            )
          case _ =>
            assertTrue(false) ?? "Expected DynamicValue.Record"
        }
      },
      test("converts Point structural to DynamicValue") {
        val schema  = Schema.derived[PointLike]
        val point   = makePoint(100, 200)
        val dynamic = schema.toDynamicValue(point)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("x").contains(DynamicValue.Primitive(PrimitiveValue.Int(100))),
              fieldMap.get("y").contains(DynamicValue.Primitive(PrimitiveValue.Int(200)))
            )
          case _ =>
            assertTrue(false) ?? "Expected DynamicValue.Record"
        }
      }
    ),
    suite("Schema fromDynamicValue")(
      test("constructs Record-based structural from DynamicValue") {
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
            assertTrue(
              person.name == "Eve",
              person.age == 40
            )
          case Left(err) =>
            assertTrue(false) ?? s"Expected Right, got Left($err)"
        }
      },
      test("constructs RecordWithApply-based structural from DynamicValue") {
        val schema  = Schema.derived[PersonLikeApply]
        val dynamic = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Frank")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(45))
          )
        )
        val result = schema.fromDynamicValue(dynamic)

        result match {
          case Right(person) =>
            assertTrue(
              person.name == "Frank",
              person.age == 45
            )
          case Left(err) =>
            assertTrue(false) ?? s"Expected Right, got Left($err)"
        }
      },
      test("constructs Point structural from DynamicValue") {
        val schema  = Schema.derived[PointLike]
        val dynamic = DynamicValue.Record(
          Vector(
            "x" -> DynamicValue.Primitive(PrimitiveValue.Int(50)),
            "y" -> DynamicValue.Primitive(PrimitiveValue.Int(60))
          )
        )
        val result = schema.fromDynamicValue(dynamic)

        result match {
          case Right(point) =>
            assertTrue(
              point.x == 50,
              point.y == 60
            )
          case Left(err) =>
            assertTrue(false) ?? s"Expected Right, got Left($err)"
        }
      }
    ),
    suite("Round-Trip via Schema")(
      test("Record-based structural round-trips through DynamicValue") {
        val schema   = Schema.derived[PersonLike]
        val original = makePerson("Grace", 50)

        val dynamic = schema.toDynamicValue(original)
        val result  = schema.fromDynamicValue(dynamic)

        result match {
          case Right(reconstructed) =>
            assertTrue(
              reconstructed.name == original.name,
              reconstructed.age == original.age
            )
          case Left(err) =>
            assertTrue(false) ?? s"Round-trip failed: $err"
        }
      },
      test("RecordWithApply-based structural round-trips through DynamicValue") {
        val schema   = Schema.derived[PersonLikeApply]
        val original = makePersonApply("Henry", 55)

        val dynamic = schema.toDynamicValue(original)
        val result  = schema.fromDynamicValue(dynamic)

        result match {
          case Right(reconstructed) =>
            assertTrue(
              reconstructed.name == original.name,
              reconstructed.age == original.age
            )
          case Left(err) =>
            assertTrue(false) ?? s"Round-trip failed: $err"
        }
      },
      test("Point structural round-trips through DynamicValue") {
        val schema   = Schema.derived[PointLike]
        val original = makePoint(300, 400)

        val dynamic = schema.toDynamicValue(original)
        val result  = schema.fromDynamicValue(dynamic)

        result match {
          case Right(reconstructed) =>
            assertTrue(
              reconstructed.x == original.x,
              reconstructed.y == original.y
            )
          case Left(err) =>
            assertTrue(false) ?? s"Round-trip failed: $err"
        }
      }
    ),
    suite("Error Handling")(
      test("missing field access throws NoSuchElementException") {
        val person = Record(Map("name" -> "Incomplete")).asInstanceOf[PersonLike]

        val thrown =
          try {
            person.age // This field doesn't exist in the map
            false
          } catch {
            case _: NoSuchElementException => true
            case _: Throwable              => false
          }

        assertTrue(thrown)
      }
    )
  )
}
