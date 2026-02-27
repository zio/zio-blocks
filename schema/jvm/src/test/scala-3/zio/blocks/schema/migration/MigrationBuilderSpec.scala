package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema, SchemaBaseSpec}
import zio.test.Assertion._
import zio.test._

object MigrationBuilderSpec extends SchemaBaseSpec {

  // ─── Test types ─────────────────────────────────────────────────────

  case class PersonV1(firstName: String, lastName: String, age: Int)
  object PersonV1 {
    given Schema[PersonV1] = Schema.derived[PersonV1]
  }

  case class PersonV2(fullName: String, age: Int, email: String)
  object PersonV2 {
    given Schema[PersonV2] = Schema.derived[PersonV2]
  }

  case class SimpleA(name: String, value: Int)
  object SimpleA {
    given Schema[SimpleA] = Schema.derived[SimpleA]
  }

  case class SimpleB(name: String, value: Int, extra: String)
  object SimpleB {
    given Schema[SimpleB] = Schema.derived[SimpleB]
  }

  case class RenameA(firstName: String, age: Int)
  object RenameA {
    given Schema[RenameA] = Schema.derived[RenameA]
  }

  case class RenameB(givenName: String, age: Int)
  object RenameB {
    given Schema[RenameB] = Schema.derived[RenameB]
  }

  case class Inner(street: String)
  object Inner {
    given Schema[Inner] = Schema.derived[Inner]
  }

  case class Outer(address: Inner)
  object Outer {
    given Schema[Outer] = Schema.derived[Outer]
  }

  // ─── Tests ──────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilderSpec")(
    selectorSuite,
    buildPartialSuite,
    buildSuite,
    typedMigrationSuite
  )

  private val selectorSuite = suite("selector macros")(
    test("selectorToOptic converts simple field access") {
      val optic = MigrationBuilderMacros.selectorToOptic[SimpleA](_.name)
      assert(optic)(equalTo(DynamicOptic.root.field("name")))
    },
    test("selectorToOptic converts another simple field") {
      val optic = MigrationBuilderMacros.selectorToOptic[SimpleA](_.value)
      assert(optic)(equalTo(DynamicOptic.root.field("value")))
    },
    test("selectorToOptic converts nested field access") {
      val optic = MigrationBuilderMacros.selectorToOptic[Outer](_.address.street)
      assert(optic)(equalTo(DynamicOptic.root.field("address").field("street")))
    }
  )

  private val buildPartialSuite = suite("buildPartial")(
    test("creates a migration without full validation") {
      val migration = Migration.newBuilder[SimpleA, SimpleB]
        .addField[String](_.extra, "default")
        .buildPartial

      assert(migration.dynamicMigration.actions.length)(equalTo(1))
    },
    test("builds with multiple actions") {
      val migration = Migration.newBuilder[RenameA, RenameB]
        .renameField(_.firstName, _.givenName)
        .buildPartial

      assert(migration.dynamicMigration.actions.length)(equalTo(1))
    }
  )

  private val buildSuite = suite("build")(
    test("creates a migration with macro validation") {
      val migration = Migration.newBuilder[SimpleA, SimpleB]
        .addField[String](_.extra, "default")
        .build

      assert(migration.dynamicMigration.actions.length)(equalTo(1))
    }
  )

  private val typedMigrationSuite = suite("typed Migration")(
    test("Migration.identity returns value unchanged") {
      val identity = Migration.identity[SimpleA]
      val input    = SimpleA("test", 42)
      assert(identity(input))(isRight(equalTo(input)))
    },
    test("addField migration adds a field with default value") {
      val migration = Migration.newBuilder[SimpleA, SimpleB]
        .addField[String](_.extra, "default")
        .buildPartial

      val input  = SimpleA("test", 42)
      val result = migration(input)
      assert(result)(isRight(equalTo(SimpleB("test", 42, "default"))))
    },
    test("renameField migration renames a field") {
      val migration = Migration.newBuilder[RenameA, RenameB]
        .renameField(_.firstName, _.givenName)
        .buildPartial

      val input  = RenameA("Alice", 30)
      val result = migration(input)
      assert(result)(isRight(equalTo(RenameB("Alice", 30))))
    },
    test("reverse migration undoes addField") {
      val migration = Migration.newBuilder[SimpleA, SimpleB]
        .addField[String](_.extra, "default")
        .buildPartial

      val input   = SimpleA("test", 42)
      val forward = migration(input)
      val reverse = forward.flatMap(b => migration.reverse(b))
      assert(reverse)(isRight(equalTo(input)))
    },
    test("++ composes typed migrations") {
      val m1 = Migration.identity[SimpleA]
      val m2 = Migration.newBuilder[SimpleA, SimpleB]
        .addField[String](_.extra, "default")
        .buildPartial

      val composed = m1 ++ m2
      val input    = SimpleA("test", 42)
      assert(composed(input))(isRight(equalTo(SimpleB("test", 42, "default"))))
    },
    test("dropField removes a field") {
      val migration = Migration.newBuilder[SimpleB, SimpleA]
        .dropField[String](_.extra, "")
        .buildPartial

      val input  = SimpleB("test", 42, "extra-value")
      val result = migration(input)
      assert(result)(isRight(equalTo(SimpleA("test", 42))))
    }
  )
}
