package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object PathBasedActionSpec extends ZIOSpecDefault {

  def spec = suite("Point 3: Path-based Action Verification")(
    test("Every action must correctly expose its path via DynamicOptic") {
      // ১. একটি পাথ তৈরি করি (user.profile)
      val testPath = DynamicOptic.root.field("user").field("profile")
      
      // ২. বিভিন্ন অ্যাকশন তৈরি করি এবং তাদের পাথ ভেরিফাই করি
      
      // ক) Rename অ্যাকশনের পাথ চেক
      val renameAction = MigrationAction.rename(testPath, "old", "new")
      val renamePathValid = renameAction.at.toString == ".user.profile"

      // খ) AddField অ্যাকশনের পাথ চেক
      val addAction = MigrationAction.addField(testPath, "age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
      val addPathValid = addAction.at.toString == ".user.profile"

      // গ) Mandate অ্যাকশনের পাথ চেক
      val mandateAction = MigrationAction.mandate(testPath, DynamicValue.Primitive(PrimitiveValue.Int(0)))
      val mandatePathValid = mandateAction.at.toString == ".user.profile"

      // ৩. চূড়ান্ত প্রমাণ (Assertion)
      val allPathsValid = renamePathValid && addPathValid && mandatePathValid

      assertTrue(allPathsValid)
    }
  )
}