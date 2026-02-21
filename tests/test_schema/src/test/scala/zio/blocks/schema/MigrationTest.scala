package zio.blocks.schema

import zio._
import zio.json._
import zio.test._
import zio.test.Assertion._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object MigrationTest extends ZIOSpecDefault {
  def spec = suite("Migration")(
    test("migrate Record values") {
      val recordValue = Json.fromFields(Seq(("key", Json.fromString("value"))))
      val migration = Migration("m2", 2, "Add Record value")
      for {
        _ <- Migration.create(migration)
        result <- Migration.migrate(recordValue, migration)
      } yield assert(result)(isRight(isJson(recordValue)))
    }
  )
}