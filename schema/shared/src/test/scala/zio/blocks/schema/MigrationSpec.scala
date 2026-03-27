package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object MigrationSpec extends ZIOSpecDefault {

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit lazy val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  case class PersonV2(fullName: String, age: Int, active: Boolean)
  object PersonV2 {
    implicit lazy val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }

  def spec = suite("MigrationSpec")(
    test("Migration.identity preserves all data") {
      val migration = Migration.identity[PersonV1]
      val p1        = PersonV1("Alice", 30)
      val result    = migration(p1)
      assert(result)(isRight(equalTo(p1)))
    },
    test("Composition (m1 ++ m2) behaves sequentially") {
      val m1 = MigrationBuilder
        .make[PersonV1, PersonV1]
        .renameField(_.name, _.name)
        .buildPartial

      val m2 = MigrationBuilder.make[PersonV1, PersonV1].buildPartial

      val composed = m1 ++ m2
      val p1       = PersonV1("Bob", 25)

      assert(composed(p1))(isRight(equalTo(p1)))
    },
    test("Macro Validation Test (Working)") {
      // The following code would fail to compile with a Shape Diff error:
      // "Field(s) [active, fullName] in target schema are missing from source and have no default value provided."
      // val failingMigration = MigrationBuilder.make[PersonV1, PersonV2].build

      val trueLiteral = SchemaExpr.Literal[Any, Boolean](true, Schema[Boolean])

      val workingMigration = MigrationBuilder
        .make[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .addField(_.active, trueLiteral)
        .build

      val p1       = PersonV1("Charlie", 40)
      val expected = PersonV2("Charlie", 40, true)

      assert(workingMigration(p1))(isRight(equalTo(expected)))
    }
  )
}
