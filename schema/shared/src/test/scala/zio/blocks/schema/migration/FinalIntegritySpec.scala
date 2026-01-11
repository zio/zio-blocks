package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._

object FinalIntegritySpec extends ZIOSpecDefault {

  case class V1(id: Int)
  case class V2(id: Int, name: String)

  implicit val v1Schema: Schema[V1] = Schema.derived
  implicit val v2Schema: Schema[V2] = Schema.derived

  def spec = suite("Point 5 & 6: Final Integrity Verification")(
    
    test("build() must fail for incomplete migrations (Point 5)") {
      // ZIO.attempt ব্যবহার করে আমরা এক্সেপশনটিকে ZIO এর ভেতর ক্যাচ করছি
      // এটি JS/Native প্ল্যাটফর্মে ক্র্যাশ হওয়া রোধ করে
      val result = zio.ZIO.attempt(MigrationBuilder.make[V1, V2].build).exit
      
      assertZIO(result)(zio.test.Assertion.fails(zio.test.Assertion.anything))
    },

    test("buildPartial() must succeed even if incomplete (Point 6)") {
      val migration = MigrationBuilder.make[V1, V2].buildPartial
      assertTrue(migration.dynamicMigration.actions.isEmpty)
    }
  )
}