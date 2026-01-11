package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object MigrationLawSpec extends ZIOSpecDefault {
  def spec = suite("Migration Laws Verification")(
    test("Associativity law") {
      // ফিক্স: MigrationAction.rename ব্যবহার করা হয়েছে
      val m1 = DynamicMigration(Vector(MigrationAction.rename(DynamicOptic.root, "a", "b")))
      val m2 = DynamicMigration(Vector(MigrationAction.rename(DynamicOptic.root, "b", "c")))
      val m3 = DynamicMigration(Vector(MigrationAction.rename(DynamicOptic.root, "c", "d")))

      val leftSide = (m1 ++ m2) ++ m3
      val rightSide = m1 ++ (m2 ++ m3)
      
      assertTrue(leftSide.actions == rightSide.actions)
    }
  )
}