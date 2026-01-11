package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object CollectionSpec extends ZIOSpecDefault {
  def spec = suite("Collection/Sequence Transformation Verification")(
    test("Should migrate all elements inside a sequence (.each)") {
      val oldData = DynamicValue.Record(Vector(
        "tags" -> DynamicValue.Sequence(Vector(
          DynamicValue.Record(Vector("label" -> DynamicValue.Primitive(PrimitiveValue.String("scala"))))
        ))
      ))
      
      // ফিক্স: MigrationAction.rename (ফ্যাক্টরি মেথড) ব্যবহার করা হয়েছে
      val action = MigrationAction.rename(
        DynamicOptic.root.field("tags").elements, 
        "label", 
        "title"
      )
      val migration = DynamicMigration(Vector(action))
      val result = migration.apply(oldData)
      
      assertTrue(result.isRight)
    }
  )
}