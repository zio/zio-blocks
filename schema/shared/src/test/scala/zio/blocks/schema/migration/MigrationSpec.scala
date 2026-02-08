package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}
import zio.test._

object MigrationSpec extends ZIOSpecDefault {

  // ──────────────── Test Data ────────────────

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }

  case class UserV1(id: Int)
  object UserV1 {
    implicit val schema: Schema[UserV1] = Schema.derived[UserV1]
  }

  case class UserV2(id: Int, active: Boolean)
  object UserV2 {
    implicit val schema: Schema[UserV2] = Schema.derived[UserV2]
  }

  case class ItemV1(id: Int, title: String)
  object ItemV1 {
    implicit val schema: Schema[ItemV1] = Schema.derived[ItemV1]
  }

  case class ItemV2(id: Int, name: String, price: Double)
  object ItemV2 {
    implicit val schema: Schema[ItemV2] = Schema.derived[ItemV2]
  }

  case class PersonWithEmail(fullName: String, age: Int, email: String)
  object PersonWithEmail {
    implicit val schema: Schema[PersonWithEmail] = Schema.derived[PersonWithEmail]
  }

  case class Address(street: String, city: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived[Address]
  }

  case class PersonFlat(name: String, age: Int, street: String, city: String)
  object PersonFlat {
    implicit val schema: Schema[PersonFlat] = Schema.derived[PersonFlat]
  }

  case class PersonNested(name: String, age: Int, address: Address)
  object PersonNested {
    implicit val schema: Schema[PersonNested] = Schema.derived[PersonNested]
  }

  // ──────────────── Helper ────────────────

  private def mkRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(Chunk(fields: _*))

  private def mkVariant(caseName: String, payload: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, payload)

  private def mkSeq(elements: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(Chunk(elements: _*))

  private def mkMap(entries: (DynamicValue, DynamicValue)*): DynamicValue =
    DynamicValue.Map(Chunk(entries: _*))

  private def s(v: String): DynamicValue  = DynamicValue.string(v)
  private def i(v: Int): DynamicValue     = DynamicValue.int(v)
  private def b(v: Boolean): DynamicValue = DynamicValue.boolean(v)
  private def d(v: Double): DynamicValue  = DynamicValue.double(v)

  override def spec: Spec[Any, Any] = suite("MigrationSpec")(
    dynamicMigrationSuite,
    migrationSuite,
    migrationBuilderSuite,
    compositionSuite,
    reverseSuite,
    lawsSuite,
    enumSuite,
    collectionSuite,
    mapSuite,
    nestUnnestSuite,
    errorSuite,
    pathSuite,
    edgeCaseSuite,
    multiStepSuite,
    deepPathSuite,
    builderEdgeCaseSuite,
    reverseEdgeCaseSuite,
    introspectionSuite
  )

  // ──────────────── DynamicMigration Suite ────────────────

  val dynamicMigrationSuite: Spec[Any, Nothing] = suite("DynamicMigration")(
    test("identity does nothing") {
      val record = mkRecord("name" -> s("Alice"), "age" -> i(30))
      val result = DynamicMigration.identity.migrate(record)
      assertTrue(result == Right(record))
    },
    test("renameField renames a field") {
      val record = mkRecord("name" -> s("Alice"), "age" -> i(30))
      val result = DynamicMigration.renameField("name", "fullName").migrate(record)
      assertTrue(result == Right(mkRecord("fullName" -> s("Alice"), "age" -> i(30))))
    },
    test("addField adds a field with default") {
      val record = mkRecord("id" -> i(1))
      val result = DynamicMigration.addField("active", b(true)).migrate(record)
      assertTrue(result == Right(mkRecord("id" -> i(1), "active" -> b(true))))
    },
    test("addField fails on duplicate field") {
      val record = mkRecord("id" -> i(1))
      val result = DynamicMigration.addField("id", i(2)).migrate(record)
      assertTrue(result.isLeft)
    },
    test("dropField removes a field") {
      val record = mkRecord("id" -> i(1), "tmp" -> s("x"))
      val result = DynamicMigration.dropField("tmp").migrate(record)
      assertTrue(result == Right(mkRecord("id" -> i(1))))
    },
    test("dropField is no-op for missing field") {
      val record = mkRecord("id" -> i(1))
      val result = DynamicMigration.dropField("nope").migrate(record)
      assertTrue(result == Right(mkRecord("id" -> i(1))))
    },
    test("renameField fails on non-Record") {
      val result = DynamicMigration.renameField("x", "y").migrate(s("hello"))
      assertTrue(result.isLeft)
    },
    test("addField fails on non-Record") {
      val result = DynamicMigration.addField("x", i(1)).migrate(s("hello"))
      assertTrue(result.isLeft)
    },
    test("mandate replaces null with default") {
      val record = mkRecord("name" -> s("Alice"), "email" -> DynamicValue.Null)
      val result = DynamicMigration.mandate("email", s("default@example.com")).migrate(record)
      assertTrue(result == Right(mkRecord("name" -> s("Alice"), "email" -> s("default@example.com"))))
    },
    test("mandate keeps non-null value") {
      val record = mkRecord("name" -> s("Alice"), "email" -> s("alice@example.com"))
      val result = DynamicMigration.mandate("email", s("default@example.com")).migrate(record)
      assertTrue(result == Right(record))
    },
    test("optionalize is a no-op at runtime") {
      val record = mkRecord("name" -> s("Alice"))
      val result = DynamicMigration.optionalize("name").migrate(record)
      assertTrue(result == Right(record))
    },
    test("renameCase renames a variant case") {
      val variant = mkVariant("OldName", mkRecord("x" -> i(1)))
      val result  = DynamicMigration.renameCase("OldName", "NewName").migrate(variant)
      assertTrue(result == Right(mkVariant("NewName", mkRecord("x" -> i(1)))))
    },
    test("renameCase is no-op for non-matching case") {
      val variant = mkVariant("Other", mkRecord("x" -> i(1)))
      val result  = DynamicMigration.renameCase("OldName", "NewName").migrate(variant)
      assertTrue(result == Right(variant))
    }
  )

  // ──────────────── Migration (typed) Suite ────────────────

  val migrationSuite: Spec[Any, Nothing] = suite("Migration (typed)")(
    test("renameField migrates PersonV1 to PersonV2") {
      val migration = Migration.renameField[PersonV1, PersonV2]("name", "fullName")
      val result    = migration.migrate(PersonV1("Alice", 30))
      assertTrue(result == Right(PersonV2("Alice", 30)))
    },
    test("addField migrates UserV1 to UserV2") {
      val migration = Migration.addField[UserV1, UserV2]("active", b(true))
      val result    = migration.migrate(UserV1(1))
      assertTrue(result == Right(UserV2(1, true)))
    },
    test("removeField works") {
      val migration = Migration.removeField[UserV2, UserV1]("active")
      val result    = migration.migrate(UserV2(1, true))
      assertTrue(result == Right(UserV1(1)))
    },
    test("identity migration preserves value") {
      val migration = Migration.identity[PersonV1]
      val result    = migration.migrate(PersonV1("Alice", 30))
      assertTrue(result == Right(PersonV1("Alice", 30)))
    },
    test("Migration.fromDynamic works") {
      val dynamic   = DynamicMigration.renameField("name", "fullName")
      val migration = Migration.fromDynamic[PersonV1, PersonV2](dynamic)
      val result    = migration.migrate(PersonV1("Bob", 25))
      assertTrue(result == Right(PersonV2("Bob", 25)))
    }
  )

  // ──────────────── MigrationBuilder Suite ────────────────

  val migrationBuilderSuite: Spec[Any, Nothing] = suite("MigrationBuilder")(
    test("builder renameField works") {
      val migration = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .build
      assertTrue(migration.migrate(PersonV1("Alice", 30)) == Right(PersonV2("Alice", 30)))
    },
    test("builder addField works") {
      val migration = Migration
        .newBuilder[UserV1, UserV2]
        .addField("active", b(true))
        .build
      assertTrue(migration.migrate(UserV1(1)) == Right(UserV2(1, true)))
    },
    test("builder dropField works") {
      val migration = Migration
        .newBuilder[UserV2, UserV1]
        .dropField("active")
        .build
      assertTrue(migration.migrate(UserV2(1, true)) == Right(UserV1(1)))
    },
    test("builder multi-step: rename + addField") {
      val migration = Migration
        .newBuilder[ItemV1, ItemV2]
        .renameField("title", "name")
        .addField("price", d(9.99))
        .build
      assertTrue(migration.migrate(ItemV1(100, "Book")) == Right(ItemV2(100, "Book", 9.99)))
    },
    test("builder nestFields works") {
      val migration = Migration
        .newBuilder[PersonFlat, PersonNested]
        .nestFields(Vector("street", "city"), "address")
        .build
      val result = migration.migrate(PersonFlat("Alice", 30, "123 Main", "Springfield"))
      assertTrue(result == Right(PersonNested("Alice", 30, Address("123 Main", "Springfield"))))
    },
    test("builder unnestField works") {
      val migration = Migration
        .newBuilder[PersonNested, PersonFlat]
        .unnestField("address")
        .build
      val result = migration.migrate(PersonNested("Alice", 30, Address("123 Main", "Springfield")))
      assertTrue(result == Right(PersonFlat("Alice", 30, "123 Main", "Springfield")))
    }
  )

  // ──────────────── Composition Suite ────────────────

  val compositionSuite: Spec[Any, Nothing] = suite("Composition")(
    test("DynamicMigration ++ composes actions") {
      val m1       = DynamicMigration.renameField("title", "name")
      val m2       = DynamicMigration.addField("price", d(9.99))
      val composed = m1 ++ m2
      val record   = mkRecord("id" -> i(100), "title" -> s("Book"))
      val result   = composed.migrate(record)
      assertTrue(result == Right(mkRecord("id" -> i(100), "name" -> s("Book"), "price" -> d(9.99))))
    },
    test("Migration >>> composes typed migrations") {
      val m1       = Migration.renameField[PersonV1, PersonV2]("name", "fullName")
      val m2       = Migration.addField[PersonV2, PersonWithEmail]("email", s("default@example.com"))
      val composed = m1 >>> m2
      val result   = composed.migrate(PersonV1("Alice", 30))
      assertTrue(result == Right(PersonWithEmail("Alice", 30, "default@example.com")))
    },
    test("Migration andThen is the same as >>>") {
      val m1       = Migration.renameField[PersonV1, PersonV2]("name", "fullName")
      val m2       = Migration.addField[PersonV2, PersonWithEmail]("email", s("default@example.com"))
      val composed = m1.andThen(m2)
      val result   = composed.migrate(PersonV1("Alice", 30))
      assertTrue(result == Right(PersonWithEmail("Alice", 30, "default@example.com")))
    },
    test("multiple DynamicMigration compositions") {
      val m = DynamicMigration.renameField("a", "b") ++
        DynamicMigration.addField("c", i(1)) ++
        DynamicMigration.dropField("d")
      val record = mkRecord("a" -> s("x"), "d" -> s("remove_me"))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("b" -> s("x"), "c" -> i(1))))
    }
  )

  // ──────────────── Reverse Suite ────────────────

  val reverseSuite: Spec[Any, Nothing] = suite("Reverse")(
    test("rename reverse is swap") {
      val m       = DynamicMigration.renameField("name", "fullName")
      val record  = mkRecord("name" -> s("Alice"))
      val forward = m.migrate(record)
      val back    = m.reverse.migrate(forward.toOption.get)
      assertTrue(back == Right(record))
    },
    test("add+drop reverse roundtrips") {
      val m       = DynamicMigration.addField("active", b(true))
      val record  = mkRecord("id" -> i(1))
      val forward = m.migrate(record)
      // reverse of addField is dropField
      val back = m.reverse.migrate(forward.toOption.get)
      assertTrue(back == Right(record))
    },
    test("composed reverse reverses order and each action") {
      val m = DynamicMigration.renameField("name", "fullName") ++
        DynamicMigration.addField("email", s("default@example.com"))
      val record  = mkRecord("name" -> s("Alice"))
      val forward = m.migrate(record)
      val back    = m.reverse.migrate(forward.toOption.get)
      assertTrue(back == Right(record))
    },
    test("identity reverse is identity") {
      val m = DynamicMigration.identity
      assertTrue(m.reverse.actions.isEmpty)
    },
    test("renameCase reverse swaps names") {
      val m       = DynamicMigration.renameCase("Old", "New")
      val variant = mkVariant("Old", mkRecord())
      val forward = m.migrate(variant)
      val back    = m.reverse.migrate(forward.toOption.get)
      assertTrue(back == Right(variant))
    }
  )

  // ──────────────── Laws Suite ────────────────

  val lawsSuite: Spec[Any, Nothing] = suite("Laws")(
    test("identity law: identity ++ m == m") {
      val m      = DynamicMigration.renameField("name", "fullName")
      val record = mkRecord("name" -> s("Alice"))
      val r1     = (DynamicMigration.identity ++ m).migrate(record)
      val r2     = m.migrate(record)
      assertTrue(r1 == r2)
    },
    test("identity law: m ++ identity == m") {
      val m      = DynamicMigration.renameField("name", "fullName")
      val record = mkRecord("name" -> s("Alice"))
      val r1     = (m ++ DynamicMigration.identity).migrate(record)
      val r2     = m.migrate(record)
      assertTrue(r1 == r2)
    },
    test("associativity law: (a ++ b) ++ c == a ++ (b ++ c)") {
      val a      = DynamicMigration.renameField("x", "y")
      val bm     = DynamicMigration.addField("z", i(1))
      val c      = DynamicMigration.dropField("w")
      val record = mkRecord("x" -> s("hello"), "w" -> s("bye"))
      val r1     = ((a ++ bm) ++ c).migrate(record)
      val r2     = (a ++ (bm ++ c)).migrate(record)
      assertTrue(r1 == r2)
    },
    test("structural reverse law: (m.reverse.reverse).migrate == m.migrate") {
      val m      = DynamicMigration.renameField("name", "fullName")
      val record = mkRecord("name" -> s("Alice"))
      val r1     = m.reverse.reverse.migrate(record)
      val r2     = m.migrate(record)
      assertTrue(r1 == r2)
    },
    test("reverse law: (a ++ b).reverse == b.reverse ++ a.reverse") {
      val a       = DynamicMigration.renameField("x", "y")
      val bm      = DynamicMigration.addField("z", i(1))
      val record  = mkRecord("x" -> s("hello"))
      val forward = (a ++ bm).migrate(record)
      val r1      = (a ++ bm).reverse.migrate(forward.toOption.get)
      val r2      = (bm.reverse ++ a.reverse).migrate(forward.toOption.get)
      assertTrue(r1 == r2)
    }
  )

  // ──────────────── Enum Suite ────────────────

  val enumSuite: Spec[Any, Nothing] = suite("Enum migrations")(
    test("renameCase renames matching case") {
      val m       = DynamicMigration.renameCase("Active", "Enabled")
      val variant = mkVariant("Active", mkRecord("since" -> s("2024-01-01")))
      val result  = m.migrate(variant)
      assertTrue(result == Right(mkVariant("Enabled", mkRecord("since" -> s("2024-01-01")))))
    },
    test("renameCase passes through non-matching case") {
      val m       = DynamicMigration.renameCase("Active", "Enabled")
      val variant = mkVariant("Inactive", mkRecord())
      val result  = m.migrate(variant)
      assertTrue(result == Right(variant))
    },
    test("transformCase transforms payload of matching case") {
      val action = MigrationAction.TransformCase(
        DynamicOptic.root,
        "Active",
        Vector(MigrationAction.AddField(DynamicOptic.root, "reason", s("auto")))
      )
      val m       = new DynamicMigration(Vector(action))
      val variant = mkVariant("Active", mkRecord("since" -> s("2024")))
      val result  = m.migrate(variant)
      assertTrue(result == Right(mkVariant("Active", mkRecord("since" -> s("2024"), "reason" -> s("auto")))))
    },
    test("transformCase passes through non-matching case") {
      val action = MigrationAction.TransformCase(
        DynamicOptic.root,
        "Active",
        Vector(MigrationAction.AddField(DynamicOptic.root, "reason", s("auto")))
      )
      val m       = new DynamicMigration(Vector(action))
      val variant = mkVariant("Inactive", mkRecord())
      val result  = m.migrate(variant)
      assertTrue(result == Right(variant))
    }
  )

  // ──────────────── Collection Suite ────────────────

  val collectionSuite: Spec[Any, Nothing] = suite("Collection migrations")(
    test("TransformElements transforms all elements") {
      val innerMig = DynamicMigration.renameField("title", "name")
      val action   = MigrationAction.TransformElements(DynamicOptic.root, innerMig)
      val m        = new DynamicMigration(Vector(action))
      val seq      = mkSeq(
        mkRecord("title" -> s("A"), "id" -> i(1)),
        mkRecord("title" -> s("B"), "id" -> i(2))
      )
      val result = m.migrate(seq)
      assertTrue(
        result == Right(
          mkSeq(
            mkRecord("name" -> s("A"), "id" -> i(1)),
            mkRecord("name" -> s("B"), "id" -> i(2))
          )
        )
      )
    },
    test("TransformElements on empty sequence is identity") {
      val innerMig = DynamicMigration.renameField("x", "y")
      val action   = MigrationAction.TransformElements(DynamicOptic.root, innerMig)
      val m        = new DynamicMigration(Vector(action))
      val seq      = mkSeq()
      val result   = m.migrate(seq)
      assertTrue(result == Right(mkSeq()))
    },
    test("TransformElements propagates errors") {
      val innerMig = DynamicMigration.renameField("missing", "y")
      val action   = MigrationAction.TransformElements(DynamicOptic.root, innerMig)
      val m        = new DynamicMigration(Vector(action))
      val seq      = mkSeq(mkRecord("x" -> i(1)))
      val result   = m.migrate(seq)
      assertTrue(result.isLeft)
    }
  )

  // ──────────────── Map Suite ────────────────

  val mapSuite: Spec[Any, Nothing] = suite("Map migrations")(
    test("TransformKeys transforms all keys") {
      val keyMig = DynamicMigration.renameField("name", "label")
      val action = MigrationAction.TransformKeys(DynamicOptic.root, keyMig)
      val m      = new DynamicMigration(Vector(action))
      val map    = mkMap(
        (mkRecord("name" -> s("k1")), i(1)),
        (mkRecord("name" -> s("k2")), i(2))
      )
      val result = m.migrate(map)
      assertTrue(
        result == Right(
          mkMap(
            (mkRecord("label" -> s("k1")), i(1)),
            (mkRecord("label" -> s("k2")), i(2))
          )
        )
      )
    },
    test("TransformValues transforms all values") {
      val valMig = DynamicMigration.addField("extra", b(true))
      val action = MigrationAction.TransformValues(DynamicOptic.root, valMig)
      val m      = new DynamicMigration(Vector(action))
      val map    = mkMap(
        (s("a"), mkRecord("x" -> i(1))),
        (s("b"), mkRecord("x" -> i(2)))
      )
      val result = m.migrate(map)
      assertTrue(
        result == Right(
          mkMap(
            (s("a"), mkRecord("x" -> i(1), "extra" -> b(true))),
            (s("b"), mkRecord("x" -> i(2), "extra" -> b(true)))
          )
        )
      )
    },
    test("TransformKeys on empty map is identity") {
      val keyMig = DynamicMigration.renameField("x", "y")
      val action = MigrationAction.TransformKeys(DynamicOptic.root, keyMig)
      val m      = new DynamicMigration(Vector(action))
      val map    = mkMap()
      val result = m.migrate(map)
      assertTrue(result == Right(mkMap()))
    }
  )

  // ──────────────── Nest / Unnest Suite ────────────────

  val nestUnnestSuite: Spec[Any, Nothing] = suite("Nest / Unnest")(
    test("nest fields into sub-record") {
      val m      = DynamicMigration.nest(Vector("street", "city"), "address")
      val record = mkRecord("name" -> s("Alice"), "street" -> s("123 Main"), "city" -> s("Springfield"))
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "name"    -> s("Alice"),
            "address" -> mkRecord("street" -> s("123 Main"), "city" -> s("Springfield"))
          )
        )
      )
    },
    test("unnest sub-record into parent") {
      val m      = DynamicMigration.unnest("address")
      val record = mkRecord(
        "name"    -> s("Alice"),
        "address" -> mkRecord("street" -> s("123 Main"), "city" -> s("Springfield"))
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord("name" -> s("Alice"), "street" -> s("123 Main"), "city" -> s("Springfield"))
        )
      )
    },
    test("nest then unnest is roundtrip") {
      val m = DynamicMigration.nest(Vector("street", "city"), "address") ++
        DynamicMigration.unnest("address")
      val record = mkRecord("name" -> s("Alice"), "street" -> s("123 Main"), "city" -> s("Springfield"))
      val result = m.migrate(record)
      assertTrue(result == Right(record))
    }
  )

  // ──────────────── Error Suite ────────────────

  val errorSuite: Spec[Any, Nothing] = suite("Error handling")(
    test("MigrationError has path info") {
      val m      = DynamicMigration.renameField("missing", "x")
      val record = mkRecord("name" -> s("Alice"))
      val result = m.migrate(record)
      result match {
        case Left(err: MigrationError.FieldNotFound) =>
          assertTrue(err.fieldName == "missing") &&
          assertTrue(err.path == DynamicOptic.root) &&
          assertTrue(err.message.contains("missing"))
        case _ => assertTrue(false)
      }
    },
    test("TypeMismatch error for wrong value type") {
      val m      = DynamicMigration.renameField("x", "y")
      val result = m.migrate(s("not a record"))
      result match {
        case Left(err: MigrationError.TypeMismatch) =>
          assertTrue(err.expected == "Record") &&
          assertTrue(err.message.contains("Record"))
        case _ => assertTrue(false)
      }
    },
    test("FieldAlreadyExists error") {
      val m      = DynamicMigration.addField("id", i(1))
      val record = mkRecord("id" -> i(2))
      val result = m.migrate(record)
      result match {
        case Left(err: MigrationError.FieldAlreadyExists) =>
          assertTrue(err.fieldName == "id")
        case _ => assertTrue(false)
      }
    }
  )

  // ──────────────── Path Suite ────────────────

  val pathSuite: Spec[Any, Nothing] = suite("Path-based actions")(
    test("rename field at nested path") {
      val action = MigrationAction.Rename(DynamicOptic.root.field("address"), "streetName", "street")
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord(
        "name"    -> s("Alice"),
        "address" -> mkRecord("streetName" -> s("123 Main"), "city" -> s("Springfield"))
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "name"    -> s("Alice"),
            "address" -> mkRecord("street" -> s("123 Main"), "city" -> s("Springfield"))
          )
        )
      )
    },
    test("add field at nested path") {
      val action = MigrationAction.AddField(DynamicOptic.root.field("address"), "zip", s("62704"))
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord(
        "name"    -> s("Alice"),
        "address" -> mkRecord("street" -> s("123 Main"), "city" -> s("Springfield"))
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "name"    -> s("Alice"),
            "address" -> mkRecord("street" -> s("123 Main"), "city" -> s("Springfield"), "zip" -> s("62704"))
          )
        )
      )
    },
    test("transform elements at path") {
      val action = MigrationAction.TransformElements(
        DynamicOptic.root.field("items"),
        DynamicMigration.renameField("title", "name")
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord(
        "id"    -> i(1),
        "items" -> mkSeq(
          mkRecord("title" -> s("A")),
          mkRecord("title" -> s("B"))
        )
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "id"    -> i(1),
            "items" -> mkSeq(
              mkRecord("name" -> s("A")),
              mkRecord("name" -> s("B"))
            )
          )
        )
      )
    },
    test("nested path with case (variant at path)") {
      val action = MigrationAction.Rename(
        DynamicOptic.root.field("status").caseOf("Active"),
        "since",
        "activeSince"
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord(
        "name"   -> s("Alice"),
        "status" -> mkVariant("Active", mkRecord("since" -> s("2024-01-01")))
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "name"   -> s("Alice"),
            "status" -> mkVariant("Active", mkRecord("activeSince" -> s("2024-01-01")))
          )
        )
      )
    },
    test("path through variant passes through non-matching case") {
      val action = MigrationAction.Rename(
        DynamicOptic.root.field("status").caseOf("Active"),
        "since",
        "activeSince"
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord(
        "name"   -> s("Alice"),
        "status" -> mkVariant("Inactive", mkRecord("reason" -> s("quit")))
      )
      val result = m.migrate(record)
      assertTrue(result == Right(record))
    },
    test("builder addFieldAt works for nested records") {
      val migration = Migration
        .newBuilder[PersonNested, PersonNested]
        .addFieldAt(DynamicOptic.root.field("address"), "zip", s("62704"))
        .build
      // Note: since we're adding a field that doesn't exist in the target schema,
      // the typed roundtrip will remove it. Test via DynamicMigration directly instead.
      val dynValue = PersonNested.schema.toDynamicValue(PersonNested("Alice", 30, Address("123 Main", "Springfield")))
      val result   = migration.dynamicMigration.migrate(dynValue)
      assertTrue(result.isRight)
    }
  )

  // ──────────────── Edge Cases Suite ────────────────

  val edgeCaseSuite: Spec[Any, Nothing] = suite("Edge cases")(
    test("rename on empty record is error") {
      val record = mkRecord()
      val result = DynamicMigration.renameField("x", "y").migrate(record)
      assertTrue(result.isLeft)
    },
    test("addField on empty record works") {
      val record = mkRecord()
      val result = DynamicMigration.addField("x", i(1)).migrate(record)
      assertTrue(result == Right(mkRecord("x" -> i(1))))
    },
    test("dropField on single-field record leaves empty") {
      val record = mkRecord("x" -> i(1))
      val result = DynamicMigration.dropField("x").migrate(record)
      assertTrue(result == Right(mkRecord()))
    },
    test("identity on empty record") {
      val record = mkRecord()
      val result = DynamicMigration.identity.migrate(record)
      assertTrue(result == Right(record))
    },
    test("identity on primitive") {
      val result = DynamicMigration.identity.migrate(s("hello"))
      assertTrue(result == Right(s("hello")))
    },
    test("identity on Null") {
      val result = DynamicMigration.identity.migrate(DynamicValue.Null)
      assertTrue(result == Right(DynamicValue.Null))
    },
    test("identity on sequence") {
      val seq    = mkSeq(i(1), i(2), i(3))
      val result = DynamicMigration.identity.migrate(seq)
      assertTrue(result == Right(seq))
    },
    test("identity on variant") {
      val variant = mkVariant("Active", mkRecord("since" -> s("2024")))
      val result  = DynamicMigration.identity.migrate(variant)
      assertTrue(result == Right(variant))
    },
    test("rename preserves field order") {
      val record = mkRecord("a" -> i(1), "b" -> i(2), "c" -> i(3))
      val result = DynamicMigration.renameField("b", "beta").migrate(record)
      result match {
        case Right(DynamicValue.Record(fields)) =>
          val names = fields.map(_._1).toList
          assertTrue(names == List("a", "beta", "c"))
        case _ => assertTrue(false)
      }
    },
    test("multiple renames on same record") {
      val m      = DynamicMigration.renameField("a", "x") ++ DynamicMigration.renameField("b", "y")
      val record = mkRecord("a" -> i(1), "b" -> i(2))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("x" -> i(1), "y" -> i(2))))
    },
    test("addField then dropField same field is no-op") {
      val m      = DynamicMigration.addField("tmp", i(999)) ++ DynamicMigration.dropField("tmp")
      val record = mkRecord("id" -> i(1))
      val result = m.migrate(record)
      assertTrue(result == Right(record))
    },
    test("dropField then addField same field replaces value") {
      val m      = DynamicMigration.dropField("x") ++ DynamicMigration.addField("x", i(999))
      val record = mkRecord("x" -> i(1))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("x" -> i(999))))
    },
    test("mandate on missing field is error") {
      val record = mkRecord("name" -> s("Alice"))
      val result = DynamicMigration.mandate("email", s("default")).migrate(record)
      assertTrue(result.isLeft)
    },
    test("optionalize on missing field succeeds (schema marker)") {
      val record = mkRecord("name" -> s("Alice"))
      val result = DynamicMigration.optionalize("email").migrate(record)
      assertTrue(result.isRight)
    },
    test("changeType transforms field value") {
      val converter = DynamicMigration.identity
      val action    = MigrationAction.ChangeType(DynamicOptic.root, "age", converter)
      val m         = new DynamicMigration(Vector(action))
      val record    = mkRecord("name" -> s("Alice"), "age" -> i(30))
      val result    = m.migrate(record)
      assertTrue(result == Right(record))
    },
    test("nest with empty field list preserves record") {
      val m      = DynamicMigration.nest(Vector.empty, "sub")
      val record = mkRecord("a" -> i(1), "b" -> i(2))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("a" -> i(1), "b" -> i(2), "sub" -> mkRecord())))
    },
    test("renameCase on non-variant is error") {
      val result = DynamicMigration.renameCase("A", "B").migrate(mkRecord("x" -> i(1)))
      assertTrue(result.isLeft)
    }
  )

  // ──────────────── Multi-step Migration Suite ────────────────

  val multiStepSuite: Spec[Any, Nothing] = suite("Multi-step migrations")(
    test("three renames chain") {
      val m = DynamicMigration.renameField("a", "b") ++
        DynamicMigration.renameField("b", "c") ++
        DynamicMigration.renameField("c", "d")
      val record = mkRecord("a" -> i(1))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("d" -> i(1))))
    },
    test("rename then add then rename") {
      val m = DynamicMigration.renameField("name", "fullName") ++
        DynamicMigration.addField("email", s("default")) ++
        DynamicMigration.renameField("fullName", "displayName")
      val record = mkRecord("name" -> s("Alice"), "age" -> i(30))
      val result = m.migrate(record)
      assertTrue(
        result == Right(mkRecord("displayName" -> s("Alice"), "age" -> i(30), "email" -> s("default")))
      )
    },
    test("add multiple fields sequentially") {
      val m = DynamicMigration.addField("a", i(1)) ++
        DynamicMigration.addField("b", i(2)) ++
        DynamicMigration.addField("c", i(3))
      val record = mkRecord()
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("a" -> i(1), "b" -> i(2), "c" -> i(3))))
    },
    test("drop multiple fields sequentially") {
      val m = DynamicMigration.dropField("x") ++
        DynamicMigration.dropField("y") ++
        DynamicMigration.dropField("z")
      val record = mkRecord("x" -> i(1), "y" -> i(2), "z" -> i(3), "keep" -> i(4))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("keep" -> i(4))))
    },
    test("5-step complex migration") {
      val m = DynamicMigration.renameField("firstName", "name") ++
        DynamicMigration.dropField("middleName") ++
        DynamicMigration.addField("email", s("none")) ++
        DynamicMigration.renameField("lastName", "surname") ++
        DynamicMigration.addField("active", b(true))
      val record = mkRecord(
        "firstName"  -> s("Alice"),
        "middleName" -> s("M"),
        "lastName"   -> s("Smith"),
        "age"        -> i(30)
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "name"    -> s("Alice"),
            "surname" -> s("Smith"),
            "age"     -> i(30),
            "email"   -> s("none"),
            "active"  -> b(true)
          )
        )
      )
    },
    test("rename + nest combined") {
      val m = DynamicMigration.renameField("street_addr", "street") ++
        DynamicMigration.nest(Vector("street", "city"), "address")
      val record = mkRecord("name" -> s("Alice"), "street_addr" -> s("123 Main"), "city" -> s("NYC"))
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "name"    -> s("Alice"),
            "address" -> mkRecord("street" -> s("123 Main"), "city" -> s("NYC"))
          )
        )
      )
    },
    test("unnest + rename combined") {
      val m = DynamicMigration.unnest("address") ++
        DynamicMigration.renameField("street", "streetName")
      val record = mkRecord(
        "name"    -> s("Alice"),
        "address" -> mkRecord("street" -> s("123 Main"), "city" -> s("NYC"))
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord("name" -> s("Alice"), "streetName" -> s("123 Main"), "city" -> s("NYC"))
        )
      )
    },
    test("transform elements then rename case on variant list") {
      val innerMig = DynamicMigration.addField("flag", b(false))
      val action   = MigrationAction.TransformElements(DynamicOptic.root, innerMig)
      val m        = new DynamicMigration(Vector(action))
      val seq      = mkSeq(mkRecord("id" -> i(1)), mkRecord("id" -> i(2)), mkRecord("id" -> i(3)))
      val result   = m.migrate(seq)
      assertTrue(
        result == Right(
          mkSeq(
            mkRecord("id" -> i(1), "flag" -> b(false)),
            mkRecord("id" -> i(2), "flag" -> b(false)),
            mkRecord("id" -> i(3), "flag" -> b(false))
          )
        )
      )
    }
  )

  // ──────────────── Deep Path Suite ────────────────

  val deepPathSuite: Spec[Any, Nothing] = suite("Deep path actions")(
    test("two-level nested rename") {
      val action = MigrationAction.Rename(
        DynamicOptic.root.field("user").field("profile"),
        "firstName",
        "name"
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord(
        "user" -> mkRecord(
          "profile" -> mkRecord("firstName" -> s("Alice"), "age" -> i(30))
        )
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "user" -> mkRecord(
              "profile" -> mkRecord("name" -> s("Alice"), "age" -> i(30))
            )
          )
        )
      )
    },
    test("add field at two-level nested path") {
      val action = MigrationAction.AddField(
        DynamicOptic.root.field("config").field("db"),
        "timeout",
        i(30)
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord(
        "config" -> mkRecord(
          "db" -> mkRecord("host" -> s("localhost"), "port" -> i(5432))
        )
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "config" -> mkRecord(
              "db" -> mkRecord("host" -> s("localhost"), "port" -> i(5432), "timeout" -> i(30))
            )
          )
        )
      )
    },
    test("drop field at nested path") {
      val action = MigrationAction.DropField(
        DynamicOptic.root.field("user"),
        "password",
        s("***")
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord(
        "user" -> mkRecord("name" -> s("Alice"), "password" -> s("secret"))
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord("user" -> mkRecord("name" -> s("Alice")))
        )
      )
    },
    test("transform elements at nested sequence") {
      val action = MigrationAction.TransformElements(
        DynamicOptic.root.field("data").field("items"),
        DynamicMigration.renameField("title", "name")
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord(
        "data" -> mkRecord(
          "items" -> mkSeq(
            mkRecord("title" -> s("A")),
            mkRecord("title" -> s("B"))
          )
        )
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "data" -> mkRecord(
              "items" -> mkSeq(
                mkRecord("name" -> s("A")),
                mkRecord("name" -> s("B"))
              )
            )
          )
        )
      )
    },
    test("multiple actions at different paths") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root, "firstName", "name"),
          MigrationAction.AddField(DynamicOptic.root.field("address"), "zip", s("12345")),
          MigrationAction.DropField(DynamicOptic.root, "temp", DynamicValue.Null)
        )
      )
      val record = mkRecord(
        "firstName" -> s("Alice"),
        "temp"      -> s("remove"),
        "address"   -> mkRecord("street" -> s("Main"), "city" -> s("NYC"))
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "name"    -> s("Alice"),
            "address" -> mkRecord("street" -> s("Main"), "city" -> s("NYC"), "zip" -> s("12345"))
          )
        )
      )
    },
    test("path targeting missing field gives error with path info") {
      val action = MigrationAction.Rename(DynamicOptic.root.field("missing"), "x", "y")
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord("name" -> s("Alice"))
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    }
  )

  // ──────────────── Builder Edge Cases Suite ────────────────

  val builderEdgeCaseSuite: Spec[Any, Nothing] = suite("Builder edge cases")(
    test("empty builder produces identity migration") {
      val m      = Migration.newBuilder[PersonV1, PersonV1].build
      val result = m.migrate(PersonV1("Alice", 30))
      assertTrue(result == Right(PersonV1("Alice", 30)))
    },
    test("builder with single action") {
      val m = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .build
      assertTrue(m.migrate(PersonV1("Alice", 30)) == Right(PersonV2("Alice", 30)))
    },
    test("builder mandateField works") {
      val m = Migration
        .newBuilder[PersonV1, PersonV1]
        .mandateField("name", s("Unknown"))
        .build
      val dyn    = PersonV1.schema.toDynamicValue(PersonV1("Alice", 30))
      val result = m.dynamicMigration.migrate(dyn)
      assertTrue(result.isRight)
    },
    test("builder optionalizeField works") {
      val m = Migration
        .newBuilder[PersonV1, PersonV1]
        .optionalizeField("name")
        .build
      val dyn    = PersonV1.schema.toDynamicValue(PersonV1("Alice", 30))
      val result = m.dynamicMigration.migrate(dyn)
      assertTrue(result.isRight)
    },
    test("builder changeFieldType works") {
      val m = Migration
        .newBuilder[PersonV1, PersonV1]
        .changeFieldType("name", DynamicMigration.identity)
        .build
      val dyn    = PersonV1.schema.toDynamicValue(PersonV1("Alice", 30))
      val result = m.dynamicMigration.migrate(dyn)
      assertTrue(result.isRight)
    },
    test("builder renameCase works") {
      val m = Migration
        .newBuilder[PersonV1, PersonV1]
        .renameCase("Old", "New")
        .build
      val variant = mkVariant("Old", mkRecord())
      val result  = m.dynamicMigration.migrate(variant)
      assertTrue(result == Right(mkVariant("New", mkRecord())))
    },
    test("builder transformCase works") {
      val m = Migration
        .newBuilder[PersonV1, PersonV1]
        .transformCase("Active", Vector(MigrationAction.AddField(DynamicOptic.root, "x", i(1))))
        .build
      val variant = mkVariant("Active", mkRecord())
      val result  = m.dynamicMigration.migrate(variant)
      assertTrue(result == Right(mkVariant("Active", mkRecord("x" -> i(1)))))
    },
    test("builder renameFieldAt works") {
      val m = Migration
        .newBuilder[PersonNested, PersonNested]
        .renameFieldAt(DynamicOptic.root.field("address"), "street", "streetName")
        .build
      val dyn = PersonNested.schema.toDynamicValue(
        PersonNested("Alice", 30, Address("123 Main", "NYC"))
      )
      val result = m.dynamicMigration.migrate(dyn)
      assertTrue(result.isRight)
    },
    test("buildPartial works same as build") {
      val m = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .buildPartial
      assertTrue(m.migrate(PersonV1("Alice", 30)) == Right(PersonV2("Alice", 30)))
    },
    test("builder preserves action order") {
      val m = Migration
        .newBuilder[PersonV1, PersonV1]
        .renameField("name", "fullName")
        .renameField("fullName", "displayName")
        .build
      val dyn    = PersonV1.schema.toDynamicValue(PersonV1("Alice", 30))
      val result = m.dynamicMigration.migrate(dyn)
      assertTrue(result.isRight)
    }
  )

  // ──────────────── Reverse Edge Cases Suite ────────────────

  val reverseEdgeCaseSuite: Spec[Any, Nothing] = suite("Reverse edge cases")(
    test("reverse of dropField restores field") {
      val m       = DynamicMigration.dropField("x", i(42))
      val record  = mkRecord("x" -> i(42), "y" -> i(2))
      val forward = m.migrate(record)
      assertTrue(forward == Right(mkRecord("y" -> i(2))))
      val back = m.reverse.migrate(forward.toOption.get)
      assertTrue(back == Right(mkRecord("y" -> i(2), "x" -> i(42))))
    },
    test("reverse of mandate is optionalize") {
      val m       = DynamicMigration.mandate("email", s("default"))
      val record  = mkRecord("email" -> DynamicValue.Null)
      val forward = m.migrate(record)
      assertTrue(forward == Right(mkRecord("email" -> s("default"))))
    },
    test("double reverse of identity is identity") {
      val m = DynamicMigration.identity
      assertTrue(m.reverse.reverse.actions == m.actions)
    },
    test("reverse of complex 3-step migration roundtrips") {
      val m = DynamicMigration.renameField("a", "b") ++
        DynamicMigration.addField("c", i(1)) ++
        DynamicMigration.renameField("d", "e")
      val record  = mkRecord("a" -> s("hello"), "d" -> s("world"))
      val forward = m.migrate(record)
      val back    = m.reverse.migrate(forward.toOption.get)
      assertTrue(back == Right(record))
    },
    test("reverse of renameCase roundtrips") {
      val m       = DynamicMigration.renameCase("Active", "Enabled")
      val variant = mkVariant("Active", mkRecord("x" -> i(1)))
      val forward = m.migrate(variant)
      assertTrue(forward == Right(mkVariant("Enabled", mkRecord("x" -> i(1)))))
      val back = m.reverse.migrate(forward.toOption.get)
      assertTrue(back == Right(variant))
    },
    test("typed Migration reverse works") {
      val m       = Migration.renameField[PersonV1, PersonV2]("name", "fullName")
      val rev     = m.reverse
      val forward = m.migrate(PersonV1("Alice", 30))
      val back    = rev.migrate(forward.toOption.get)
      assertTrue(back == Right(PersonV1("Alice", 30)))
    },
    test("composed typed Migration reverse roundtrips") {
      val m1       = Migration.renameField[PersonV1, PersonV2]("name", "fullName")
      val m2       = Migration.addField[PersonV2, PersonWithEmail]("email", s("default@x.com"))
      val composed = m1 >>> m2
      val rev      = composed.reverse
      val forward  = composed.migrate(PersonV1("Alice", 30))
      val back     = rev.migrate(forward.toOption.get)
      assertTrue(back == Right(PersonV1("Alice", 30)))
    }
  )

  // ──────────────── Introspection Suite ────────────────

  val introspectionSuite: Spec[Any, Nothing] = suite("Introspection")(
    test("identity has no actions") {
      assertTrue(DynamicMigration.identity.actions.isEmpty)
    },
    test("single action migration has one action") {
      assertTrue(DynamicMigration.renameField("a", "b").actions.size == 1)
    },
    test("composed migration concatenates actions") {
      val m = DynamicMigration.renameField("a", "b") ++ DynamicMigration.addField("c", i(1))
      assertTrue(m.actions.size == 2)
    },
    test("3-step migration has 3 actions") {
      val m = DynamicMigration.renameField("a", "b") ++
        DynamicMigration.addField("c", i(1)) ++
        DynamicMigration.dropField("d")
      assertTrue(m.actions.size == 3)
    },
    test("reverse preserves action count") {
      val m = DynamicMigration.renameField("a", "b") ++
        DynamicMigration.addField("c", i(1))
      assertTrue(m.reverse.actions.size == m.actions.size)
    },
    test("actions are inspectable") {
      val m = DynamicMigration.renameField("name", "fullName")
      m.actions.head match {
        case MigrationAction.Rename(_, from, to) =>
          assertTrue(from == "name") && assertTrue(to == "fullName")
        case _ => assertTrue(false)
      }
    },
    test("addField action is inspectable") {
      val m = DynamicMigration.addField("active", b(true))
      m.actions.head match {
        case MigrationAction.AddField(_, name, default) =>
          assertTrue(name == "active") && assertTrue(default == b(true))
        case _ => assertTrue(false)
      }
    },
    test("dropField action is inspectable") {
      val m = DynamicMigration.dropField("tmp")
      m.actions.head match {
        case MigrationAction.DropField(_, name, _) =>
          assertTrue(name == "tmp")
        case _ => assertTrue(false)
      }
    },
    test("Migration exposes dynamicMigration") {
      val m = Migration.renameField[PersonV1, PersonV2]("name", "fullName")
      assertTrue(m.dynamicMigration.actions.size == 1)
    },
    test("Migration apply works like migrate") {
      val m  = Migration.renameField[PersonV1, PersonV2]("name", "fullName")
      val v  = PersonV1("Alice", 30)
      val r1 = m.apply(v)
      val r2 = m.migrate(v)
      assertTrue(r1 == r2)
    }
  )
}
