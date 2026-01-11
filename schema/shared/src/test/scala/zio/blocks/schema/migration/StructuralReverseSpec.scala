package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object AlgebraicLawsSpec extends ZIOSpecDefault {

  def spec = suite("Point 8: Identity & Associativity Laws Verification")(
    
    test("Identity Law: Empty migration should not change data") {
      val data = DynamicValue.Record(Vector("id" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
      val identityMigration = DynamicMigration(Vector.empty)
      
      assertTrue(identityMigration.apply(data) == Right(data))
    },

    test("Associativity Law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
      val m1 = DynamicMigration(Vector(MigrationAction.rename(DynamicOptic.root, "a", "b")))
      val m2 = DynamicMigration(Vector(MigrationAction.rename(DynamicOptic.root, "b", "c")))
      val m3 = DynamicMigration(Vector(MigrationAction.rename(DynamicOptic.root, "c", "d")))

      val leftSide = (m1 ++ m2) ++ m3
      val rightSide = m1 ++ (m2 ++ m3)
      
      // গুগল স্ট্যান্ডার্ড: দুই পাশেই অ্যাকশনগুলোর সিকোয়েন্স সমান কি না তা চেক করা
      assertTrue(leftSide.actions == rightSide.actions)
    }
  )
}