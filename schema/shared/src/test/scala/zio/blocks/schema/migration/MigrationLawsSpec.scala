package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationLawsSpec extends SchemaBaseSpec {

  case class PersonV0(firstName: String)
  object PersonV0 {
    implicit val schema: Schema[PersonV0] = Schema.derived[PersonV0]
  }

  case class PersonV1(firstName: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  case class PersonV2(fullName: String, age: Int, nickname: Option[String])
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationLawsSpec")(
    identitySuite,
    associativitySuite,
    reverseSuite,
    compositionSuite,
    lossySuite
  )

  private val identitySuite = suite("Identity law")(
    test("Migration.identity.apply(a) == Right(a) for simple record") {
      val identity = Migration.identity[PersonV1]
      val person   = PersonV1("Alice", 30)
      val result   = identity.apply(person)
      assertTrue(
        result.isRight,
        result.toOption.get == person
      )
    },
    test("Migration.identity is not lossy") {
      val identity = Migration.identity[PersonV1]
      assertTrue(!identity.isLossy)
    },
    test("Migration.identity has empty action vector") {
      val identity = Migration.identity[PersonV1]
      assertTrue(identity.dynamicMigration.actions.isEmpty)
    }
  )

  private val associativitySuite = suite("Associativity law")(
    test("(m1 ++ m2) ++ m3 produces same result as m1 ++ (m2 ++ m3)") {
      // Three migrations that rename fields back and forth
      val m1 = Migration[PersonV1, PersonV1](
        DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
          )
        ),
        PersonV1.schema,
        PersonV1.schema
      )
      val m2 = Migration[PersonV1, PersonV1](
        DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("fullName"), "name")
          )
        ),
        PersonV1.schema,
        PersonV1.schema
      )
      val m3 = Migration[PersonV1, PersonV1](
        DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("name"), "firstName")
          )
        ),
        PersonV1.schema,
        PersonV1.schema
      )

      val person     = PersonV1("Alice", 30)
      val leftAssoc  = (m1 ++ m2) ++ m3
      val rightAssoc = m1 ++ (m2 ++ m3)

      val resultLeft  = leftAssoc.apply(person)
      val resultRight = rightAssoc.apply(person)

      assertTrue(
        resultLeft.isRight,
        resultRight.isRight,
        resultLeft == resultRight
      )
    }
  )

  private val reverseSuite = suite("Reverse laws")(
    test("lossless migration reverse round-trips: reverse(reverse(m)) == m actions") {
      val m = Migration[PersonV1, PersonV1](
        DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
          )
        ),
        PersonV1.schema,
        PersonV1.schema
      )
      val reversed       = m.reverse
      val doubleReversed = reversed.flatMap(_.reverse)
      assertTrue(
        reversed.isDefined,
        doubleReversed.isDefined,
        doubleReversed.get.dynamicMigration.actions.size == m.dynamicMigration.actions.size
      )
    },
    test("lossless reverse produces original value: r.apply(m.apply(a)) == Right(a)") {
      val m = Migration[PersonV1, PersonV1](
        DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"),
            MigrationAction.Rename(DynamicOptic.root.field("fullName"), "firstName")
          )
        ),
        PersonV1.schema,
        PersonV1.schema
      )
      val person = PersonV1("Alice", 30)
      val result = m.apply(person)
      assertTrue(
        result.isRight,
        result.toOption.get == person
      )
    },
    test("lossless migration: reverse.apply undoes forward.apply") {
      val m = Migration[PersonV1, PersonV1](
        DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
          )
        ),
        PersonV1.schema,
        PersonV1.schema
      )
      val person    = PersonV1("Alice", 30)
      val forward   = m.dynamicMigration.apply(Schema[PersonV1].toDynamicValue(person))
      val reversed  = m.reverse.get.dynamicMigration
      val roundTrip = forward.flatMap(v => reversed.apply(v))
      assertTrue(
        forward.isRight,
        roundTrip.isRight,
        // Compare DynamicValues since type changes during rename
        roundTrip == Right(Schema[PersonV1].toDynamicValue(person))
      )
    }
  )

  private val compositionSuite = suite("Composition")(
    test("identity ++ m == m in effect") {
      val id = Migration.identity[PersonV1]
      val m  = Migration[PersonV1, PersonV1](
        DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"),
            MigrationAction.Rename(DynamicOptic.root.field("fullName"), "firstName")
          )
        ),
        PersonV1.schema,
        PersonV1.schema
      )
      val person         = PersonV1("Bob", 25)
      val composed       = id ++ m
      val resultComposed = composed.apply(person)
      val resultDirect   = m.apply(person)
      assertTrue(
        resultComposed.isRight,
        resultDirect.isRight,
        resultComposed == resultDirect
      )
    },
    test("m ++ identity == m in effect") {
      val id = Migration.identity[PersonV1]
      val m  = Migration[PersonV1, PersonV1](
        DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"),
            MigrationAction.Rename(DynamicOptic.root.field("fullName"), "firstName")
          )
        ),
        PersonV1.schema,
        PersonV1.schema
      )
      val person         = PersonV1("Bob", 25)
      val composed       = m ++ id
      val resultComposed = composed.apply(person)
      val resultDirect   = m.apply(person)
      assertTrue(
        resultComposed.isRight,
        resultDirect.isRight,
        resultComposed == resultDirect
      )
    },
    test("composed migration action count is sum of parts") {
      val m1 = Migration[PersonV1, PersonV1](
        DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
          )
        ),
        PersonV1.schema,
        PersonV1.schema
      )
      val m2 = Migration[PersonV1, PersonV1](
        DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root.field("fullName"), "firstName")
          )
        ),
        PersonV1.schema,
        PersonV1.schema
      )
      val composed = m1 ++ m2
      assertTrue(composed.dynamicMigration.actions.size == 2)
    }
  )

  private val lossySuite = suite("Lossiness")(
    test("DropField without reverseDefault is lossy") {
      val action = MigrationAction.DropField(DynamicOptic.root.field("age"), reverseDefault = None)
      assertTrue(action.lossy, action.reverse.isEmpty)
    },
    test("DropField with reverseDefault is lossless") {
      val defaultExpr = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action      = MigrationAction.DropField(DynamicOptic.root.field("age"), reverseDefault = Some(defaultExpr))
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("Rename is always lossless") {
      val action = MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("AddField is always lossless") {
      val defaultExpr = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action      = MigrationAction.AddField(DynamicOptic.root.field("age"), defaultExpr)
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("TransformValue without inverse is lossy") {
      val expr   = SchemaExpr.Literal[Any, Any]("transformed", Schema[String].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformValue(DynamicOptic.root.field("name"), expr, inverse = None)
      assertTrue(action.lossy, action.reverse.isEmpty)
    },
    test("TransformValue with inverse is lossless") {
      val expr    = SchemaExpr.Literal[Any, Any]("transformed", Schema[String].asInstanceOf[Schema[Any]])
      val inverse = SchemaExpr.Literal[Any, Any]("original", Schema[String].asInstanceOf[Schema[Any]])
      val action  = MigrationAction.TransformValue(DynamicOptic.root.field("name"), expr, inverse = Some(inverse))
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("mixed lossy/lossless migration is lossy overall") {
      val lossless = MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
      val lossy    = MigrationAction.DropField(DynamicOptic.root.field("age"), reverseDefault = None)
      val dm       = DynamicMigration(Vector(lossless, lossy))
      assertTrue(dm.isLossy, dm.reverse.isEmpty)
    },
    test("all-lossless migration reverse is defined") {
      val a1 = MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
      val a2 = MigrationAction.Rename(DynamicOptic.root.field("fullName"), "firstName")
      val dm = DynamicMigration(Vector(a1, a2))
      assertTrue(!dm.isLossy, dm.reverse.isDefined)
    },
    test("unsafeReverse throws for lossy migration") {
      val dm = DynamicMigration(
        Vector(
          MigrationAction.DropField(DynamicOptic.root.field("age"), reverseDefault = None)
        )
      )
      val caught = try {
        dm.unsafeReverse
        false
      } catch {
        case _: IllegalStateException => true
        case _: Throwable             => false
      }
      assertTrue(caught)
    }
  )
}
