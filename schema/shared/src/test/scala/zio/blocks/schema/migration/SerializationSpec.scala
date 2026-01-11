package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object SerializationSpec extends ZIOSpecDefault {
  def spec = suite("Point 1: Serialization Verification")(
    test("DynamicMigration should be serializable round-trip") {
      val originalMigration = DynamicMigration(Vector(
        MigrationAction.rename(DynamicOptic.root.field("user"), "old", "new")
      ))

      // ZIO-blocks সঠিক মেথড ব্যবহার
      val dynamic = DynamicMigration.schema.toDynamicValue(originalMigration)
      val decoded = DynamicMigration.schema.fromDynamicValue(dynamic)

      assertTrue(decoded == Right(originalMigration))
    }
  )
}