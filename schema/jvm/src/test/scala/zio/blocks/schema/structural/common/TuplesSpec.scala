package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for tuple to structural type conversion.
 */
object TuplesSpec extends ZIOSpecDefault {

  def spec = suite("TuplesSpec")(
    test("tuple schema can be converted to structural") {
      val tupleSchema: Schema[(String, Int)] = Schema.derived[(String, Int)]
      val structuralSchema = tupleSchema.structural

      val typeName = structuralSchema.reflect.typeName.name
      assertTrue(typeName == "{_1:String,_2:Int}")
    },
    test("tuple structural has correct fields") {
      val tupleSchema = Schema.derived[(String, Int, Boolean)]
      val structuralSchema = tupleSchema.structural

      val fieldNames = structuralSchema.reflect match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toList
        case _                            => Nil
      }
      assertTrue(fieldNames == List("_1", "_2", "_3"))
    },
    test("tuple with 4 elements converts correctly") {
      val tupleSchema = Schema.derived[(Int, String, Boolean, Double)]
      val structuralSchema = tupleSchema.structural

      val typeName = structuralSchema.reflect.typeName.name
      assertTrue(typeName == "{_1:Int,_2:String,_3:Boolean,_4:Double}")
    } @@ TestAspect.ignore,
    test("large tuple (10 elements) converts correctly") {
      val tupleSchema = Schema.derived[(Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)]
      val structuralSchema = tupleSchema.structural

      val fieldNames = structuralSchema.reflect match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toList
        case _                            => Nil
      }
      assertTrue(fieldNames.size == 10)
    } @@ TestAspect.ignore,
    test("tuple with nested case class") {
      case class Inner(value: Int)
      val tupleSchema = Schema.derived[(String, Inner)]
      val structuralSchema = tupleSchema.structural

      val typeName = structuralSchema.reflect.typeName.name
      assertTrue(typeName.contains("_1") && typeName.contains("_2"))
    } @@ TestAspect.ignore
  )
}

