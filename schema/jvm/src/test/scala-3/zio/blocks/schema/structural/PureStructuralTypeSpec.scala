package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/** Tests for pure structural type Schema derivation (JVM only). */
object PureStructuralTypeSpec extends SchemaBaseSpec {

  type PersonLike = { def name: String; def age: Int }

  def spec: Spec[Any, Nothing] = suite("PureStructuralTypeSpec")(
    test("pure structural type derives schema") {
      val schema = Schema.derived[PersonLike]
      assertTrue(schema != null)
    },
    test("pure structural type schema has correct field names") {
      val schema     = Schema.derived[PersonLike]
      val fieldNames = schema.reflect match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
        case _                            => Set.empty[String]
      }
      assertTrue(fieldNames == Set("name", "age"))
    },
    test("pure structural type converts to DynamicValue and back") {
      val schema = Schema.derived[PersonLike]

      val person: PersonLike = new {
        @scala.annotation.nowarn
        def name: String = "Alice"
        @scala.annotation.nowarn
        def age: Int = 30
      }

      val dynamic = schema.toDynamicValue(person)

      assertTrue(
        dynamic match {
          case DynamicValue.Record(fields) =>
            val fieldMap = fields.toMap
            fieldMap.get("name").contains(DynamicValue.Primitive(PrimitiveValue.String("Alice"))) &&
            fieldMap.get("age").contains(DynamicValue.Primitive(PrimitiveValue.Int(30)))
          case _ => false
        },
        schema.fromDynamicValue(dynamic).isRight
      )
    },
    test("pure structural type encodes to correct DynamicValue structure") {
      val schema = Schema.derived[PersonLike]

      val person: PersonLike = new {
        @scala.annotation.nowarn
        def name: String = "Bob"
        @scala.annotation.nowarn
        def age: Int = 25
      }

      val dynamic = schema.toDynamicValue(person)

      assertTrue(dynamic match {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          fieldMap.get("name").contains(DynamicValue.Primitive(PrimitiveValue.String("Bob"))) &&
          fieldMap.get("age").contains(DynamicValue.Primitive(PrimitiveValue.Int(25)))
        case _ => false
      })
    }
  )
}
