package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object ErrorDiagnosticSpec extends ZIOSpecDefault {
  def spec = suite("Error Diagnostics Verification")(
    test("Should report the exact path when a field is missing") {
      val data = DynamicValue.Record(Vector("user" -> DynamicValue.Record(Vector.empty)))
      val path = DynamicOptic.root.field("user")
      
      // ফিক্স: MigrationAction.rename (ফ্যাক্টরি মেথড) ব্যবহার করা হয়েছে
      val action = MigrationAction.rename(path, "age", "years")
      val migration = DynamicMigration(Vector(action))
      
      val result = migration.apply(data)
      result match {
        case Left(err: MigrationError.FieldNotFound) =>
          assertTrue(err.fieldName == "age")
        case _ => assertTrue(false)
      }
    }
  )
}