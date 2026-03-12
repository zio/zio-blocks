package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema, SchemaBaseSpec}
import zio.test.Assertion._
import zio.test._

object MigrationSpec extends SchemaBaseSpec {

  private def intDV(n: Int): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(n))

  private def fromActionInt(action: MigrationAction): Migration[Int, Int] =
    new Migration(Schema[Int], Schema[Int], DynamicMigration.single(action))

  private def fromActionsInt(actions: MigrationAction*): Migration[Int, Int] =
    new Migration(Schema[Int], Schema[Int], new DynamicMigration(actions.toVector))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    identitySuite,
    factoryMethodsSuite,
    compositionSuite,
    reverseSuite,
    accessorsSuite,
    dynamicMigrationDirectSuite
  )

  private val identitySuite = suite("Identity")(
    test("identity migration on Int") {
      val m = Migration.identity[Int]
      assert(m(42))(isRight(equalTo(42)))
    },
    test("identity migration on String") {
      val m = Migration.identity[String]
      assert(m("hello"))(isRight(equalTo("hello")))
    },
    test("identity migration on Boolean") {
      val m = Migration.identity[Boolean]
      assert(m(true))(isRight(equalTo(true)))
    },
    test("identity migration on Long") {
      val m = Migration.identity[Long]
      assert(m(100L))(isRight(equalTo(100L)))
    },
    test("identity migration on Double") {
      val m = Migration.identity[Double]
      assert(m(3.14))(isRight(equalTo(3.14)))
    },
    test("identity migration isEmpty") {
      val m = Migration.identity[Int]
      assert(m.isEmpty)(isTrue)
    },
    test("identity migration size is 0") {
      val m = Migration.identity[Int]
      assert(m.size)(equalTo(0))
    }
  )

  private val factoryMethodsSuite = suite("Factory methods")(
    test("fromAction creates single-action migration") {
      val m = fromActionInt(
        MigrationAction.TransformValue(DynamicOptic.root, DynamicSchemaExpr.Literal(intDV(99)))
      )
      assert(m.size)(equalTo(1))
    },
    test("fromActions creates multi-action migration") {
      val m = fromActionsInt(
        MigrationAction.TransformValue(DynamicOptic.root, DynamicSchemaExpr.Literal(intDV(1))),
        MigrationAction.TransformValue(DynamicOptic.root, DynamicSchemaExpr.Literal(intDV(2)))
      )
      assert(m.size)(equalTo(2))
    },
    test("fromDynamic wraps an existing DynamicMigration") {
      val dm = DynamicMigration.single(
        MigrationAction.TransformValue(DynamicOptic.root, DynamicSchemaExpr.Literal(intDV(42)))
      )
      val m = new Migration(Schema[Int], Schema[Int], dm)
      assert(m.size)(equalTo(1)) &&
      assert(m.dynamicMigration)(equalTo(dm))
    }
  )

  private val compositionSuite = suite("Composition")(
    test("++ composes two typed migrations") {
      val m1       = Migration.identity[Int]
      val m2       = Migration.identity[Int]
      val composed = m1 ++ m2
      assert(composed(42))(isRight(equalTo(42)))
    },
    test("andThen is alias for ++") {
      val m1 = Migration.identity[Int]
      val m2 = Migration.identity[Int]
      val r1 = (m1 ++ m2)(42)
      val r2 = m1.andThen(m2)(42)
      assert(r1)(equalTo(r2))
    },
    test("composed migration has sum of sizes") {
      val m1 = fromActionInt(
        MigrationAction.TransformValue(DynamicOptic.root, DynamicSchemaExpr.Literal(intDV(1)))
      )
      val m2 = fromActionsInt(
        MigrationAction.TransformValue(DynamicOptic.root, DynamicSchemaExpr.Literal(intDV(2))),
        MigrationAction.TransformValue(DynamicOptic.root, DynamicSchemaExpr.Literal(intDV(3)))
      )
      val composed = m1 ++ m2
      assert(composed.size)(equalTo(3))
    }
  )

  private val reverseSuite = suite("Reverse")(
    test("reverse of identity is identity") {
      val m = Migration.identity[Int]
      assert(m.reverse.isEmpty)(isTrue)
    },
    test("reverse swaps schemas") {
      val dm       = DynamicMigration.empty
      val m        = new Migration(Schema[Int], Schema[String], dm)
      val reversed = m.reverse
      assert(reversed.sourceSchema)(equalTo(Schema[String])) &&
      assert(reversed.targetSchema)(equalTo(Schema[Int]))
    }
  )

  private val accessorsSuite = suite("Accessors")(
    test("actions returns the action vector") {
      val action = MigrationAction.TransformValue(DynamicOptic.root, DynamicSchemaExpr.Literal(intDV(1)))
      val m      = fromActionInt(action)
      assert(m.actions)(equalTo(Vector(action)))
    }
  )

  private val dynamicMigrationDirectSuite = suite("Direct DynamicMigration usage")(
    test("DynamicMigration.empty.isEmpty") {
      assert(DynamicMigration.empty.isEmpty)(isTrue)
    },
    test("DynamicMigration.single creates one-action migration") {
      val dm = DynamicMigration.single(MigrationAction.Optionalize(DynamicOptic.root))
      assert(dm.size)(equalTo(1))
    }
  )
}
