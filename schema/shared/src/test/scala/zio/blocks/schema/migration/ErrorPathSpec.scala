package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object ErrorPathSpec extends ZIOSpecDefault {

  def spec = suite("Point 10: Error Path Verification")(
    test("Error must contain the correct path information") {
      // ১. ডেটা স্ট্রাকচার: { "user": {} }
      val data = DynamicValue.Record(Vector("user" -> DynamicValue.Record(Vector.empty)))
      
      // ২. ভুল মাইগ্রেশন: 'user' এর ভেতরে 'age' নেই, কিন্তু আমরা সেটি রিনেম করতে চাইছি
      val testPath = DynamicOptic.root.field("user")
      val action = MigrationAction.rename(testPath, "age", "years")
      val migration = DynamicMigration(Vector(action))
      
      val result = migration.apply(data)
      
      // ৩. প্রমাণ: এরর মেসেজের ভেতর পাথটি কি ".user" দেখাচ্ছে?
      result match {
        case Left(err: MigrationError.FieldNotFound) =>
          // আমরা চেক করছি এরর অবজেক্টের ভেতরে আমাদের দেওয়া পাথটি আছে কি না
          assertTrue(err.path.toString == ".user" && err.fieldName == "age")
        case _ => 
          assertTrue(false)
      }
    }
  )
}