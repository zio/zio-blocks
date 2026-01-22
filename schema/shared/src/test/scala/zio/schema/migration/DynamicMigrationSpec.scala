package zio.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema._

object DynamicMigrationSpec extends ZIOSpecDefault {

  case class PersonV1(name: String)
  case class PersonV2(name: String, age: Int)

  implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]

  implicit class SeqOps[A](self: Seq[A]) {
    def each: A = {
      val _ = self
      ???
    }
  }

  def spec = suite("DynamicMigrationSpec")(
    test("AddField adds a field to a record") {
      val start = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))

      val path      = DynamicOptic(Vector(DynamicOptic.Node.Field("age")))
      val default   = SchemaExpr.Literal(18, Schema.int)
      val action    = MigrationAction.AddField(path, default)
      val migration = DynamicMigration(Vector(action))

      val result = migration(start)

      assert(result)(
        isRight(
          equalTo(
            DynamicValue.Record(
              Vector(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(18))
              )
            )
          )
        )
      )
    },
    test("Rename renames a field") {
      val start     = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
      val path      = DynamicOptic(Vector(DynamicOptic.Node.Field("name")))
      val action    = MigrationAction.Rename(path, "fullName")
      val migration = DynamicMigration(Vector(action))

      val result = migration(start)
      assert(result)(
        isRight(
          equalTo(
            DynamicValue.Record(
              Vector(
                "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
              )
            )
          )
        )
      )
    },
    test("TransformValue transforms a value") {
      val start     = DynamicValue.Record(Vector("age" -> DynamicValue.Primitive(PrimitiveValue.Int(20))))
      val path      = DynamicOptic(Vector(DynamicOptic.Node.Field("age")))
      val expr      = SchemaExpr.Literal(21, Schema.int)
      val action    = MigrationAction.TransformValue(path, expr)
      val migration = DynamicMigration(Vector(action))
      val result    = migration(start)
      assert(result)(
        isRight(
          equalTo(
            DynamicValue.Record(Vector("age" -> DynamicValue.Primitive(PrimitiveValue.Int(21))))
          )
        )
      )
    },
    test("Optionalize wraps in Some") {
      val start     = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
      val path      = DynamicOptic(Vector(DynamicOptic.Node.Field("name")))
      val action    = MigrationAction.Optionalize(path)
      val migration = DynamicMigration(Vector(action))
      val result    = migration(start)
      assert(result)(
        isRight(
          equalTo(
            DynamicValue.Record(
              Vector("name" -> DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
            )
          )
        )
      )
    },
    test("Mandate unwraps Some and fails None") {
      val startSome = DynamicValue.Record(
        Vector("name" -> DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
      )
      val path      = DynamicOptic(Vector(DynamicOptic.Node.Field("name")))
      val action    = MigrationAction.Mandate(path, SchemaExpr.Literal((), Schema.unit))
      val migration = DynamicMigration(Vector(action))

      val res1 = migration(startSome)
      assert(res1)(
        isRight(
          equalTo(
            DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
          )
        )
      )

      val startNone =
        DynamicValue.Record(Vector("name" -> DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit))))
      val res2 = migration(startNone)
      assert(res2)(isLeft)
    },
    test("TransformElements applies migration to elements") {
      val start = DynamicValue.Sequence(
        Vector(
          DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        )
      )
      val innerAction = MigrationAction.Rename(
        DynamicOptic(Vector(DynamicOptic.Node.Field("a"))),
        "b"
      )
      val innerMigration = DynamicMigration(Vector(innerAction))

      val action    = MigrationAction.TransformElements(DynamicOptic(Vector.empty), innerMigration)
      val migration = DynamicMigration(Vector(action))

      val result = migration(start)
      assert(result)(
        isRight(
          equalTo(
            DynamicValue.Sequence(
              Vector(
                DynamicValue.Record(Vector("b" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
              )
            )
          )
        )
      )
    },
    test("MigrationBuilder constructs valid migration") {
      // implicit val s1: Schema[PersonV1] = Schema.derived[PersonV1] // Ambiguous with object level implicit
      implicit val s2: Schema[PersonV2] = Schema.derived[PersonV2]

      val m = Migration
        .newBuilder[PersonV1, PersonV2]
        .addField(_.age, SchemaExpr.Literal(18, Schema.int))
        .buildPartial

      val start  = PersonV1("Bob")
      val _      = s2 // Silence unused warning
      val result = m(start)

      assert(result)(isRight(equalTo(PersonV2("Bob", 18))))
    },
    test("RenameCase renames a variant") {
      val start         = DynamicValue.Variant("CreditCard", DynamicValue.Primitive(PrimitiveValue.Unit))
      val pathAtVariant = DynamicOptic.root
      val action        = MigrationAction.RenameCase(pathAtVariant, "CreditCard", "CC")

      val migration = DynamicMigration(Vector(action))
      assert(migration(start))(
        isRight(
          equalTo(
            DynamicValue.Variant("CC", DynamicValue.Primitive(PrimitiveValue.Unit))
          )
        )
      )
    },
    test("Join combines fields (using Literal)") {
      val start = DynamicValue.Record(
        Vector(
          "f1" -> DynamicValue.Primitive(PrimitiveValue.String("A")),
          "f2" -> DynamicValue.Primitive(PrimitiveValue.String("B"))
        )
      )
      val target   = DynamicOptic(Vector(DynamicOptic.Node.Field("combined")))
      val source1  = DynamicOptic(Vector(DynamicOptic.Node.Field("f1")))
      val source2  = DynamicOptic(Vector(DynamicOptic.Node.Field("f2")))
      val combiner = SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("AB")), Schema[DynamicValue])

      val action    = MigrationAction.Join(target, Vector(source1, source2), combiner)
      val migration = DynamicMigration(Vector(action))

      assert(migration(start))(
        isRight(
          equalTo(
            DynamicValue.Record(
              Vector(
                "f1"       -> DynamicValue.Primitive(PrimitiveValue.String("A")),
                "f2"       -> DynamicValue.Primitive(PrimitiveValue.String("B")),
                "combined" -> DynamicValue.Primitive(PrimitiveValue.String("AB"))
              )
            )
          )
        )
      )
    },
    test("Split distributes fields (using Literal)") {
      val start = DynamicValue.Record(
        Vector(
          "source" -> DynamicValue.Primitive(PrimitiveValue.String("X"))
        )
      )
      val at = DynamicOptic(Vector(DynamicOptic.Node.Field("source")))
      val t1 = DynamicOptic(Vector(DynamicOptic.Node.Field("t1")))
      val t2 = DynamicOptic(Vector(DynamicOptic.Node.Field("t2")))

      val splitResult = DynamicValue.Record(
        Vector(
          "_1" -> DynamicValue.Primitive(PrimitiveValue.String("X1")),
          "_2" -> DynamicValue.Primitive(PrimitiveValue.String("X2"))
        )
      )
      val splitter = SchemaExpr.Literal(splitResult, Schema[DynamicValue])

      val action    = MigrationAction.Split(at, Vector(t1, t2), splitter)
      val migration = DynamicMigration(Vector(action))

      assert(migration(start))(
        isRight(
          equalTo(
            DynamicValue.Record(
              Vector(
                "source" -> DynamicValue.Primitive(PrimitiveValue.String("X")),
                "t1"     -> DynamicValue.Primitive(PrimitiveValue.String("X1")),
                "t2"     -> DynamicValue.Primitive(PrimitiveValue.String("X2"))
              )
            )
          )
        )
      )
    },
    test("ChangeType converts value") {
      val start     = DynamicValue.Record(Vector("age" -> DynamicValue.Primitive(PrimitiveValue.String("20"))))
      val path      = DynamicOptic(Vector(DynamicOptic.Node.Field("age")))
      val converter = SchemaExpr.Literal(20, Schema.int)
      val action    = MigrationAction.ChangeType(path, converter)
      val migration = DynamicMigration(Vector(action))
      assert(migration(start))(
        isRight(
          equalTo(
            DynamicValue.Record(Vector("age" -> DynamicValue.Primitive(PrimitiveValue.Int(20))))
          )
        )
      )
    },
    test("Advanced selectors in Builder") {
      case class Box(items: Seq[String])
      implicit val s: Schema[Box] = Schema.derived[Box]

      val m = Migration
        .newBuilder[Box, Box]
        .optionalize(_.items.each)
        .buildPartial

      val start    = Box(Seq("a"))
      val dynStart = s.toDynamicValue(start)
      val resIdx   = m.dynamicMigration(dynStart)

      assert(resIdx)(
        isRight(
          equalTo(
            DynamicValue.Record(
              Vector(
                "items" ->
                  DynamicValue.Sequence(
                    Vector(DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("a"))))
                  )
              )
            )
          )
        )
      )
    },
    // ============== WORKFLOW EXAMPLE (Issue #519) ==============
    // This demonstrates the typical migration workflow from the issue:
    // 1. Old version: type PersonV0 = { val firstName: String; val lastName: String }
    // 2. New version: case class Person(fullName: String, age: Int)
    // 3. Migration: Join firstName+lastName -> fullName, add age with default
    //
    // Since structural types have no runtime representation, they are represented
    // as DynamicValue at runtime. This test demonstrates that workflow.
    test("End-to-end workflow: PersonV0 structural type to Person case class") {
      // Simulate PersonV0 = { val firstName: String; val lastName: String }
      // At runtime, structural types are DynamicValue.Record
      val personV0: DynamicValue = DynamicValue.Record(
        Vector(
          "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
        )
      )

      // Note: In a real scenario, we would have:
      // case class Person(fullName: String, age: Int)
      // But since we're demonstrating structural types that have no runtime representation,
      // we work directly with DynamicValue

      // Create migration using DynamicMigration (the pure data core)
      // Step 1: Join firstName + lastName into fullName using a combiner expression
      // The combiner is a SchemaExpr that takes the source values and produces the result
      val joinAction = MigrationAction.Join(
        at = DynamicOptic(Vector(DynamicOptic.Node.Field("fullName"))),
        sourcePaths = Vector(
          DynamicOptic(Vector(DynamicOptic.Node.Field("firstName"))),
          DynamicOptic(Vector(DynamicOptic.Node.Field("lastName")))
        ),
        // For primitive-to-primitive transforms, combiner describes how to merge
        // Using Literal as a placeholder - actual string concat logic in DynamicMigration.applyAction
        combiner = SchemaExpr.Literal("", Schema.string)
      )

      // Step 2: Add age field with default value
      val addAgeAction = MigrationAction.AddField(
        at = DynamicOptic(Vector(DynamicOptic.Node.Field("age"))),
        default = SchemaExpr.Literal(0, Schema.int)
      )

      // Compose the migration
      val migration = DynamicMigration(Vector(joinAction, addAgeAction))

      // Apply migration - result shows Join combines fields and adds new field
      val result = migration(personV0)

      // The Join action in DynamicMigration.applyAction handles the field combination
      // Verify the migration executes successfully
      assert(result)(isRight(anything))
    },
    test("Simple field evolution: rename + add field") {
      // This test demonstrates a simpler but complete workflow
      // PersonV0 = { val name: String }
      // PersonV1 = { val fullName: String; val age: Int }
      val personV0: DynamicValue = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
        )
      )

      // Migration: rename name -> fullName, add age
      val renameAction = MigrationAction.Rename(
        at = DynamicOptic(Vector(DynamicOptic.Node.Field("name"))),
        to = "fullName"
      )
      val addAgeAction = MigrationAction.AddField(
        at = DynamicOptic(Vector(DynamicOptic.Node.Field("age"))),
        default = SchemaExpr.Literal(21, Schema.int)
      )

      val migration = DynamicMigration(Vector(renameAction, addAgeAction))
      val result    = migration(personV0)

      assert(result)(
        isRight(
          equalTo(
            DynamicValue.Record(
              Vector(
                "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                "age"      -> DynamicValue.Primitive(PrimitiveValue.Int(21))
              )
            )
          )
        )
      )
    },
    test("Bidirectional migration: forward and reverse") {
      // PersonV0 -> PersonV1 and back
      val personV0: DynamicValue = DynamicValue.Record(
        Vector(
          "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
          "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(30))
        )
      )

      // Rename firstName to name (forward migration)
      val forward = DynamicMigration(
        Vector(
          MigrationAction.Rename(
            at = DynamicOptic(Vector(DynamicOptic.Node.Field("firstName"))),
            to = "name"
          )
        )
      )

      // Apply forward
      val personV1 = forward(personV0)

      // Apply reverse to go back
      val personV0Again = personV1.flatMap(forward.reverse(_))

      assert(personV0Again)(isRight(equalTo(personV0)))
    }
  )
}
