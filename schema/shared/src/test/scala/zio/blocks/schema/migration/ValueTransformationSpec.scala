package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object ValueTransformationSpec extends ZIOSpecDefault {

  def spec = suite("Value Transformation Verification")(
    test("Mandate should use default value for missing/null fields") {
      val data = DynamicValue.Record(Vector("id" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
      
      // ফ্যাক্টরি মেথড ব্যবহার করে টাইপ মিসম্যাচ ফিক্স করা হয়েছে
      val action = MigrationAction.mandate(
        DynamicOptic.root.field("age"), 
        DynamicValue.Primitive(PrimitiveValue.Int(30))
      )
      val migration = DynamicMigration(Vector(action))
      
      val result = migration.apply(data)
      
      result match {
        case Right(DynamicValue.Record(fields)) =>
          val age = fields.find(_._1 == "age").map(_._2)
          assertTrue(age.contains(DynamicValue.Primitive(PrimitiveValue.Int(30))))
        case _ => assertTrue(false)
      }
    }
  )
}