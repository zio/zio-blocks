package zio.blocks.schema

import zio.test._
import zio.blocks.schema._

object StructuralSchemaSpec extends ZIOSpecDefault {

  type SimpleStructural = StructuralInstance { val name: String; val age: Int }

  case class Base(id: Int)
  type RefinedStructural = Base & StructuralInstance { val name: String }

  def spec: Spec[Any, Any] = suite("StructuralSchemaSpec")(
    test("derives schema for simple structural type") {
      val schema = Schema.structural[SimpleStructural]
      val fields = schema.reflect.asRecord.get.fields

      assertTrue(fields.length == 2) &&
      assertTrue(fields.exists(_.name == "name")) &&
      assertTrue(fields.exists(_.name == "age"))
    },
    test("round-trips structural type values") {
      val schema                  = Schema.structural[SimpleStructural]
      val input: SimpleStructural =
        new MapSelectable(Map("name" -> "Alice", "age" -> 30)).asInstanceOf[SimpleStructural]

      val dynamic = schema.toDynamicValue(input)
      val output  = schema.fromDynamicValue(dynamic)

      assertTrue(output.isRight) &&
      assertTrue(output.map(_.name).getOrElse("") == "Alice") &&
      assertTrue(output.map(_.age).getOrElse(0) == 30)
    },
    test("supports base class in structural type") {
      case class Base(id: Int)
      type Refined = Base { val extra: String }

      val schema = Schema.structural[Refined]
      val fields = schema.reflect.asRecord.get.fields

      assertTrue(fields.length == 2) &&
      assertTrue(fields.exists(_.name == "id")) &&
      assertTrue(fields.exists(_.name == "extra"))
    },
    test("supports nested structural types") {
      type Inner = StructuralInstance { val count: Int }
      type Outer = StructuralInstance { val data: Inner }

      val schema             = Schema.structural[Outer]
      val fields             = schema.reflect.asRecord.get.fields
      val firstFieldIsRecord = fields.head.value.isRecord
      assertTrue(fields.length == 1) &&
      assertTrue(fields.head.name == "data") &&
      assertTrue(firstFieldIsRecord)
    }
  )
}
