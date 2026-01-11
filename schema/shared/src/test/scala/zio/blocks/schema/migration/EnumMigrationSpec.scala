package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object EnumMigrationSpec extends ZIOSpecDefault {

  def spec = suite("Point 9: Enum Migration Verification")(
    test("Should correctly rename an Enum case (Variant)") {
      // ১. একটি এনাম ডেটা তৈরি করি (যেমন: Variant "Red" যার ভ্যালু ১)
      val oldEnum = DynamicValue.Variant("Red", DynamicValue.Primitive(PrimitiveValue.Int(1)))
      
      // ২. মাইগ্রেশন: Red কে Crimson করো
      val action = MigrationAction.RenameCase(Vector.empty, "Red", "Crimson")
      val migration = DynamicMigration(Vector(action))
      
      val result = migration.apply(oldEnum)
      
      // ৩. প্রমাণ: চেক করা যে নাম Crimson হয়েছে কিন্তু ভ্যালু ১ ঠিক আছে
      result match {
        case Right(DynamicValue.Variant(caseName, value)) =>
          assertTrue(caseName == "Crimson" && value == DynamicValue.Primitive(PrimitiveValue.Int(1)))
        case _ => assertTrue(false)
      }
    }
  )
}