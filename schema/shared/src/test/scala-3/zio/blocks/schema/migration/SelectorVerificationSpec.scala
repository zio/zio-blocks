package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object SelectorVerificationSpec extends ZIOSpecDefault {

  // ১. টেস্টের জন্য ডেটা স্ট্রাকচার
  case class UserV1(firstName: String)
  case class UserV2(fullName: String)

  implicit val v1Schema: Schema[UserV1] = Schema.derived[UserV1]
  implicit val v2Schema: Schema[UserV2] = Schema.derived[UserV2]

  def spec = suite("Point 4: Selector Function (S => A) Verification")(
    test("MigrationBuilder must correctly convert lambda selectors to DynamicOptic") {
      
      // ২. ল্যাম্বডা সিলেকশন ব্যবহার করে মাইগ্রেশন বিল্ড করা
      // এখানে সরাসরি '_.firstName' এবং '_.fullName' ল্যাম্বডা ব্যবহার করা হয়েছে
      val migration = MigrationBuilder.make[UserV1, UserV2]
        .renameField(_.firstName, _.fullName)
        .build

      // ৩. ইন্টারনাল অ্যাকশনগুলো রিড করা
      val actions = migration.dynamicMigration.actions
      
      // ৪. প্রমাণ (Assertion)
      // চেক করা যে ল্যাম্বডা থেকে আসা পাথটি আসলেই 'firstName' এবং 'fullName' ধারণ করে
      val firstAction = actions.head.asInstanceOf[MigrationAction.Rename]
      
      val isSelectorWorking = 
        firstAction.from == "firstName" && 
        firstAction.to == "fullName"

      assertTrue(isSelectorWorking)
    }
  )
}