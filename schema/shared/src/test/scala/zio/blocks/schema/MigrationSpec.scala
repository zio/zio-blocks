package zio.blocks.schema

import zio.test._

object MigrationSpec extends ZIOSpecDefault {

  // Helper to create primitive DynamicValues
  private def int(i: Int): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(i))
  private def str(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def bool(b: Boolean): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  // Helper to create records
  private def record(fields: (String, DynamicValue)*): DynamicValue.Record =
    DynamicValue.Record(fields.toVector)

  // Helper to create variants
  private def variant(caseName: String, payload: DynamicValue): DynamicValue.Variant =
    DynamicValue.Variant(caseName, payload)

  // Helper to create sequences
  private def seq(elements: DynamicValue*): DynamicValue.Sequence =
    DynamicValue.Sequence(elements.toVector)

  // Helper to create maps
  private def map(entries: (DynamicValue, DynamicValue)*): DynamicValue.Map =
    DynamicValue.Map(entries.toVector)

  // Standard test registry with common transforms
  private val testRegistry: TransformRegistry = TransformRegistry(
    transforms = scala.collection.immutable.Map(
      "uppercase" -> { (dv: DynamicValue) =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.String(s.toUpperCase)))
          case _ => Left("Expected string")
        }
      },
      "lowercase" -> { (dv: DynamicValue) =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.String(s.toLowerCase)))
          case _ => Left("Expected string")
        }
      },
      "double" -> { (dv: DynamicValue) =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.Int(n * 2)))
          case _ => Left("Expected int")
        }
      },
      "halve" -> { (dv: DynamicValue) =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.Int(n / 2)))
          case _ => Left("Expected int")
        }
      },
      "intToString" -> { (dv: DynamicValue) =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.String(n.toString)))
          case _ => Left("Expected int")
        }
      },
      "stringToInt" -> { (dv: DynamicValue) =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
            scala.util.Try(s.toInt).toEither.left.map(_ => "Invalid int").map(n => DynamicValue.Primitive(PrimitiveValue.Int(n)))
          case _ => Left("Expected string")
        }
      },
      "addPrefix" -> { (dv: DynamicValue) =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
            Right(DynamicValue.Primitive(PrimitiveValue.String("prefix_" + s)))
          case _ => Left("Expected string")
        }
      },
      "identity" -> { (dv: DynamicValue) => Right(dv) }
    ),
    predicates = scala.collection.immutable.Map(
      "isPositive" -> { (dv: DynamicValue) =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.Int(n)) => n > 0
          case _ => false
        }
      },
      "nonEmpty" -> { (dv: DynamicValue) =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) => s.nonEmpty
          case _ => false
        }
      },
      "keyIsName" -> { (dv: DynamicValue) =>
        dv match {
          case DynamicValue.Record(fields) =>
            fields.find(_._1 == "key").exists {
              case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "name"
              case _ => false
            }
          case _ => false
        }
      }
    )
  )

  implicit val registry: TransformRegistry = testRegistry

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("MigrationError")(
      test("pathNotFound creates correct error") {
        val path = DynamicOptic.root.field("missing")
        val error = MigrationError.pathNotFound(path)
        assertTrue(
          error.message.contains("Path not found"),
          error.errors.length == 1
        )
      },
      test("typeMismatch creates correct error") {
        val path = DynamicOptic.root.field("name")
        val error = MigrationError.typeMismatch(path, "Record", "Primitive")
        assertTrue(
          error.message.contains("Type mismatch"),
          error.message.contains("Record"),
          error.message.contains("Primitive")
        )
      },
      test("errors can be combined with ++") {
        val error1 = MigrationError.pathNotFound(DynamicOptic.root.field("a"))
        val error2 = MigrationError.pathNotFound(DynamicOptic.root.field("b"))
        val combined = error1 ++ error2
        assertTrue(combined.errors.length == 2)
      }
    ),

    suite("DynamicMigration - AddField")(
      test("adds field with default value to record") {
        val migration = DynamicMigration.addField("age", int(25))
        val input = record("name" -> str("John"))
        val result = migration(input)
        assertTrue(
          result == Right(record("name" -> str("John"), "age" -> int(25)))
        )
      },
      test("adds field at nested path") {
        val path = DynamicOptic.root.field("address")
        val migration = DynamicMigration.addField(path, "country", str("USA"))
        val input = record("name" -> str("John"), "address" -> record("city" -> str("NYC")))
        val result = migration(input)
        assertTrue(
          result == Right(record(
            "name" -> str("John"),
            "address" -> record("city" -> str("NYC"), "country" -> str("USA"))
          ))
        )
      },
      test("fails when path target is not a record") {
        val migration = DynamicMigration.addField("age", int(25))
        val input = str("not a record")
        val result = migration(input)
        assertTrue(result.isLeft)
      }
    ),

    suite("DynamicMigration - DropField")(
      test("drops field from record") {
        val migration = DynamicMigration.dropField("age")
        val input = record("name" -> str("John"), "age" -> int(30))
        val result = migration(input)
        assertTrue(result == Right(record("name" -> str("John"))))
      },
      test("drops field at nested path") {
        val path = DynamicOptic.root.field("address")
        val migration = DynamicMigration.dropField(path, "zip")
        val input = record("address" -> record("city" -> str("NYC"), "zip" -> str("10001")))
        val result = migration(input)
        assertTrue(
          result == Right(record("address" -> record("city" -> str("NYC"))))
        )
      },
      test("silently succeeds when field does not exist") {
        val migration = DynamicMigration.dropField("nonexistent")
        val input = record("name" -> str("John"))
        val result = migration(input)
        assertTrue(result == Right(record("name" -> str("John"))))
      }
    ),

    suite("DynamicMigration - RenameField")(
      test("renames field in record") {
        val migration = DynamicMigration.renameField("name", "fullName")
        val input = record("name" -> str("John"), "age" -> int(30))
        val result = migration(input)
        assertTrue(result == Right(record("fullName" -> str("John"), "age" -> int(30))))
      },
      test("renames field at nested path") {
        val path = DynamicOptic.root.field("person")
        val migration = DynamicMigration.renameField(path, "name", "fullName")
        val input = record("person" -> record("name" -> str("John")))
        val result = migration(input)
        assertTrue(
          result == Right(record("person" -> record("fullName" -> str("John"))))
        )
      },
      test("preserves field order after rename") {
        val migration = DynamicMigration.renameField("b", "bb")
        val input = record("a" -> int(1), "b" -> int(2), "c" -> int(3))
        val result = migration(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.map(_._1) == Vector("a", "bb", "c"))
          case _ => assertTrue(false)
        }
      }
    ),

    suite("DynamicMigration - TransformValue")(
      test("transforms value using registered transform") {
        val path = DynamicOptic.root.field("name")
        val migration = DynamicMigration(Vector(MigrationAction.TransformValue(path, "uppercase", None)))
        val input = record("name" -> str("john"))
        val result = migration(input)
        assertTrue(result == Right(record("name" -> str("JOHN"))))
      },
      test("fails when transform is not registered") {
        val path = DynamicOptic.root.field("name")
        val migration = DynamicMigration(Vector(MigrationAction.TransformValue(path, "unknown", None)))
        val input = record("name" -> str("john"))
        val result = migration(input)
        assertTrue(result.isLeft)
      },
      test("fails when transform returns error") {
        val path = DynamicOptic.root.field("count")
        val migration = DynamicMigration(Vector(MigrationAction.TransformValue(path, "uppercase", None)))
        val input = record("count" -> int(42))
        val result = migration(input)
        assertTrue(result.isLeft)
      }
    ),

    suite("DynamicMigration - ChangeType")(
      test("changes type using registered coercion") {
        val path = DynamicOptic.root.field("count")
        val migration = DynamicMigration(Vector(
          MigrationAction.ChangeType(path, "Int", "String", "intToString", None)
        ))
        val input = record("count" -> int(42))
        val result = migration(input)
        assertTrue(result == Right(record("count" -> str("42"))))
      },
      test("fails when coercion is not registered") {
        val path = DynamicOptic.root.field("count")
        val migration = DynamicMigration(Vector(
          MigrationAction.ChangeType(path, "Int", "String", "unknown", None)
        ))
        val input = record("count" -> int(42))
        val result = migration(input)
        assertTrue(result.isLeft)
      }
    ),

    suite("DynamicMigration - Optionalize")(
      test("wraps field value in Some variant") {
        val migration = DynamicMigration.optionalize("name")
        val input = record("name" -> str("John"))
        val result = migration(input)
        // Option is encoded as Variant("Some", Record(Vector("value" -> v)))
        assertTrue(
          result == Right(record(
            "name" -> variant("Some", record("value" -> str("John")))
          ))
        )
      },
      test("optionalizes nested field") {
        val path = DynamicOptic.root.field("person")
        val migration = DynamicMigration(Vector(MigrationAction.Optionalize(path, "age")))
        val input = record("person" -> record("name" -> str("John"), "age" -> int(30)))
        val result = migration(input)
        assertTrue(
          result == Right(record(
            "person" -> record(
              "name" -> str("John"),
              "age" -> variant("Some", record("value" -> int(30)))
            )
          ))
        )
      }
    ),

    suite("DynamicMigration - Mandate")(
      test("unwraps Some variant to direct value") {
        val migration = DynamicMigration.mandate("name", str("default"))
        val input = record("name" -> variant("Some", record("value" -> str("John"))))
        val result = migration(input)
        assertTrue(result == Right(record("name" -> str("John"))))
      },
      test("uses default for None variant") {
        val migration = DynamicMigration.mandate("name", str("default"))
        val input = record("name" -> variant("None", record()))
        val result = migration(input)
        assertTrue(result == Right(record("name" -> str("default"))))
      },
      test("leaves non-Option values unchanged") {
        val migration = DynamicMigration.mandate("name", str("default"))
        val input = record("name" -> str("already-required"))
        val result = migration(input)
        assertTrue(result == Right(record("name" -> str("already-required"))))
      }
    ),

    suite("DynamicMigration - RenameCase")(
      test("renames matching case") {
        val migration = DynamicMigration.renameCase("OldCase", "NewCase")
        val input = variant("OldCase", record("value" -> int(42)))
        val result = migration(input)
        assertTrue(result == Right(variant("NewCase", record("value" -> int(42)))))
      },
      test("leaves non-matching case unchanged") {
        val migration = DynamicMigration.renameCase("OldCase", "NewCase")
        val input = variant("OtherCase", record("value" -> int(42)))
        val result = migration(input)
        assertTrue(result == Right(variant("OtherCase", record("value" -> int(42)))))
      }
    ),

    suite("DynamicMigration - TransformCase")(
      test("transforms matching case payload") {
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformCase(DynamicOptic.root, "StringCase", "uppercase", None)
        ))
        val input = variant("StringCase", str("hello"))
        val result = migration(input)
        assertTrue(result == Right(variant("StringCase", str("HELLO"))))
      },
      test("leaves non-matching case unchanged") {
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformCase(DynamicOptic.root, "StringCase", "uppercase", None)
        ))
        val input = variant("OtherCase", str("hello"))
        val result = migration(input)
        assertTrue(result == Right(variant("OtherCase", str("hello"))))
      }
    ),

    suite("DynamicMigration - AddCase")(
      test("addCase is a no-op on existing data") {
        val migration = DynamicMigration(Vector(
          MigrationAction.AddCase(DynamicOptic.root, "NewCase")
        ))
        val input = variant("ExistingCase", record())
        val result = migration(input)
        assertTrue(result == Right(input))
      }
    ),

    suite("DynamicMigration - DropCase")(
      test("migrates dropped case to target case") {
        val migration = DynamicMigration(Vector(
          MigrationAction.DropCase(DynamicOptic.root, "OldCase", "NewCase", None)
        ))
        val input = variant("OldCase", record("value" -> int(42)))
        val result = migration(input)
        assertTrue(result == Right(variant("NewCase", record("value" -> int(42)))))
      },
      test("leaves non-matching case unchanged") {
        val migration = DynamicMigration(Vector(
          MigrationAction.DropCase(DynamicOptic.root, "OldCase", "NewCase", None)
        ))
        val input = variant("OtherCase", record())
        val result = migration(input)
        assertTrue(result == Right(variant("OtherCase", record())))
      },
      test("applies payload transform when migrating case") {
        val migration = DynamicMigration(Vector(
          MigrationAction.DropCase(DynamicOptic.root, "IntCase", "StringCase", Some("intToString"))
        ))
        val input = variant("IntCase", int(42))
        val result = migration(input)
        assertTrue(result == Right(variant("StringCase", str("42"))))
      }
    ),

    suite("DynamicMigration - TransformElements")(
      test("transforms all sequence elements") {
        val path = DynamicOptic.root.field("numbers")
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformElements(path, "double", None)
        ))
        val input = record("numbers" -> seq(int(1), int(2), int(3)))
        val result = migration(input)
        assertTrue(result == Right(record("numbers" -> seq(int(2), int(4), int(6)))))
      },
      test("fails if any element transform fails") {
        val path = DynamicOptic.root.field("items")
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformElements(path, "uppercase", None)
        ))
        val input = record("items" -> seq(str("hello"), int(42)))
        val result = migration(input)
        assertTrue(result.isLeft)
      }
    ),

    suite("DynamicMigration - TransformKeys")(
      test("transforms all map keys") {
        val path = DynamicOptic.root.field("data")
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformKeys(path, "uppercase", None)
        ))
        val input = record("data" -> map(str("name") -> str("John"), str("city") -> str("NYC")))
        val result = migration(input)
        assertTrue(result == Right(record("data" -> map(str("NAME") -> str("John"), str("CITY") -> str("NYC")))))
      }
    ),

    suite("DynamicMigration - TransformValues (map)")(
      test("transforms all map values") {
        val path = DynamicOptic.root.field("counts")
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformValues(path, "double", None)
        ))
        val input = record("counts" -> map(str("a") -> int(1), str("b") -> int(2)))
        val result = migration(input)
        assertTrue(result == Right(record("counts" -> map(str("a") -> int(2), str("b") -> int(4)))))
      }
    ),

    suite("DynamicMigration - FilterElements")(
      test("filters sequence elements by predicate") {
        val path = DynamicOptic.root.field("numbers")
        val migration = DynamicMigration(Vector(
          MigrationAction.FilterElements(path, "isPositive")
        ))
        val input = record("numbers" -> seq(int(-1), int(0), int(1), int(2)))
        val result = migration(input)
        assertTrue(result == Right(record("numbers" -> seq(int(1), int(2)))))
      },
      test("fails when predicate not found") {
        val path = DynamicOptic.root.field("numbers")
        val migration = DynamicMigration(Vector(
          MigrationAction.FilterElements(path, "unknown")
        ))
        val input = record("numbers" -> seq(int(1), int(2)))
        val result = migration(input)
        assertTrue(result.isLeft)
      }
    ),

    suite("DynamicMigration - FilterEntries")(
      test("filters map entries by predicate") {
        val path = DynamicOptic.root.field("data")
        val migration = DynamicMigration(Vector(
          MigrationAction.FilterEntries(path, "keyIsName")
        ))
        val input = record("data" -> map(str("name") -> str("John"), str("age") -> int(30)))
        val result = migration(input)
        assertTrue(result == Right(record("data" -> map(str("name") -> str("John")))))
      }
    ),

    suite("DynamicMigration - Path traversal")(
      test("traverses through nested fields") {
        val path = DynamicOptic.root.field("person").field("address")
        val migration = DynamicMigration.addField(path, "zip", str("10001"))
        val input = record(
          "person" -> record(
            "name" -> str("John"),
            "address" -> record("city" -> str("NYC"))
          )
        )
        val result = migration(input)
        assertTrue(
          result == Right(record(
            "person" -> record(
              "name" -> str("John"),
              "address" -> record("city" -> str("NYC"), "zip" -> str("10001"))
            )
          ))
        )
      },
      test("traverses through case in variant") {
        val path = DynamicOptic.root.caseOf("Person")
        val migration = DynamicMigration.addField(path, "age", int(25))
        val input = variant("Person", record("name" -> str("John")))
        val result = migration(input)
        assertTrue(
          result == Right(variant("Person", record("name" -> str("John"), "age" -> int(25))))
        )
      },
      test("skips non-matching case") {
        val path = DynamicOptic.root.caseOf("Person")
        val migration = DynamicMigration.addField(path, "age", int(25))
        val input = variant("Company", record("name" -> str("Acme")))
        val result = migration(input)
        assertTrue(result == Right(input))
      },
      test("traverses sequence elements with Elements node") {
        val path = DynamicOptic.root.field("items").elements
        val migration = DynamicMigration.addField(path, "processed", bool(true))
        val input = record(
          "items" -> seq(
            record("id" -> int(1)),
            record("id" -> int(2))
          )
        )
        val result = migration(input)
        assertTrue(
          result == Right(record(
            "items" -> seq(
              record("id" -> int(1), "processed" -> bool(true)),
              record("id" -> int(2), "processed" -> bool(true))
            )
          ))
        )
      },
      test("traverses by index with AtIndex") {
        val path = DynamicOptic.root.field("items").at(0)
        val migration = DynamicMigration.addField(path, "first", bool(true))
        val input = record(
          "items" -> seq(
            record("id" -> int(1)),
            record("id" -> int(2))
          )
        )
        val result = migration(input)
        assertTrue(
          result == Right(record(
            "items" -> seq(
              record("id" -> int(1), "first" -> bool(true)),
              record("id" -> int(2))
            )
          ))
        )
      },
      test("fails with pathNotFound for invalid index") {
        val path = DynamicOptic.root.field("items").at(10)
        val migration = DynamicMigration.addField(path, "flag", bool(true))
        val input = record("items" -> seq(record("id" -> int(1))))
        val result = migration(input)
        assertTrue(result.isLeft)
      }
    ),

    suite("DynamicMigration - Composition")(
      test("andThen chains migrations") {
        val m1 = DynamicMigration.addField("b", int(2))
        val m2 = DynamicMigration.addField("c", int(3))
        val combined = m1.andThen(m2)
        val input = record("a" -> int(1))
        val result = combined(input)
        assertTrue(result == Right(record("a" -> int(1), "b" -> int(2), "c" -> int(3))))
      },
      test(">>> is alias for andThen") {
        val m1 = DynamicMigration.addField("b", int(2))
        val m2 = DynamicMigration.addField("c", int(3))
        val combined = m1 >>> m2
        val input = record("a" -> int(1))
        val result = combined(input)
        assertTrue(result == Right(record("a" -> int(1), "b" -> int(2), "c" -> int(3))))
      },
      test("identity migration does nothing") {
        val migration = DynamicMigration.identity
        val input = record("a" -> int(1))
        val result = migration(input)
        assertTrue(result == Right(input))
      },
      test("composing with identity yields same result") {
        val m = DynamicMigration.addField("b", int(2))
        val input = record("a" -> int(1))
        val r1 = m(input)
        val r2 = (DynamicMigration.identity >>> m)(input)
        val r3 = (m >>> DynamicMigration.identity)(input)
        assertTrue(r1 == r2 && r2 == r3)
      }
    ),

    suite("DynamicMigration - Reverse")(
      test("RenameField is reversible") {
        val migration = DynamicMigration.renameField("old", "new")
        val reversed = migration.reverse
        assertTrue(reversed.isDefined)
        val input = record("old" -> int(1))
        val migrated = migration(input)
        val roundtrip = migrated.flatMap(v => reversed.get(v))
        assertTrue(roundtrip == Right(input))
      },
      test("AddField with DropField forms reversible pair") {
        val migration = DynamicMigration.dropField("temp", int(0))
        val reversed = migration.reverse
        assertTrue(reversed.isDefined)
        // The reverse of drop (with default) is add
        reversed.get.actions.head match {
          case MigrationAction.AddField(_, "temp", _) => assertTrue(true)
          case _ => assertTrue(false)
        }
      },
      test("TransformValue with reverse is reversible") {
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformValue(DynamicOptic.root.field("n"), "double", Some("halve"))
        ))
        val reversed = migration.reverse
        assertTrue(reversed.isDefined)
        val input = record("n" -> int(10))
        val migrated = migration(input)
        val roundtrip = migrated.flatMap(v => reversed.get(v))
        assertTrue(roundtrip == Right(input))
      },
      test("TransformValue without reverse is not reversible") {
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformValue(DynamicOptic.root.field("n"), "double", None)
        ))
        val reversed = migration.reverse
        assertTrue(reversed.isEmpty)
      },
      test("Optionalize is not reversible") {
        val migration = DynamicMigration.optionalize("name")
        val reversed = migration.reverse
        assertTrue(reversed.isEmpty)
      },
      test("FilterElements is not reversible") {
        val migration = DynamicMigration(Vector(
          MigrationAction.FilterElements(DynamicOptic.root.field("items"), "isPositive")
        ))
        val reversed = migration.reverse
        assertTrue(reversed.isEmpty)
      },
      test("reversed migration actions are in reverse order") {
        val m1 = DynamicMigration.renameField("a", "b")
        val m2 = DynamicMigration.renameField("c", "d")
        val combined = m1 >>> m2
        val reversed = combined.reverse
        assertTrue(reversed.isDefined)
        reversed.get.actions match {
          case Vector(
            MigrationAction.RenameField(_, "d", "c"),
            MigrationAction.RenameField(_, "b", "a")
          ) => assertTrue(true)
          case _ => assertTrue(false)
        }
      }
    ),

    suite("TransformRegistry")(
      test("empty registry returns None for all lookups") {
        val empty = TransformRegistry.empty
        assertTrue(
          empty.getTransform("any").isEmpty,
          empty.getPredicate("any").isEmpty
        )
      },
      test("registry returns registered transforms") {
        val reg = TransformRegistry(
          transforms = scala.collection.immutable.Map(
            "id" -> ((dv: DynamicValue) => Right(dv))
          ),
          predicates = scala.collection.immutable.Map(
            "always" -> ((_: DynamicValue) => true)
          )
        )
        assertTrue(
          reg.getTransform("id").isDefined,
          reg.getPredicate("always").isDefined,
          reg.getTransform("unknown").isEmpty
        )
      }
    ),

    suite("Migration.Builder")(
      test("builds migration with multiple actions") {
        // Using DynamicValue schemas for simplicity
        implicit val dvSchema: Schema[DynamicValue] = Schema.dynamic

        val migration = Migration.builder[DynamicValue, DynamicValue]
          .addField("b", int(2))
          .renameField("a", "aa")
          .build

        val input = record("a" -> int(1))
        val result = migration(input)
        assertTrue(result == Right(record("aa" -> int(1), "b" -> int(2))))
      },
      test("builder supports all action types") {
        implicit val dvSchema: Schema[DynamicValue] = Schema.dynamic

        // Just verify it compiles and builds
        val builder = Migration.builder[DynamicValue, DynamicValue]
          .addField("f", int(1))
          .dropField("old")
          .renameField("x", "y")
          .optionalize("opt")
          .mandate("req", str("default"))
          .transformValue(DynamicOptic.root.field("v"), "uppercase")
          .changeType(DynamicOptic.root.field("t"), "Int", "String", "intToString")
          .renameCase("Old", "New")
          .transformCase(DynamicOptic.root, "Case", "identity")
          .addCase("NewCase")
          .dropCase("OldCase", "NewCase")
          .transformElements(DynamicOptic.root.field("seq"), "identity")
          .transformKeys(DynamicOptic.root.field("map"), "identity")
          .transformValues(DynamicOptic.root.field("map"), "identity")
          .filterElements(DynamicOptic.root.field("seq"), "isPositive")
          .filterEntries(DynamicOptic.root.field("map"), "keyIsName")

        val migration = builder.build
        assertTrue(migration.dynamicMigration.actions.length == 16)
      }
    ),

    suite("Migration typed wrapper")(
      test("identity migration returns same value") {
        implicit val dvSchema: Schema[DynamicValue] = Schema.dynamic
        val migration = Migration.identity[DynamicValue]
        val input = record("a" -> int(1))
        val result = migration(input)
        assertTrue(result == Right(input))
      },
      test("fromDynamic creates migration from DynamicMigration") {
        implicit val dvSchema: Schema[DynamicValue] = Schema.dynamic
        val dynamic = DynamicMigration.addField("b", int(2))
        val typed = Migration.fromDynamic[DynamicValue, DynamicValue](dynamic)
        val input = record("a" -> int(1))
        val result = typed(input)
        assertTrue(result == Right(record("a" -> int(1), "b" -> int(2))))
      },
      test("typed migrations compose with andThen") {
        implicit val dvSchema: Schema[DynamicValue] = Schema.dynamic
        val m1 = Migration.fromDynamic[DynamicValue, DynamicValue](DynamicMigration.addField("b", int(2)))
        val m2 = Migration.fromDynamic[DynamicValue, DynamicValue](DynamicMigration.addField("c", int(3)))
        val combined = m1.andThen(m2)
        val input = record("a" -> int(1))
        val result = combined(input)
        assertTrue(result == Right(record("a" -> int(1), "b" -> int(2), "c" -> int(3))))
      },
      test("typed migrations compose with >>>") {
        implicit val dvSchema: Schema[DynamicValue] = Schema.dynamic
        val m1 = Migration.fromDynamic[DynamicValue, DynamicValue](DynamicMigration.addField("b", int(2)))
        val m2 = Migration.fromDynamic[DynamicValue, DynamicValue](DynamicMigration.addField("c", int(3)))
        val combined = m1 >>> m2
        val input = record("a" -> int(1))
        val result = combined(input)
        assertTrue(result == Right(record("a" -> int(1), "b" -> int(2), "c" -> int(3))))
      },
      test("typed migration reverse works") {
        implicit val dvSchema: Schema[DynamicValue] = Schema.dynamic
        val m = Migration.fromDynamic[DynamicValue, DynamicValue](
          DynamicMigration.renameField("old", "new")
        )
        val reversed = m.reverse
        assertTrue(reversed.isDefined)
        val input = record("old" -> int(1))
        val migrated = m(input)
        val roundtrip = migrated.flatMap(v => reversed.get(v))
        assertTrue(roundtrip == Right(input))
      }
    ),

    suite("Error cases")(
      test("PathNotFound when field doesn't exist in path") {
        val path = DynamicOptic.root.field("nonexistent").field("child")
        val migration = DynamicMigration.addField(path, "x", int(1))
        val input = record("other" -> int(1))
        val result = migration(input)
        assertTrue(result.isLeft)
        result match {
          case Left(err) => assertTrue(err.message.contains("Path not found"))
          case _ => assertTrue(false)
        }
      },
      test("TypeMismatch when expected Record but got Primitive") {
        val migration = DynamicMigration.addField("x", int(1))
        val input = int(42)
        val result = migration(input)
        assertTrue(result.isLeft)
        result match {
          case Left(err) => assertTrue(err.message.contains("Type mismatch"))
          case _ => assertTrue(false)
        }
      },
      test("InvalidTransform when transform fails") {
        val path = DynamicOptic.root.field("value")
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformValue(path, "uppercase", None)
        ))
        val input = record("value" -> int(42))
        val result = migration(input)
        assertTrue(result.isLeft)
        result match {
          case Left(err) => assertTrue(err.message.contains("Invalid transform"))
          case _ => assertTrue(false)
        }
      }
    )
  )
}
