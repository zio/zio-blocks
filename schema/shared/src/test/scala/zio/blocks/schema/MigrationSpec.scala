package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object MigrationSpec extends SchemaBaseSpec {

  // ----- Test models -----

  case class PersonV1(name: String, age: Int)

  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(name: String, age: Int, email: String)

  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class PersonV3(fullName: String, age: Int)

  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived
  }

  case class Address(street: String, city: String)

  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class PersonWithAddress(name: String, address: Address)

  object PersonWithAddress {
    implicit val schema: Schema[PersonWithAddress] = Schema.derived
  }

  // ----- Test data -----

  val defaultEmail: DynamicValue = DynamicValue.string("unknown@example.com")

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    migrationApplySuite,
    identitySuite,
    compositionSuite,
    reverseSuite,
    builderSuite,
    defaultValueExprSuite
  )

  // ----- Migration.apply -----

  val migrationApplySuite: Spec[Any, Nothing] = suite("Migration.apply")(
    test("applies addField migration from V1 to V2") {
      val migration = Migration
        .builder[PersonV1, PersonV2]
        .addField(DynamicOptic.root, "email", defaultEmail)
        .build

      val result = migration(PersonV1("Alice", 30))
      assert(result)(isRight(equalTo(PersonV2("Alice", 30, "unknown@example.com"))))
    },
    test("applies dropField migration") {
      val migration = Migration
        .builder[PersonV2, PersonV1]
        .dropField(DynamicOptic.root, "email", defaultEmail)
        .build

      val result = migration(PersonV2("Alice", 30, "alice@example.com"))
      assert(result)(isRight(equalTo(PersonV1("Alice", 30))))
    },
    test("applies renameField migration") {
      val migration = Migration
        .builder[PersonV1, PersonV3]
        .renameField(DynamicOptic.root, "name", "fullName")
        .build

      val result = migration(PersonV1("Alice", 30))
      assert(result)(isRight(equalTo(PersonV3("Alice", 30))))
    }
  )

  // ----- Identity -----

  val identitySuite: Spec[Any, Nothing] = suite("Migration.identity")(
    test("identity migration returns same value") {
      val migration = Migration.identity[PersonV1]
      val person    = PersonV1("Alice", 30)
      assert(migration(person))(isRight(equalTo(person)))
    },
    test("identity migration is empty") {
      val migration = Migration.identity[PersonV1]
      assert(migration.isEmpty)(isTrue)
    }
  )

  // ----- Composition -----

  val compositionSuite: Spec[Any, Nothing] = suite("Migration.++")(
    test("composes two migrations") {
      val m1 = Migration
        .builder[PersonV1, PersonV2]
        .addField(DynamicOptic.root, "email", defaultEmail)
        .build

      val m2 = Migration
        .builder[PersonV2, PersonV3]
        .dropField(DynamicOptic.root, "email", defaultEmail)
        .renameField(DynamicOptic.root, "name", "fullName")
        .build

      val composed = m1 ++ m2
      val result   = composed(PersonV1("Alice", 30))
      assert(result)(isRight(equalTo(PersonV3("Alice", 30))))
    },
    test("andThen is alias for ++") {
      val m1 = Migration
        .builder[PersonV1, PersonV2]
        .addField(DynamicOptic.root, "email", defaultEmail)
        .build

      val m2 = Migration
        .builder[PersonV2, PersonV1]
        .dropField(DynamicOptic.root, "email", defaultEmail)
        .build

      val composed1 = m1 ++ m2
      val composed2 = m1 andThen m2
      val person    = PersonV1("Alice", 30)
      assert(composed1(person))(equalTo(composed2(person)))
    }
  )

  // ----- Reverse -----

  val reverseSuite: Spec[Any, Nothing] = suite("Migration.reverse")(
    test("reverse of addField is dropField") {
      val migration = Migration
        .builder[PersonV1, PersonV2]
        .addField(DynamicOptic.root, "email", defaultEmail)
        .build

      val person2 = PersonV2("Alice", 30, "unknown@example.com")
      val result  = migration.reverse(person2)
      assert(result)(isRight(equalTo(PersonV1("Alice", 30))))
    },
    test("reverse of rename is reverse rename") {
      val migration = Migration
        .builder[PersonV1, PersonV3]
        .renameField(DynamicOptic.root, "name", "fullName")
        .build

      val person3 = PersonV3("Alice", 30)
      val result  = migration.reverse(person3)
      assert(result)(isRight(equalTo(PersonV1("Alice", 30))))
    }
  )

  // ----- Builder -----

  val builderSuite: Spec[Any, Nothing] = suite("MigrationBuilder")(
    test("build with no actions produces identity migration") {
      val migration = Migration.builder[PersonV1, PersonV1].build
      assert(migration.isEmpty)(isTrue)
    },
    test("buildPartial produces same result as build") {
      val builder = Migration
        .builder[PersonV1, PersonV2]
        .addField(DynamicOptic.root, "email", defaultEmail)

      val m1     = builder.build
      val m2     = builder.buildPartial
      val person = PersonV1("Alice", 30)
      assert(m1(person))(equalTo(m2(person)))
    },
    test("addAction with raw action") {
      val migration = Migration
        .builder[PersonV1, PersonV3]
        .addAction(MigrationAction.Rename(DynamicOptic.root, "name", "fullName"))
        .build

      val result = migration(PersonV1("Bob", 25))
      assert(result)(isRight(equalTo(PersonV3("Bob", 25))))
    }
  )

  // ----- SchemaExpr.DefaultValue -----

  val defaultValueExprSuite: Spec[Any, Nothing] = suite("SchemaExpr.DefaultValue")(
    test("eval returns default value when schema has one") {
      val schemaWithDefault = PersonV1.schema.defaultValue(PersonV1("default", 0))
      val expr              = SchemaExpr.DefaultValue[Unit, PersonV1](schemaWithDefault)
      val result            = expr.eval(())
      assert(result)(isRight(equalTo(Seq(PersonV1("default", 0)))))
    },
    test("eval returns error when schema has no default") {
      val expr   = SchemaExpr.DefaultValue[Unit, PersonV1](PersonV1.schema)
      val result = expr.eval(())
      assert(result)(isLeft)
    },
    test("evalDynamic returns dynamic value when schema has default") {
      val intSchema = Schema[Int].defaultValue(42)
      val expr      = SchemaExpr.DefaultValue[Unit, Int](intSchema)
      val result    = expr.evalDynamic(())
      assert(result)(isRight)
    },
    test("evalDynamic returns error when schema has no default") {
      val expr   = SchemaExpr.DefaultValue[Unit, Int](Schema[Int])
      val result = expr.evalDynamic(())
      assert(result)(isLeft)
    },
    test("eval ignores input value") {
      val schemaWithDefault = Schema[String].defaultValue("hello")
      val expr              = SchemaExpr.DefaultValue[Int, String](schemaWithDefault)
      assert(expr.eval(1))(equalTo(expr.eval(999)))
    }
  )
}
