package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object MigrationSpec extends ZIOSpecDefault {

  def spec = suite("Migration System Verification")(
    test("AddField should add a new field to a Record") {
      val oldData = DynamicValue.Record(Vector(
        "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
      ))
      
      /**
       * সমাধান: সরাসরি Case Class (বড় হাতের AddField) কল না করে 
       * ফ্যাক্টরি মেথড (ছোট হাতের addField) ব্যবহার করা হয়েছে। 
       * এটি টাইপ মিসম্যাচ এবং লিঙ্কার এরর দূর করবে।
       */
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

    test("Rename should change the field key in a Record") {
      val oldData = DynamicValue.Record(Vector(
        "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
      ))
      
      /**
       * সমাধান: সরাসরি Rename (বড় হাত) এর বদলে rename (ছোট হাত) ব্যবহার করা হয়েছে।
       * এটি আপনার এরর লগে থাকা 'Unreachable symbols' এররটি ফিক্স করবে।
       */
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