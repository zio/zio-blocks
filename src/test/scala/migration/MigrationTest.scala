package migration

import org.scalatest.funsuite.AnyFunSuite
import migration.DynamicValue._

class MigrationTest extends AnyFunSuite {

  test("rename nested field works") {

    val input =
      Obj(Map(
        "person" -> Obj(Map(
          "firstName" -> Str("John")
        ))
      ))

    val migration =
      DynamicMigration(Vector(
        RenameField(
          DynamicOptic(List(Field("person"))),
          "firstName",
          "fullName"
        )
      ))

    val result = migration(input)

    assert(
      result ==
        Right(
          Obj(Map(
            "person" -> Obj(Map(
              "fullName" -> Str("John")
            ))
          ))
        )
    )
  }

  test("reverse migration restores original") {

    val input =
      Obj(Map("name" -> Str("John")))

    val migration =
      DynamicMigration(Vector(
        RenameField(DynamicOptic(Nil), "name", "fullName")
      ))

    val forward = migration(input)
    val backward = forward.flatMap(migration.reverse.apply)

    assert(backward == Right(input))
  }

  test("composition behaves correctly") {

    val input =
      Obj(Map("name" -> Str("John")))

    val m1 =
      DynamicMigration(Vector(
        RenameField(DynamicOptic(Nil), "name", "firstName")
      ))

    val m2 =
      DynamicMigration(Vector(
        RenameField(DynamicOptic(Nil), "firstName", "fullName")
      ))

    val combined = m1 ++ m2

    val sequential = m1(input).flatMap(m2.apply)
    val composed = combined(input)

    assert(composed == sequential)
  }
}