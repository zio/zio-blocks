package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object WrapsSchemaSpec extends ZIOSpecDefault {

  def spec = suite("Point 2: Migration Wrapping Verification")(
    test("Migration[A, B] must correctly wrap source schema, target schema, and actions") {
      // ১. ডামি স্কিমা তৈরি
      val source = Schema[String]
      val target = Schema[Int]
      val actions = DynamicMigration(Vector.empty)

      // ২. মাইগ্রেশন অবজেক্ট তৈরি (Wrapping)
      val migration = Migration(actions, source, target)

      // ৩. ভেরিফিকেশন (প্রমাণ)
      // আমরা চেক করছি মাইগ্রেশন অবজেক্টের ভেতর আমাদের দেওয়া জিনিসগুলোই আছে কি না
      val condition = 
        migration.sourceSchema == source && 
        migration.targetSchema == target && 
        migration.dynamicMigration == actions

      assertTrue(condition)
    }
  )
}