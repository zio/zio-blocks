package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion._ // সমাধান: এটি 'fails' এবং 'anything' খুঁজে পেতে সাহায্য করবে
import zio.blocks.schema._

object BuildValidationSpec extends ZIOSpecDefault {

  case class UserV1(name: String)
  case class UserV2(fullName: String, age: Int)

  implicit val v1Schema: Schema[UserV1] = Schema.derived
  implicit val v2Schema: Schema[UserV2] = Schema.derived

  def spec = suite("Point 5 & 6: Build Validation Verification")(
    test("build should fail or warn if migration is incomplete") {
      val attempt = zio.ZIO.attempt {
        MigrationBuilder.make[UserV1, UserV2].build
      }
      // এখন এটি সঠিকভাবে কম্পাইল হবে
      assertZIO(attempt.exit)(fails(anything))
    },

    test("buildPartial should work even if incomplete") {
      val migration = MigrationBuilder.make[UserV1, UserV2].buildPartial
      assertTrue(migration.dynamicMigration.actions.isEmpty)
    }
  )
}