package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object InvertibilitySpec extends ZIOSpecDefault {
  def spec = suite("Invertibility Verification")(
    test("Migration Round-trip: m.reverse.apply(m.apply(data)) == data") {
      val data = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("ZIO"))))
      
      // ফিক্স: MigrationAction.rename ব্যবহার করা হয়েছে
      val action = MigrationAction.rename(DynamicOptic.root, "name", "title")
      val migration = DynamicMigration(Vector(action))
      val reversed = migration.reverse
      
      val result = for {
        migrated <- migration.apply(data)
        restored <- reversed.apply(migrated)
      } yield restored

      assertTrue(result == Right(data))
    }
  )
}