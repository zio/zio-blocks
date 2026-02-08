package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object MigrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types
  // ─────────────────────────────────────────────────────────────────────────

  case class PersonV0(name: String, age: Int)
  object PersonV0 {
    implicit val schema: Schema[PersonV0] = Schema.derived
  }

  case class PersonV1(name: String, age: Int, email: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int, email: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class SimpleRecord(x: Int, y: String)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  case class Nested(inner: SimpleRecord, label: String)
  object Nested {
    implicit val schema: Schema[Nested] = Schema.derived
  }

  case class WithOption(name: String, tag: Option[String])
  object WithOption {
    implicit val schema: Schema[WithOption] = Schema.derived
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Dynamic Value Helpers
  // ─────────────────────────────────────────────────────────────────────────

  private def stringDV(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def intDV(i: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(i))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    identitySuite,
    applySuite,
    compositionSuite,
    andThenSuite,
    reverseSuite,
    isEmptySuite,
    errorPropagationSuite
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Identity Law
  // ─────────────────────────────────────────────────────────────────────────

  private val identitySuite = suite("identity")(
    test("identity migration returns Right(a) for a simple case class") {
      val m      = Migration.identity[SimpleRecord]
      val value  = SimpleRecord(42, "hello")
      val result = m(value)
      assertTrue(result == Right(value))
    },
    test("identity migration returns Right(a) for a nested case class") {
      val m      = Migration.identity[Nested]
      val value  = Nested(SimpleRecord(1, "a"), "label")
      val result = m(value)
      assertTrue(result == Right(value))
    },
    test("identity migration returns Right(a) for case class with Option") {
      val m      = Migration.identity[WithOption]
      val value1 = WithOption("test", Some("tag"))
      val value2 = WithOption("test", None)
      assertTrue(
        m(value1) == Right(value1),
        m(value2) == Right(value2)
      )
    },
    test("identity migration returns Right(a) for primitive types") {
      val m      = Migration.identity[String]
      val result = m("hello")
      assertTrue(result == Right("hello"))
    },
    test("identity migration returns Right(a) for Int") {
      val m      = Migration.identity[Int]
      val result = m(42)
      assertTrue(result == Right(42))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Apply: typed migration transforms data between case classes
  // ─────────────────────────────────────────────────────────────────────────

  private val applySuite = suite("apply")(
    test("migration that adds a field transforms V0 to V1") {
      // PersonV0(name, age) -> PersonV1(name, age, email)
      val addEmail = DynamicMigration(
        Chunk(
          MigrationAction.AddField(
            DynamicOptic.root,
            "email",
            MigrationExpr.Literal(stringDV("default@example.com"))
          )
        )
      )
      val m      = Migration.fromDynamic(addEmail, PersonV0.schema, PersonV1.schema)
      val result = m(PersonV0("Alice", 30))
      assertTrue(result == Right(PersonV1("Alice", 30, "default@example.com")))
    },
    test("migration that removes a field transforms V1 to V0") {
      // PersonV1(name, age, email) -> PersonV0(name, age)
      val dropEmail = DynamicMigration(
        Chunk(
          MigrationAction.DropField(
            DynamicOptic.root,
            "email",
            MigrationExpr.Literal(stringDV(""))
          )
        )
      )
      val m      = Migration.fromDynamic(dropEmail, PersonV1.schema, PersonV0.schema)
      val result = m(PersonV1("Alice", 30, "alice@example.com"))
      assertTrue(result == Right(PersonV0("Alice", 30)))
    },
    test("migration that renames a field transforms V1 to V2") {
      // PersonV1(name, age, email) -> PersonV2(fullName, age, email)
      val renameName = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
      )
      val m      = Migration.fromDynamic(renameName, PersonV1.schema, PersonV2.schema)
      val result = m(PersonV1("Alice", 30, "alice@example.com"))
      assertTrue(result == Right(PersonV2("Alice", 30, "alice@example.com")))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Composition: (m1 ++ m2).apply(a) == m1.apply(a).flatMap(m2.apply)
  // ─────────────────────────────────────────────────────────────────────────

  private val compositionSuite = suite("composition")(
    test("composed migration produces same result as sequential application") {
      // V0 -> V1 (add email)
      val addEmail = DynamicMigration(
        Chunk(
          MigrationAction.AddField(
            DynamicOptic.root,
            "email",
            MigrationExpr.Literal(stringDV("default@example.com"))
          )
        )
      )
      val m1 = Migration.fromDynamic(addEmail, PersonV0.schema, PersonV1.schema)

      // V1 -> V2 (rename name -> fullName)
      val renameName = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
      )
      val m2 = Migration.fromDynamic(renameName, PersonV1.schema, PersonV2.schema)

      val input = PersonV0("Alice", 30)

      // Composed
      val composed       = m1 ++ m2
      val composedResult = composed(input)

      // Sequential
      val sequentialResult = m1(input).flatMap(m2.apply)

      assertTrue(composedResult == sequentialResult)
    },
    test("associativity: ((m1 ++ m2) ++ m3).apply(a) == (m1 ++ (m2 ++ m3)).apply(a)") {
      // m1: V0 -> V1 (add email)
      val addEmail = DynamicMigration(
        Chunk(
          MigrationAction.AddField(
            DynamicOptic.root,
            "email",
            MigrationExpr.Literal(stringDV("default@example.com"))
          )
        )
      )
      val m1 = Migration.fromDynamic(addEmail, PersonV0.schema, PersonV1.schema)

      // m2: V1 -> V2 (rename name -> fullName)
      val renameName = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
      )
      val m2 = Migration.fromDynamic(renameName, PersonV1.schema, PersonV2.schema)

      // m3: V2 -> V2 (identity; just a no-op migration)
      val m3 = Migration.identity[PersonV2]

      val input = PersonV0("Alice", 30)

      val leftAssoc  = (m1 ++ m2) ++ m3
      val rightAssoc = m1 ++ (m2 ++ m3)

      assertTrue(leftAssoc(input) == rightAssoc(input))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // andThen alias
  // ─────────────────────────────────────────────────────────────────────────

  private val andThenSuite = suite("andThen")(
    test("andThen is equivalent to ++") {
      val addEmail = DynamicMigration(
        Chunk(
          MigrationAction.AddField(
            DynamicOptic.root,
            "email",
            MigrationExpr.Literal(stringDV("default@example.com"))
          )
        )
      )
      val m1 = Migration.fromDynamic(addEmail, PersonV0.schema, PersonV1.schema)

      val renameName = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
      )
      val m2 = Migration.fromDynamic(renameName, PersonV1.schema, PersonV2.schema)

      val input = PersonV0("Alice", 30)

      val viaAndThen  = m1.andThen(m2)
      val viaPlusPlus = m1 ++ m2

      assertTrue(viaAndThen(input) == viaPlusPlus(input))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Reverse
  // ─────────────────────────────────────────────────────────────────────────

  private val reverseSuite = suite("reverse")(
    test("double reverse produces the same dynamic migration") {
      val addEmail = DynamicMigration(
        Chunk(
          MigrationAction.AddField(
            DynamicOptic.root,
            "email",
            MigrationExpr.Literal(stringDV("default@example.com"))
          )
        )
      )
      val m = Migration.fromDynamic(addEmail, PersonV0.schema, PersonV1.schema)
      assertTrue(m.reverse.reverse.dynamicMigration == m.dynamicMigration)
    },
    test("reverse swaps source and target schemas") {
      val addEmail = DynamicMigration(
        Chunk(
          MigrationAction.AddField(
            DynamicOptic.root,
            "email",
            MigrationExpr.Literal(stringDV("default@example.com"))
          )
        )
      )
      val m = Migration.fromDynamic(addEmail, PersonV0.schema, PersonV1.schema)
      val r = m.reverse
      assertTrue(
        r.sourceSchema == m.targetSchema,
        r.targetSchema == m.sourceSchema
      )
    },
    test("semantic inverse: m.apply(a).flatMap(m.reverse.apply) == Right(a)") {
      // Rename is perfectly invertible
      val renameName = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
      )
      val m         = Migration.fromDynamic(renameName, PersonV1.schema, PersonV2.schema)
      val input     = PersonV1("Alice", 30, "alice@example.com")
      val roundTrip = m(input).flatMap(m.reverse.apply)
      assertTrue(roundTrip == Right(input))
    },
    test("semantic inverse with add/drop field") {
      // AddField is invertible when defaults are stored
      val addEmail = DynamicMigration(
        Chunk(
          MigrationAction.AddField(
            DynamicOptic.root,
            "email",
            MigrationExpr.Literal(stringDV("default@example.com"))
          )
        )
      )
      val m         = Migration.fromDynamic(addEmail, PersonV0.schema, PersonV1.schema)
      val input     = PersonV0("Alice", 30)
      val roundTrip = m(input).flatMap(m.reverse.apply)
      assertTrue(roundTrip == Right(input))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // isEmpty
  // ─────────────────────────────────────────────────────────────────────────

  private val isEmptySuite = suite("isEmpty")(
    test("identity migration is empty") {
      val m = Migration.identity[SimpleRecord]
      assertTrue(m.isEmpty)
    },
    test("non-empty migration is not empty") {
      val addField = DynamicMigration(
        Chunk(
          MigrationAction.AddField(
            DynamicOptic.root,
            "email",
            MigrationExpr.Literal(stringDV(""))
          )
        )
      )
      val m = Migration.fromDynamic(addField, PersonV0.schema, PersonV1.schema)
      assertTrue(!m.isEmpty)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Error Propagation
  // ─────────────────────────────────────────────────────────────────────────

  private val errorPropagationSuite = suite("error propagation")(
    test("migration error propagates when action fails") {
      // Try to rename a field that does not exist
      val badRename = DynamicMigration(
        Chunk(
          MigrationAction.Rename(DynamicOptic.root, "nonexistent", "other")
        )
      )
      val m      = Migration.fromDynamic(badRename, SimpleRecord.schema, SimpleRecord.schema)
      val result = m(SimpleRecord(1, "hello"))
      assertTrue(result.isLeft)
    },
    test("target conversion error produces CustomError") {
      // Add a field with wrong type - will fail on fromDynamicValue
      // We add an integer field but the target schema expects a String for "email"
      val addEmailAsInt = DynamicMigration(
        Chunk(
          MigrationAction.AddField(
            DynamicOptic.root,
            "email",
            MigrationExpr.Literal(intDV(42))
          )
        )
      )
      val m      = Migration.fromDynamic(addEmailAsInt, PersonV0.schema, PersonV1.schema)
      val result = m(PersonV0("Alice", 30))
      assertTrue(result.isLeft)
    }
  )
}
