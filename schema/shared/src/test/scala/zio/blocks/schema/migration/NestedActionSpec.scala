package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration._

object NestedActionSpec extends ZIOSpecDefault {

  def spec = suite("Nested Migration Verification")(
    test("Nested Rename should change a field deep inside an object") {
      val oldData = DynamicValue.Record(Vector(
        "user" -> DynamicValue.Record(Vector(
          "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
        ))
      ))
      
      // ফিক্স: MigrationAction.Rename (Case Class) এর বদলে 
      // MigrationAction.rename (Factory Method) ব্যবহার করা হয়েছে।
      // এটি DynamicOptic কে অটোমেটিক সিরিয়ালাইজেবল প্রক্সিতে রূপান্তর করবে।
      val action = MigrationAction.rename(
        DynamicOptic.root.field("user"), 
        "firstName", 
        "name"
      )
      
      val migration = DynamicMigration(Vector(action))
      val result = migration.apply(oldData)
      
      val expected = DynamicValue.Record(Vector(
        "user" -> DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
        ))
      ))
      
      assertTrue(result == Right(expected))
    }
  )
}