package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema._

object BuildValidationSpec extends ZIOSpecDefault {

  case class UserV1(name: String)
  case class UserV2(fullName: String, age: Int)

  implicit val v1Schema: Schema[UserV1] = Schema.derived
  implicit val v2Schema: Schema[UserV2] = Schema.derived

  def spec = suite("Point 5 & 6: Scala 2.13 Build Validation Verification")(
    test("build should fail if migration is incomplete in Scala 2") {
      val attempt = zio.ZIO.attempt {
        MigrationBuilder.make[UserV1, UserV2].build
      }
      assertZIO(attempt.exit)(fails(anything))
    },

    test("buildPartial should work even if incomplete in Scala 2") {
      val migration = MigrationBuilder.make[UserV1, UserV2].buildPartial
      assertTrue(migration.dynamicMigration.actions.isEmpty)
    }
  )
}