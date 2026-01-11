package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration._

object RecordActionSpec extends ZIOSpecDefault {

  def spec = suite("Record Action Verification")(
    test("AddField should add a new field to a flat Record") {
      val oldData = DynamicValue.Record(Vector(
        "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
      ))
      
      // ফিক্স: MigrationAction.AddField (বড় হাত) এর বদলে 
      // MigrationAction.addField (ছোট হাত - ফ্যাক্টরি মেথড) ব্যবহার করা হয়েছে।
      val action = MigrationAction.addField(
        DynamicOptic.root, 
        "age", 
        DynamicValue.Primitive(PrimitiveValue.Int(30))
      )
      
      val migration = DynamicMigration(Vector(action))
      val result = migration.apply(oldData)
      
      val expected = DynamicValue.Record(Vector(
        "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
        "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
      ))
      
      assertTrue(result == Right(expected))
    },

    test("Rename should change the field key in a flat Record") {
      val oldData = DynamicValue.Record(Vector(
        "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
      ))
      
      // ফিক্স: MigrationAction.Rename (বড় হাত) এর বদলে 
      // MigrationAction.rename (ছোট হাত - ফ্যাক্টরি মেথড) ব্যবহার করা হয়েছে।
      val action = MigrationAction.rename(DynamicOptic.root, "firstName", "lastName")
      
      val migration = DynamicMigration(Vector(action))
      val result = migration.apply(oldData)
      
      val expected = DynamicValue.Record(Vector(
        "lastName" -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
      ))
      
      assertTrue(result == Right(expected))
    }
  )
}