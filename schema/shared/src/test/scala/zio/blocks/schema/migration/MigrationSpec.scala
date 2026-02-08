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
    pathSuite
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
}
