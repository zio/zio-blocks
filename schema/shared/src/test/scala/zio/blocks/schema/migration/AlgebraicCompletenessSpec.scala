package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object AlgebraicCompletenessSpec extends ZIOSpecDefault {

  def spec = suite("Point 7 & 8: Algebraic Completeness Verification")(
    
    test("Identity Law: Empty migration should be a no-op") {
      val data = DynamicValue.Record(Vector("id" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
      val migration = DynamicMigration(Vector.empty)
      
      assertTrue(migration.apply(data) == Right(data))
    },

    test("Associativity Law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
      val m1 = DynamicMigration(Vector(MigrationAction.rename(DynamicOptic.root, "a", "b")))
      val m2 = DynamicMigration(Vector(MigrationAction.rename(DynamicOptic.root, "b", "c")))
      val m3 = DynamicMigration(Vector(MigrationAction.rename(DynamicOptic.root, "c", "d")))

      val leftSide = (m1 ++ m2) ++ m3
      val rightSide = m1 ++ (m2 ++ m3)
      
      assertTrue(leftSide.actions == rightSide.actions)
    },

    test("Structural Reverse: m.reverse.reverse == m (Point 7)") {
      val m = DynamicMigration(Vector(
        MigrationAction.rename(DynamicOptic.root, "old", "new"),
        MigrationAction.addField(DynamicOptic.root, "added", DynamicValue.Primitive(PrimitiveValue.Int(0)))
      ))
      
      // ডাবল রিভার্স করলে আগের মাইগ্রেশন ফিরে আসার কথা
      assertTrue(m.reverse.reverse.actions == m.actions)
    },

    test("Semantic Inverse: Round-trip data integrity") {
      val data = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("ZIO"))))
      val m = DynamicMigration(Vector(MigrationAction.rename(DynamicOptic.root, "name", "title")))
      
      val result = for {
        migrated <- m.apply(data)
        restored <- m.reverse.apply(migrated)
      } yield restored

      // মাইগ্রেশন করে আবার রিভার্স করলে অরিজিনাল ডেটা ফিরে আসতে হবে
      assertTrue(result == Right(data))
    }
  )
}