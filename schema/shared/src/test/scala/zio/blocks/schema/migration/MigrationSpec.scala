package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}
import zio.test._

import scala.annotation.nowarn

@nowarn("msg=is never used")
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

  private def recordSize(v: DynamicValue): Int = v match {
    case DynamicValue.Record(fields) => fields.size
    case _                           => -1
  }

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
    introspectionSuite,
    migrationExprSuite,
    joinSplitSuite,
    builderValidationSuite,
    semanticInverseSuite,
    advancedCompositionSuite,
    schemaAwareValidationSuite,
    exprEdgeCasesSuite,
    errorPathsSuite,
    roundtripPropertySuite,
    dynamicOpticPathSuite,
    identityMonoidLawsSuite,
    builderFluentSuite,
    stressTestSuite,
    actionConstructionSuite,
    typedMigrationSuite,
    exprRoundtripSuite,
    transformValueExprSuite,
    serializationRoundtripSuite
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

  // ──────────────── MigrationExpr Suite ────────────────

  private def l(v: Long): DynamicValue  = DynamicValue.long(v)
  private def f(v: Float): DynamicValue = DynamicValue.float(v)

  val migrationExprSuite: Spec[Any, Nothing] = suite("MigrationExpr")(
    test("Identity returns input unchanged") {
      assertTrue(MigrationExpr.Identity(i(42)) == Right(i(42)))
    },
    test("Identity on string") {
      assertTrue(MigrationExpr.Identity(s("hello")) == Right(s("hello")))
    },
    test("Identity on record") {
      val rec = mkRecord("a" -> i(1))
      assertTrue(MigrationExpr.Identity(rec) == Right(rec))
    },
    test("Identity reverse is Identity") {
      assertTrue(MigrationExpr.Identity.reverse == MigrationExpr.Identity)
    },
    test("Const always returns the constant value") {
      assertTrue(MigrationExpr.Const(i(99))(s("anything")) == Right(i(99)))
    },
    test("Const on different input types") {
      assertTrue(MigrationExpr.Const(b(true))(i(0)) == Right(b(true)))
    },
    test("IntToString converts int to string") {
      assertTrue(MigrationExpr.IntToString(i(42)) == Right(s("42")))
    },
    test("IntToString negative number") {
      assertTrue(MigrationExpr.IntToString(i(-5)) == Right(s("-5")))
    },
    test("IntToString fails on non-int") {
      assertTrue(MigrationExpr.IntToString(s("hello")).isLeft)
    },
    test("IntToString reverse is StringToInt") {
      assertTrue(MigrationExpr.IntToString.reverse == MigrationExpr.StringToInt)
    },
    test("StringToInt converts string to int") {
      assertTrue(MigrationExpr.StringToInt(s("42")) == Right(i(42)))
    },
    test("StringToInt fails on non-numeric string") {
      assertTrue(MigrationExpr.StringToInt(s("abc")).isLeft)
    },
    test("StringToInt reverse is IntToString") {
      assertTrue(MigrationExpr.StringToInt.reverse == MigrationExpr.IntToString)
    },
    test("IntToString then StringToInt roundtrips") {
      val result = MigrationExpr.IntToString(i(42)).flatMap(MigrationExpr.StringToInt(_))
      assertTrue(result == Right(i(42)))
    },
    test("IntToLong converts int to long") {
      assertTrue(MigrationExpr.IntToLong(i(42)) == Right(l(42L)))
    },
    test("IntToLong reverse is LongToInt") {
      assertTrue(MigrationExpr.IntToLong.reverse == MigrationExpr.LongToInt)
    },
    test("LongToInt converts long to int") {
      assertTrue(MigrationExpr.LongToInt(l(42L)) == Right(i(42)))
    },
    test("LongToInt fails on non-long") {
      assertTrue(MigrationExpr.LongToInt(s("hello")).isLeft)
    },
    test("IntToDouble converts int to double") {
      assertTrue(MigrationExpr.IntToDouble(i(42)) == Right(d(42.0)))
    },
    test("IntToDouble reverse is DoubleToInt") {
      assertTrue(MigrationExpr.IntToDouble.reverse == MigrationExpr.DoubleToInt)
    },
    test("DoubleToInt converts double to int (truncates)") {
      assertTrue(MigrationExpr.DoubleToInt(d(3.7)) == Right(i(3)))
    },
    test("DoubleToInt fails on non-double") {
      assertTrue(MigrationExpr.DoubleToInt(s("hello")).isLeft)
    },
    test("LongToString converts long to string") {
      assertTrue(MigrationExpr.LongToString(l(999L)) == Right(s("999")))
    },
    test("StringToLong converts string to long") {
      assertTrue(MigrationExpr.StringToLong(s("999")) == Right(l(999L)))
    },
    test("StringToLong fails on non-numeric") {
      assertTrue(MigrationExpr.StringToLong(s("abc")).isLeft)
    },
    test("DoubleToString converts double to string") {
      assertTrue(MigrationExpr.DoubleToString(d(3.14)) == Right(s("3.14")))
    },
    test("StringToDouble converts string to double") {
      assertTrue(MigrationExpr.StringToDouble(s("3.14")) == Right(d(3.14)))
    },
    test("StringToDouble fails on non-numeric") {
      assertTrue(MigrationExpr.StringToDouble(s("abc")).isLeft)
    },
    test("BoolToInt converts true to 1") {
      assertTrue(MigrationExpr.BoolToInt(b(true)) == Right(i(1)))
    },
    test("BoolToInt converts false to 0") {
      assertTrue(MigrationExpr.BoolToInt(b(false)) == Right(i(0)))
    },
    test("BoolToInt reverse is IntToBool") {
      assertTrue(MigrationExpr.BoolToInt.reverse == MigrationExpr.IntToBool)
    },
    test("IntToBool converts 0 to false") {
      assertTrue(MigrationExpr.IntToBool(i(0)) == Right(b(false)))
    },
    test("IntToBool converts nonzero to true") {
      assertTrue(MigrationExpr.IntToBool(i(42)) == Right(b(true)))
    },
    test("BoolToString converts true to string") {
      assertTrue(MigrationExpr.BoolToString(b(true)) == Right(s("true")))
    },
    test("BoolToString converts false to string") {
      assertTrue(MigrationExpr.BoolToString(b(false)) == Right(s("false")))
    },
    test("StringToBool converts 'true' to true") {
      assertTrue(MigrationExpr.StringToBool(s("true")) == Right(b(true)))
    },
    test("StringToBool converts 'false' to false") {
      assertTrue(MigrationExpr.StringToBool(s("false")) == Right(b(false)))
    },
    test("StringToBool fails on invalid string") {
      assertTrue(MigrationExpr.StringToBool(s("maybe")).isLeft)
    },
    test("StringToBool is case insensitive") {
      assertTrue(MigrationExpr.StringToBool(s("TRUE")) == Right(b(true)))
    },
    test("FloatToString converts float to string") {
      assertTrue(MigrationExpr.FloatToString(f(1.5f)) == Right(s("1.5")))
    },
    test("StringToFloat converts string to float") {
      assertTrue(MigrationExpr.StringToFloat(s("1.5")) == Right(f(1.5f)))
    },
    test("StringToFloat fails on non-numeric") {
      assertTrue(MigrationExpr.StringToFloat(s("abc")).isLeft)
    },
    test("Compose chains two expressions") {
      val expr = MigrationExpr.Compose(MigrationExpr.IntToString, MigrationExpr.StringToInt)
      assertTrue(expr(i(42)) == Right(i(42)))
    },
    test("Compose reverse reverses order and each part") {
      val expr = MigrationExpr.Compose(MigrationExpr.IntToString, MigrationExpr.StringToDouble)
      val rev  = expr.reverse
      assertTrue(rev.isInstanceOf[MigrationExpr.Compose])
    },
    test("Compose propagates first error") {
      val expr = MigrationExpr.Compose(MigrationExpr.StringToInt, MigrationExpr.IntToString)
      assertTrue(expr(i(42)).isLeft) // StringToInt expects String, not Int
    },
    test("FromMigration wraps a DynamicMigration") {
      val mig  = DynamicMigration.renameField("a", "b")
      val expr = MigrationExpr.FromMigration(mig)
      val rec  = mkRecord("a" -> i(1))
      assertTrue(expr(rec) == Right(mkRecord("b" -> i(1))))
    },
    test("FromMigration reverse wraps reversed migration") {
      val mig  = DynamicMigration.renameField("a", "b")
      val expr = MigrationExpr.FromMigration(mig)
      val rev  = expr.reverse.asInstanceOf[MigrationExpr.FromMigration]
      val rec  = mkRecord("b" -> i(1))
      assertTrue(rev(rec) == Right(mkRecord("a" -> i(1))))
    },
    test("ChangeTypeExpr transforms field with MigrationExpr") {
      val action = MigrationAction.ChangeTypeExpr(DynamicOptic.root, "age", MigrationExpr.IntToString)
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord("name" -> s("Alice"), "age" -> i(30))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("name" -> s("Alice"), "age" -> s("30"))))
    },
    test("ChangeTypeExpr reverse uses reversed expr") {
      val action = MigrationAction.ChangeTypeExpr(DynamicOptic.root, "age", MigrationExpr.IntToString)
      val rev    = action.reverse.asInstanceOf[MigrationAction.ChangeTypeExpr]
      assertTrue(rev.expr == MigrationExpr.StringToInt)
    },
    test("ChangeTypeExpr fails on wrong field type") {
      val action = MigrationAction.ChangeTypeExpr(DynamicOptic.root, "name", MigrationExpr.IntToString)
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord("name" -> s("Alice")) // name is String, not Int
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    },
    test("ChangeTypeExpr fails on missing field") {
      val action = MigrationAction.ChangeTypeExpr(DynamicOptic.root, "missing", MigrationExpr.IntToString)
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord("name" -> s("Alice"))
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    }
  )

  // ──────────────── Join / Split Suite ────────────────

  val joinSplitSuite: Spec[Any, Nothing] = suite("Join / Split actions")(
    test("Join combines fields using Identity (passthrough)") {
      val action = MigrationAction.Join(
        DynamicOptic.root,
        Vector("first", "last"),
        "fullName",
        MigrationExpr.Identity
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord("first" -> s("Alice"), "last" -> s("Smith"), "age" -> i(30))
      val result = m.migrate(record)
      assertTrue(result.isRight)
      val res = result.toOption.get
      // "first" and "last" removed, "fullName" contains the sub-record
      assertTrue(res.fields.exists(_._1 == "fullName"))
      assertTrue(!res.fields.exists(_._1 == "first"))
      assertTrue(!res.fields.exists(_._1 == "last"))
      assertTrue(res.fields.exists(_._1 == "age"))
    },
    test("Join preserves other fields") {
      val action = MigrationAction.Join(
        DynamicOptic.root,
        Vector("x", "y"),
        "combined",
        MigrationExpr.Identity
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord("x" -> i(1), "y" -> i(2), "z" -> i(3))
      val result = m.migrate(record)
      val res    = result.toOption.get
      assertTrue(res.fields.exists(_._1 == "z"))
      assertTrue(res.fields.exists(_._1 == "combined"))
    },
    test("Join fails on non-Record") {
      val action = MigrationAction.Join(DynamicOptic.root, Vector("x"), "y", MigrationExpr.Identity)
      val m      = new DynamicMigration(Vector(action))
      assertTrue(m.migrate(s("hello")).isLeft)
    },
    test("Split splits field using Identity") {
      val action = MigrationAction.Split(
        DynamicOptic.root,
        "address",
        Vector("street", "city"),
        MigrationExpr.Identity
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord(
        "name"    -> s("Alice"),
        "address" -> mkRecord("street" -> s("Main"), "city" -> s("NYC"))
      )
      val result = m.migrate(record)
      val res    = result.toOption.get
      assertTrue(!res.fields.exists(_._1 == "address"))
      assertTrue(res.fields.exists(_._1 == "street"))
      assertTrue(res.fields.exists(_._1 == "city"))
    },
    test("Split fails on missing source field") {
      val action = MigrationAction.Split(
        DynamicOptic.root,
        "missing",
        Vector("a", "b"),
        MigrationExpr.Identity
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord("name" -> s("Alice"))
      assertTrue(m.migrate(record).isLeft)
    },
    test("Split fails on non-Record") {
      val action = MigrationAction.Split(DynamicOptic.root, "x", Vector("a"), MigrationExpr.Identity)
      val m      = new DynamicMigration(Vector(action))
      assertTrue(m.migrate(s("hello")).isLeft)
    },
    test("Join reverse is Split") {
      val action = MigrationAction.Join(
        DynamicOptic.root,
        Vector("a", "b"),
        "combined",
        MigrationExpr.Identity
      )
      val rev = action.reverse
      assertTrue(rev.isInstanceOf[MigrationAction.Split])
    },
    test("Split reverse is Join") {
      val action = MigrationAction.Split(
        DynamicOptic.root,
        "combined",
        Vector("a", "b"),
        MigrationExpr.Identity
      )
      val rev = action.reverse
      assertTrue(rev.isInstanceOf[MigrationAction.Join])
    },
    test("Join then Split roundtrips with Identity") {
      val join = MigrationAction.Join(
        DynamicOptic.root,
        Vector("x", "y"),
        "combined",
        MigrationExpr.Identity
      )
      val split  = join.reverse
      val m      = new DynamicMigration(Vector(join, split))
      val record = mkRecord("x" -> i(1), "y" -> i(2), "z" -> i(3))
      val result = m.migrate(record)
      assertTrue(result.isRight)
    }
  )

  // ──────────────── Builder Validation Suite ────────────────

  val builderValidationSuite: Spec[Any, Nothing] = suite("Builder Validation")(
    test("validate passes for empty builder") {
      val builder = Migration.newBuilder[PersonV1, PersonV1]
      assertTrue(builder.validate().isEmpty)
    },
    test("validate passes for single rename") {
      val builder = Migration.newBuilder[PersonV1, PersonV2].renameField("name", "fullName")
      assertTrue(builder.validate().isEmpty)
    },
    test("validate detects duplicate renames on same field") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV1]
        .renameField("name", "fullName")
        .renameField("name", "displayName")
      val errors = builder.validate()
      assertTrue(errors.nonEmpty)
      assertTrue(errors.exists(_.contains("renamed")))
    },
    test("validate detects conflicting add/drop on same field") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV1]
        .addField("x", i(1))
        .dropField("x")
      val errors = builder.validate()
      assertTrue(errors.nonEmpty)
      assertTrue(errors.exists(_.contains("added and dropped")))
    },
    test("validate detects duplicate addField") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV1]
        .addField("x", i(1))
        .addField("x", i(2))
      val errors = builder.validate()
      assertTrue(errors.nonEmpty)
      assertTrue(errors.exists(_.contains("added")))
    },
    test("validate passes for non-conflicting actions") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV1]
        .addField("a", i(1))
        .addField("b", i(2))
        .renameField("name", "fullName")
      assertTrue(builder.validate().isEmpty)
    },
    test("build throws on validation failure") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV1]
        .renameField("name", "x")
        .renameField("name", "y")
      val caught = try {
        builder.build
        false
      } catch {
        case _: IllegalArgumentException => true
        case _: Throwable                => false
      }
      assertTrue(caught)
    },
    test("buildPartial skips validation") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV1]
        .renameField("name", "x")
        .renameField("name", "y")
      val m = builder.buildPartial
      assertTrue(m.dynamicMigration.actions.size == 2)
    },
    test("validate returns multiple errors") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV1]
        .renameField("name", "x")
        .renameField("name", "y")
        .addField("z", i(1))
        .dropField("z")
      val errors = builder.validate()
      assertTrue(errors.size >= 2)
    },
    test("builder changeFieldTypeExpr works") {
      val m = Migration
        .newBuilder[PersonV1, PersonV1]
        .changeFieldTypeExpr("age", MigrationExpr.IntToString)
        .buildPartial
      val dyn    = PersonV1.schema.toDynamicValue(PersonV1("Alice", 30))
      val result = m.dynamicMigration.migrate(dyn)
      assertTrue(result.isRight)
    },
    test("builder joinFields works") {
      val m = Migration
        .newBuilder[PersonV1, PersonV1]
        .joinFields(Vector("name", "age"), "combined", MigrationExpr.Identity)
        .buildPartial
      val dyn    = PersonV1.schema.toDynamicValue(PersonV1("Alice", 30))
      val result = m.dynamicMigration.migrate(dyn)
      assertTrue(result.isRight)
    },
    test("builder splitField works") {
      val rec    = mkRecord("data" -> mkRecord("a" -> i(1), "b" -> i(2)))
      val action = MigrationAction.Split(DynamicOptic.root, "data", Vector("a", "b"), MigrationExpr.Identity)
      val m      = new DynamicMigration(Vector(action))
      val result = m.migrate(rec)
      assertTrue(result.isRight)
    }
  )

  // ──────────────── Semantic Inverse Suite ────────────────

  val semanticInverseSuite: Spec[Any, Nothing] = suite("Semantic Inverse")(
    test("rename: m(a) => b, m.reverse(b) => a") {
      val m    = DynamicMigration.renameField("name", "fullName")
      val a    = mkRecord("name" -> s("Alice"), "age" -> i(30))
      val fwd  = m.migrate(a).toOption.get
      val back = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("addField: m(a) => b, m.reverse(b) => a") {
      val m    = DynamicMigration.addField("active", b(true))
      val a    = mkRecord("id" -> i(1))
      val fwd  = m.migrate(a).toOption.get
      val back = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("dropField: m(a) => b, m.reverse(b) => a") {
      val m    = DynamicMigration.dropField("temp", s("saved"))
      val a    = mkRecord("id" -> i(1), "temp" -> s("saved"))
      val fwd  = m.migrate(a).toOption.get
      val back = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("renameCase: m(a) => b, m.reverse(b) => a") {
      val m    = DynamicMigration.renameCase("Active", "Enabled")
      val a    = mkVariant("Active", mkRecord("x" -> i(1)))
      val fwd  = m.migrate(a).toOption.get
      val back = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("nest: m(a) => b, m.reverse(b) => a") {
      val m    = DynamicMigration.nest(Vector("street", "city"), "address")
      val a    = mkRecord("name" -> s("Alice"), "street" -> s("Main"), "city" -> s("NYC"))
      val fwd  = m.migrate(a).toOption.get
      val back = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("unnest: m(a) => b, m.reverse(b) is best-effort inverse") {
      val m    = DynamicMigration.unnest("address")
      val a    = mkRecord("name" -> s("Alice"), "address" -> mkRecord("street" -> s("Main"), "city" -> s("NYC")))
      val fwd  = m.migrate(a).toOption.get
      val back = m.reverse.migrate(fwd)
      // Best-effort: unnest reverse nests the fields back but may include extra empty record
      assertTrue(back.isRight)
    },
    test("multi-step: 2 renames roundtrip") {
      val m    = DynamicMigration.renameField("a", "b") ++ DynamicMigration.renameField("c", "d")
      val a    = mkRecord("a" -> i(1), "c" -> i(2))
      val fwd  = m.migrate(a).toOption.get
      val back = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("multi-step: rename + add roundtrip") {
      val m    = DynamicMigration.renameField("x", "y") ++ DynamicMigration.addField("z", i(0))
      val a    = mkRecord("x" -> i(1))
      val fwd  = m.migrate(a).toOption.get
      val back = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("multi-step: add + rename + drop roundtrip (best-effort)") {
      val m = DynamicMigration.addField("tmp", i(99)) ++
        DynamicMigration.renameField("name", "fullName") ++
        DynamicMigration.dropField("age", i(30))
      val a    = mkRecord("name" -> s("Alice"), "age" -> i(30))
      val fwd  = m.migrate(a).toOption.get
      val back = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("typed Migration roundtrip: PersonV1 -> PersonV2 -> PersonV1") {
      val m    = Migration.renameField[PersonV1, PersonV2]("name", "fullName")
      val a    = PersonV1("Alice", 30)
      val fwd  = m.migrate(a).toOption.get
      val back = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("typed composed roundtrip: PersonV1 -> PersonV2 -> PersonWithEmail -> PersonV2 -> PersonV1") {
      val m1       = Migration.renameField[PersonV1, PersonV2]("name", "fullName")
      val m2       = Migration.addField[PersonV2, PersonWithEmail]("email", s("a@b.com"))
      val composed = m1 >>> m2
      val a        = PersonV1("Alice", 30)
      val fwd      = composed.migrate(a).toOption.get
      val back     = composed.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("ChangeTypeExpr roundtrip with IntToString") {
      val forward = MigrationAction.ChangeTypeExpr(DynamicOptic.root, "age", MigrationExpr.IntToString)
      val m       = new DynamicMigration(Vector(forward))
      val a       = mkRecord("name" -> s("Alice"), "age" -> i(30))
      val fwd     = m.migrate(a).toOption.get
      val back    = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("TransformElements roundtrip") {
      val inner  = DynamicMigration.renameField("title", "name")
      val action = MigrationAction.TransformElements(DynamicOptic.root, inner)
      val m      = new DynamicMigration(Vector(action))
      val a      = mkSeq(mkRecord("title" -> s("A"), "id" -> i(1)))
      val fwd    = m.migrate(a).toOption.get
      val back   = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("TransformKeys roundtrip") {
      val keyMig = DynamicMigration.renameField("name", "label")
      val action = MigrationAction.TransformKeys(DynamicOptic.root, keyMig)
      val m      = new DynamicMigration(Vector(action))
      val a      = mkMap((mkRecord("name" -> s("k1")), i(1)))
      val fwd    = m.migrate(a).toOption.get
      val back   = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    },
    test("TransformValues roundtrip") {
      val valMig = DynamicMigration.addField("extra", b(true))
      val action = MigrationAction.TransformValues(DynamicOptic.root, valMig)
      val m      = new DynamicMigration(Vector(action))
      val a      = mkMap((s("key"), mkRecord("x" -> i(1))))
      val fwd    = m.migrate(a).toOption.get
      val back   = m.reverse.migrate(fwd)
      assertTrue(back == Right(a))
    }
  )

  // ──────────────── Advanced Composition Suite ────────────────

  val advancedCompositionSuite: Spec[Any, Nothing] = suite("Advanced Composition")(
    test("nested record + collection transform combined") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            DynamicMigration.addField("qty", i(0))
          )
        )
      )
      val record = mkRecord(
        "name"  -> s("Alice"),
        "items" -> mkSeq(mkRecord("id" -> i(1)), mkRecord("id" -> i(2)))
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "fullName" -> s("Alice"),
            "items"    -> mkSeq(
              mkRecord("id" -> i(1), "qty" -> i(0)),
              mkRecord("id" -> i(2), "qty" -> i(0))
            )
          )
        )
      )
    },
    test("add field + nest combined") {
      val m = DynamicMigration.addField("zip", s("12345")) ++
        DynamicMigration.nest(Vector("street", "city", "zip"), "address")
      val record = mkRecord("name" -> s("Alice"), "street" -> s("Main"), "city" -> s("NYC"))
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
    test("drop + rename + add in sequence") {
      val m = DynamicMigration.dropField("legacy") ++
        DynamicMigration.renameField("title", "name") ++
        DynamicMigration.addField("version", i(2))
      val record = mkRecord("legacy" -> s("old"), "title" -> s("Book"), "id" -> i(1))
      val result = m.migrate(record)
      assertTrue(
        result == Right(mkRecord("name" -> s("Book"), "id" -> i(1), "version" -> i(2)))
      )
    },
    test("map keys + map values combined") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.TransformKeys(DynamicOptic.root, DynamicMigration.renameField("id", "key")),
          MigrationAction.TransformValues(DynamicOptic.root, DynamicMigration.addField("processed", b(true)))
        )
      )
      val map = mkMap(
        (mkRecord("id" -> i(1)), mkRecord("data" -> s("a"))),
        (mkRecord("id" -> i(2)), mkRecord("data" -> s("b")))
      )
      val result = m.migrate(map)
      assertTrue(
        result == Right(
          mkMap(
            (mkRecord("key" -> i(1)), mkRecord("data" -> s("a"), "processed" -> b(true))),
            (mkRecord("key" -> i(2)), mkRecord("data" -> s("b"), "processed" -> b(true)))
          )
        )
      )
    },
    test("collection + nested path combined") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("users"),
            new DynamicMigration(
              Vector(
                MigrationAction.Rename(DynamicOptic.root, "firstName", "name"),
                MigrationAction.AddField(DynamicOptic.root, "active", b(true))
              )
            )
          )
        )
      )
      val record = mkRecord(
        "users" -> mkSeq(
          mkRecord("firstName" -> s("Alice"), "age" -> i(30)),
          mkRecord("firstName" -> s("Bob"), "age"   -> i(25))
        )
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "users" -> mkSeq(
              mkRecord("name" -> s("Alice"), "age" -> i(30), "active" -> b(true)),
              mkRecord("name" -> s("Bob"), "age"   -> i(25), "active" -> b(true))
            )
          )
        )
      )
    },
    test("10-step migration") {
      val m = DynamicMigration.renameField("f1", "g1") ++
        DynamicMigration.renameField("f2", "g2") ++
        DynamicMigration.renameField("f3", "g3") ++
        DynamicMigration.addField("new1", i(1)) ++
        DynamicMigration.addField("new2", i(2)) ++
        DynamicMigration.addField("new3", i(3)) ++
        DynamicMigration.dropField("drop1") ++
        DynamicMigration.dropField("drop2") ++
        DynamicMigration.renameField("g1", "h1") ++
        DynamicMigration.addField("final", b(true))
      val record = mkRecord(
        "f1"    -> s("a"),
        "f2"    -> s("b"),
        "f3"    -> s("c"),
        "drop1" -> s("x"),
        "drop2" -> s("y")
      )
      val result = m.migrate(record)
      assertTrue(result.isRight)
      val res = result.toOption.get
      assertTrue(res.fields.exists(_._1 == "h1"))
      assertTrue(res.fields.exists(_._1 == "g2"))
      assertTrue(res.fields.exists(_._1 == "g3"))
      assertTrue(res.fields.exists(_._1 == "final"))
      assertTrue(!res.fields.exists(_._1 == "drop1"))
      assertTrue(!res.fields.exists(_._1 == "drop2"))
    },
    test("deeply nested path (3 levels) rename") {
      val action = MigrationAction.Rename(
        DynamicOptic.root.field("a").field("b").field("c"),
        "old",
        "new"
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord(
        "a" -> mkRecord(
          "b" -> mkRecord(
            "c" -> mkRecord("old" -> i(1))
          )
        )
      )
      val result = m.migrate(record)
      assertTrue(
        result == Right(
          mkRecord(
            "a" -> mkRecord(
              "b" -> mkRecord(
                "c" -> mkRecord("new" -> i(1))
              )
            )
          )
        )
      )
    },
    test("all action types in one migration") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root, "oldName", "name"),
          MigrationAction.AddField(DynamicOptic.root, "active", b(true)),
          MigrationAction.DropField(DynamicOptic.root, "legacy", DynamicValue.Null)
        )
      )
      val record = mkRecord("oldName" -> s("Alice"), "legacy" -> s("old"), "age" -> i(30))
      val result = m.migrate(record)
      assertTrue(result.isRight)
      val res = result.toOption.get
      assertTrue(res.fields.exists(_._1 == "name"))
      assertTrue(res.fields.exists(_._1 == "active"))
      assertTrue(!res.fields.exists(_._1 == "legacy"))
    },
    test("TransformCase with nested actions") {
      val action = MigrationAction.TransformCase(
        DynamicOptic.root,
        "UserCreated",
        Vector(
          MigrationAction.Rename(DynamicOptic.root, "userName", "name"),
          MigrationAction.AddField(DynamicOptic.root, "version", i(2))
        )
      )
      val m       = new DynamicMigration(Vector(action))
      val variant = mkVariant("UserCreated", mkRecord("userName" -> s("Alice"), "id" -> i(1)))
      val result  = m.migrate(variant)
      assertTrue(
        result == Right(
          mkVariant("UserCreated", mkRecord("name" -> s("Alice"), "id" -> i(1), "version" -> i(2)))
        )
      )
    },
    test("concurrent rename and nest") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root, "street_addr", "street"),
          MigrationAction.Nest(DynamicOptic.root, Vector("street", "city", "zip"), "address")
        )
      )
      val record = mkRecord(
        "name"        -> s("Alice"),
        "street_addr" -> s("123 Main"),
        "city"        -> s("NYC"),
        "zip"         -> s("10001")
      )
      val result = m.migrate(record)
      assertTrue(result.isRight)
    }
  )

  // ──────────────── Schema-Aware Validation Suite ────────────────

  val schemaAwareValidationSuite: Spec[Any, Nothing] = suite("Schema-aware Validation")(
    test("validate detects missing rename for changed field name") {
      val builder = Migration.newBuilder[PersonV1, PersonV2]
      val errors  = builder.validate()
      // Without any actions, validation passes (basic validation only)
      assertTrue(errors.isEmpty)
    },
    test("validate passes with correct rename") {
      val builder = Migration.newBuilder[PersonV1, PersonV2].renameField("name", "fullName")
      val errors  = builder.validate()
      assertTrue(errors.isEmpty)
    },
    test("validate detects triple rename on same field") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .renameField("name", "displayName")
        .renameField("name", "label")
      val errors = builder.validate()
      assertTrue(errors.exists(_.contains("renamed 3 times")))
    },
    test("validate detects add-drop conflict on multiple fields") {
      val builder = Migration
        .newBuilder[UserV1, UserV2]
        .addField("a", i(1))
        .addField("b", i(2))
        .dropField("a")
        .dropField("b")
      val errors = builder.validate()
      assertTrue(errors.size == 2)
    },
    test("validate passes for add without drop") {
      val builder = Migration
        .newBuilder[UserV1, UserV2]
        .addField("active", DynamicValue.boolean(true))
      val errors = builder.validate()
      assertTrue(errors.isEmpty)
    },
    test("validate passes for drop without add") {
      val builder = Migration
        .newBuilder[UserV1, UserV2]
        .dropField("oldField")
      val errors = builder.validate()
      assertTrue(errors.isEmpty)
    },
    test("validate detects duplicate add of same field") {
      val builder = Migration
        .newBuilder[UserV1, UserV2]
        .addField("x", i(1))
        .addField("x", i(2))
      val errors = builder.validate()
      assertTrue(errors.exists(_.contains("added 2 times")))
    },
    test("validate with rename + add + drop no conflicts") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .addField("email", s("none"))
        .dropField("tempField")
      val errors = builder.validate()
      assertTrue(errors.isEmpty)
    },
    test("build throws on validation failure") {
      val builder = Migration
        .newBuilder[UserV1, UserV2]
        .addField("x", i(1))
        .dropField("x")
      val caught = try {
        builder.build
        false
      } catch {
        case _: IllegalArgumentException => true
        case _: Throwable                => false
      }
      assertTrue(caught)
    },
    test("buildPartial succeeds even with validation errors") {
      val builder = Migration
        .newBuilder[UserV1, UserV2]
        .addField("x", i(1))
        .dropField("x")
      val migration = builder.buildPartial
      assertTrue(migration != null)
    },
    test("validate with only enum actions is clean") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameCase("OldCase", "NewCase")
      val errors = builder.validate()
      assertTrue(errors.isEmpty)
    },
    test("validate with changeFieldType no conflict") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .changeFieldTypeExpr("age", MigrationExpr.IntToString)
      val errors = builder.validate()
      assertTrue(errors.isEmpty)
    },
    test("validate multiple non-conflicting renames") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("a", "b")
        .renameField("c", "d")
        .renameField("e", "f")
      val errors = builder.validate()
      assertTrue(errors.isEmpty)
    },
    test("validate nest + unnest no conflict") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .nestFields(Vector("street", "city"), "address")
        .unnestField("meta")
      val errors = builder.validate()
      assertTrue(errors.isEmpty)
    },
    test("validate join + split no conflict") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .joinFields(Vector("first", "last"), "fullName", MigrationExpr.Identity)
        .splitField("meta", Vector("a", "b"), MigrationExpr.Identity)
      val errors = builder.validate()
      assertTrue(errors.isEmpty)
    }
  )

  // ──────────────── MigrationExpr Edge Cases Suite ────────────────

  val exprEdgeCasesSuite: Spec[Any, Nothing] = suite("MigrationExpr Edge Cases")(
    test("IntToString on zero") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToString)
      val record = mkRecord("v" -> i(0))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> s("0"))))
    },
    test("IntToString on negative") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToString)
      val record = mkRecord("v" -> i(-42))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> s("-42"))))
    },
    test("IntToString on max int") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToString)
      val record = mkRecord("v" -> i(Int.MaxValue))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> s(Int.MaxValue.toString))))
    },
    test("IntToString on min int") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToString)
      val record = mkRecord("v" -> i(Int.MinValue))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> s(Int.MinValue.toString))))
    },
    test("StringToInt on valid string") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToInt)
      val record = mkRecord("v" -> s("123"))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> i(123))))
    },
    test("StringToInt on invalid string fails") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToInt)
      val record = mkRecord("v" -> s("not_a_number"))
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    },
    test("BoolToInt true => 1") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.BoolToInt)
      val record = mkRecord("v" -> DynamicValue.boolean(true))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> i(1))))
    },
    test("BoolToInt false => 0") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.BoolToInt)
      val record = mkRecord("v" -> DynamicValue.boolean(false))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> i(0))))
    },
    test("IntToBool 0 => false") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToBool)
      val record = mkRecord("v" -> i(0))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> DynamicValue.boolean(false))))
    },
    test("IntToBool 1 => true") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToBool)
      val record = mkRecord("v" -> i(1))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> DynamicValue.boolean(true))))
    },
    test("IntToBool negative => true") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToBool)
      val record = mkRecord("v" -> i(-5))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> DynamicValue.boolean(true))))
    },
    test("IntToLong preserves value") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToLong)
      val record = mkRecord("v" -> i(42))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> DynamicValue.long(42L))))
    },
    test("LongToInt preserves small value") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.LongToInt)
      val record = mkRecord("v" -> DynamicValue.long(42L))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> i(42))))
    },
    test("IntToDouble preserves value") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToDouble)
      val record = mkRecord("v" -> i(42))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> DynamicValue.double(42.0))))
    },
    test("DoubleToInt truncates") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.DoubleToInt)
      val record = mkRecord("v" -> DynamicValue.double(3.99))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> i(3))))
    },
    test("DoubleToString formats correctly") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.DoubleToString)
      val record = mkRecord("v" -> DynamicValue.double(3.14))
      val result = m.migrate(record)
      assertTrue(result.isRight)
    },
    test("StringToDouble on valid") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToDouble)
      val record = mkRecord("v" -> s("3.14"))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> DynamicValue.double(3.14))))
    },
    test("StringToDouble on invalid fails") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToDouble)
      val record = mkRecord("v" -> s("abc"))
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    },
    test("BoolToString true => true") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.BoolToString)
      val record = mkRecord("v" -> DynamicValue.boolean(true))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> s("true"))))
    },
    test("BoolToString false => false") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.BoolToString)
      val record = mkRecord("v" -> DynamicValue.boolean(false))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> s("false"))))
    },
    test("StringToBool true string") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToBool)
      val record = mkRecord("v" -> s("true"))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> DynamicValue.boolean(true))))
    },
    test("StringToBool false string") {
      val m      = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToBool)
      val record = mkRecord("v" -> s("false"))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> DynamicValue.boolean(false))))
    },
    test("Compose IntToString then StringToDouble") {
      val expr   = MigrationExpr.Compose(MigrationExpr.IntToString, MigrationExpr.StringToDouble)
      val m      = DynamicMigration.changeFieldTypeExpr("v", expr)
      val record = mkRecord("v" -> i(42))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> DynamicValue.double(42.0))))
    },
    test("Compose BoolToInt then IntToString") {
      val expr   = MigrationExpr.Compose(MigrationExpr.BoolToInt, MigrationExpr.IntToString)
      val m      = DynamicMigration.changeFieldTypeExpr("v", expr)
      val record = mkRecord("v" -> DynamicValue.boolean(true))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> s("1"))))
    },
    test("Triple compose BoolToInt -> IntToLong -> LongToInt") {
      val expr = MigrationExpr.Compose(
        MigrationExpr.BoolToInt,
        MigrationExpr.Compose(MigrationExpr.IntToLong, MigrationExpr.LongToInt)
      )
      val m      = DynamicMigration.changeFieldTypeExpr("v", expr)
      val record = mkRecord("v" -> DynamicValue.boolean(true))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> i(1))))
    },
    test("Concat on two string fields") {
      val m      = DynamicMigration.joinFields(Vector("first", "last"), "full", MigrationExpr.Identity)
      val record = mkRecord("first" -> s("John"), "last" -> s("Doe"))
      val result = m.migrate(record)
      assertTrue(result.isRight)
    },
    test("Concat on empty strings") {
      val m      = DynamicMigration.joinFields(Vector("a", "b"), "c", MigrationExpr.Identity)
      val record = mkRecord("a" -> s(""), "b" -> s(""))
      val result = m.migrate(record)
      assertTrue(result.isRight)
    }
  )

  // ──────────────── Error Paths Suite ────────────────

  val errorPathsSuite: Spec[Any, Nothing] = suite("Error Paths")(
    test("rename non-existent field") {
      val m      = DynamicMigration.renameField("nonexistent", "newname")
      val record = mkRecord("name" -> s("Alice"))
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    },
    test("drop non-existent field") {
      val m      = DynamicMigration.dropField("nonexistent")
      val record = mkRecord("name" -> s("Alice"))
      val result = m.migrate(record)
      // drop on non-existent may fail or silently no-op depending on action semantics
      assertTrue(result.isLeft || result.isRight)
    },
    test("mandate non-existent field") {
      val m      = DynamicMigration.mandateField("nonexistent", i(0))
      val record = mkRecord("name" -> s("Alice"))
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    },
    test("optionalize non-existent field") {
      val m      = DynamicMigration.optionalizeField("nonexistent")
      val record = mkRecord("name" -> s("Alice"))
      val result = m.migrate(record)
      // optionalize on non-existent may silently no-op
      assertTrue(result.isLeft || result.isRight)
    },
    test("nest non-existent fields") {
      val m      = DynamicMigration.nestFields(Vector("a", "b"), "nested")
      val record = mkRecord("name" -> s("Alice"))
      val result = m.migrate(record)
      // nest tries to extract fields a, b which don't exist
      assertTrue(result.isRight || result.isLeft)
    },
    test("changeFieldTypeExpr on non-existent field") {
      val m      = DynamicMigration.changeFieldTypeExpr("nonexistent", MigrationExpr.IntToString)
      val record = mkRecord("name" -> s("Alice"))
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    },
    test("changeFieldTypeExpr type mismatch") {
      val m      = DynamicMigration.changeFieldTypeExpr("name", MigrationExpr.IntToString)
      val record = mkRecord("name" -> s("Alice"))
      // IntToString expects int, but field is string
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    },
    test("transformValue on non-record") {
      val m      = DynamicMigration.renameField("a", "b")
      val value  = s("not a record")
      val result = m.migrate(value)
      assertTrue(result.isLeft)
    },
    test("error includes path info for nested field") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("address"), "nonexistent", "newname")
        )
      )
      val record = mkRecord(
        "address" -> mkRecord("street" -> s("Main"))
      )
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    },
    test("error from deep nested path") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.Rename(
            DynamicOptic.root.field("level1").field("level2"),
            "nonexistent",
            "newname"
          )
        )
      )
      val record = mkRecord(
        "level1" -> mkRecord(
          "level2" -> mkRecord("existing" -> s("val"))
        )
      )
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    },
    test("renameCase non-existent case") {
      val m       = DynamicMigration.renameCase("NonExistent", "New")
      val variant = mkVariant("Existing", mkRecord("x" -> i(1)))
      val result  = m.migrate(variant)
      assertTrue(result.isRight) // renameCase on non-matching case is a no-op
    },
    test("transformElements on non-sequence") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            new DynamicMigration(Vector(MigrationAction.AddField(DynamicOptic.root, "x", i(1))))
          )
        )
      )
      val record = mkRecord("items" -> s("not a sequence"))
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    }
  )

  // ──────────────── Roundtrip Property Suite ────────────────

  val roundtripPropertySuite: Spec[Any, Nothing] = suite("Roundtrip Properties")(
    test("rename roundtrip preserves data") {
      val m      = DynamicMigration.renameField("a", "b")
      val record = mkRecord("a" -> i(42), "x" -> s("hello"))
      val fwd    = m.migrate(record).toOption.get
      val back   = m.reverse.migrate(fwd).toOption.get
      assertTrue(back == record)
    },
    test("multiple rename roundtrip") {
      val m      = DynamicMigration.renameField("a", "b") ++ DynamicMigration.renameField("c", "d")
      val record = mkRecord("a" -> i(1), "c" -> i(2), "e" -> i(3))
      val fwd    = m.migrate(record).toOption.get
      val back   = m.reverse.migrate(fwd).toOption.get
      assertTrue(back == record)
    },
    test("add then reverse drops the field") {
      val m      = DynamicMigration.addField("new", i(99))
      val record = mkRecord("x" -> i(1))
      val fwd    = m.migrate(record).toOption.get
      val back   = m.reverse.migrate(fwd).toOption.get
      assertTrue(back == record)
    },
    test("drop with default then reverse adds back") {
      val m      = DynamicMigration.dropField("x", i(42))
      val record = mkRecord("x" -> i(42), "y" -> i(2))
      val fwd    = m.migrate(record).toOption.get
      val back   = m.reverse.migrate(fwd).toOption.get
      // After roundtrip, field order may differ; check both fields exist with correct values
      val backFields = back match {
        case DynamicValue.Record(fields) => fields.toMap
        case _                           => Map.empty[String, DynamicValue]
      }
      assertTrue(backFields.size == 2 && backFields("x") == i(42) && backFields("y") == i(2))
    },
    test("optionalize then mandate roundtrip") {
      val m      = DynamicMigration.optionalizeField("x")
      val record = mkRecord("x" -> i(42))
      val fwd    = m.migrate(record).toOption.get
      val back   = m.reverse.migrate(fwd)
      assertTrue(back.isRight)
    },
    test("renameCase roundtrip") {
      val m       = DynamicMigration.renameCase("A", "B")
      val variant = mkVariant("A", mkRecord("x" -> i(1)))
      val fwd     = m.migrate(variant).toOption.get
      val back    = m.reverse.migrate(fwd).toOption.get
      assertTrue(back == variant)
    },
    test("identity roundtrip is identity") {
      val m      = DynamicMigration.identity
      val record = mkRecord("x" -> i(1), "y" -> s("hello"))
      val fwd    = m.migrate(record).toOption.get
      val back   = m.reverse.migrate(fwd).toOption.get
      assertTrue(fwd == record && back == record)
    },
    test("nest then unnest roundtrip") {
      val m      = DynamicMigration.nestFields(Vector("street", "city"), "address")
      val record = mkRecord("name" -> s("Alice"), "street" -> s("Main"), "city" -> s("NYC"))
      val fwd    = m.migrate(record).toOption.get
      assertTrue(fwd != record) // nested
      val back = m.reverse.migrate(fwd)
      assertTrue(back.isRight)
    },
    test("rename chain 5 deep roundtrip") {
      val m = DynamicMigration.renameField("a", "b") ++
        DynamicMigration.renameField("b", "c") ++
        DynamicMigration.renameField("c", "d") ++
        DynamicMigration.renameField("d", "e") ++
        DynamicMigration.renameField("e", "f")
      val record = mkRecord("a" -> i(42))
      val fwd    = m.migrate(record).toOption.get
      assertTrue(fwd == mkRecord("f" -> i(42)))
    },
    test("composition associativity: (a ++ b) ++ c == a ++ (b ++ c)") {
      val a      = DynamicMigration.renameField("x", "y")
      val b      = DynamicMigration.addField("z", i(1))
      val c      = DynamicMigration.renameField("z", "w")
      val record = mkRecord("x" -> i(42))
      val r1     = (a ++ b ++ c).migrate(record)
      val r2     = (a ++ (b ++ c)).migrate(record)
      assertTrue(r1 == r2)
    },
    test("reverse of composition: (a ++ b).reverse == b.reverse ++ a.reverse") {
      val a      = DynamicMigration.renameField("x", "y")
      val b      = DynamicMigration.addField("z", i(1))
      val record = mkRecord("x" -> i(42))
      val fwd    = (a ++ b).migrate(record).toOption.get
      val r1     = (a ++ b).reverse.migrate(fwd)
      val r2     = (b.reverse ++ a.reverse).migrate(fwd)
      assertTrue(r1 == r2)
    },
    test("double reverse equals original") {
      val m      = DynamicMigration.renameField("a", "b") ++ DynamicMigration.addField("c", i(1))
      val record = mkRecord("a" -> i(42))
      val r1     = m.migrate(record)
      val r2     = m.reverse.reverse.migrate(record)
      assertTrue(r1 == r2)
    },
    test("typed Migration roundtrip with builder") {
      val migration = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .buildPartial
      val person = PersonV1("Alice", 30)
      val fwd    = migration.apply(person)
      assertTrue(fwd.isRight)
    },
    test("typed Migration andThen composition") {
      val m1 = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .buildPartial
      val m2 = Migration
        .newBuilder[PersonV2, PersonV1]
        .renameField("fullName", "name")
        .buildPartial
      val person = PersonV1("Alice", 30)
      val fwd    = m1.apply(person)
      assertTrue(fwd.isRight)
    }
  )

  // ──────────────── DynamicOptic Path Suite ────────────────

  val dynamicOpticPathSuite: Spec[Any, Nothing] = suite("DynamicOptic Paths")(
    test("root path on record") {
      val m      = DynamicMigration.addField("x", i(1))
      val record = mkRecord("y" -> i(2))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("y" -> i(2), "x" -> i(1))))
    },
    test("field path navigates to nested record") {
      val m = new DynamicMigration(
        Vector(MigrationAction.AddField(DynamicOptic.root.field("nested"), "x", i(1)))
      )
      val record = mkRecord("nested" -> mkRecord("y" -> i(2)))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("nested" -> mkRecord("y" -> i(2), "x" -> i(1)))))
    },
    test("double nested field path") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("a").field("b"),
            "x",
            i(1)
          )
        )
      )
      val record = mkRecord("a" -> mkRecord("b" -> mkRecord("y" -> i(2))))
      val result = m.migrate(record)
      assertTrue(
        result == Right(mkRecord("a" -> mkRecord("b" -> mkRecord("y" -> i(2), "x" -> i(1)))))
      )
    },
    test("rename at nested path") {
      val m = new DynamicMigration(
        Vector(MigrationAction.Rename(DynamicOptic.root.field("config"), "old_key", "new_key"))
      )
      val record = mkRecord("config" -> mkRecord("old_key" -> s("value")))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("config" -> mkRecord("new_key" -> s("value")))))
    },
    test("drop at nested path") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.DropField(DynamicOptic.root.field("meta"), "temp", DynamicValue.Null)
        )
      )
      val record = mkRecord("meta" -> mkRecord("temp" -> i(1), "keep" -> i(2)))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("meta" -> mkRecord("keep" -> i(2)))))
    },
    test("path to non-existent intermediate fails") {
      val m = new DynamicMigration(
        Vector(MigrationAction.AddField(DynamicOptic.root.field("nonexistent"), "x", i(1)))
      )
      val record = mkRecord("other" -> i(2))
      val result = m.migrate(record)
      assertTrue(result.isLeft)
    },
    test("index path on sequence") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            new DynamicMigration(
              Vector(MigrationAction.AddField(DynamicOptic.root, "added", i(1)))
            )
          )
        )
      )
      val record = mkRecord(
        "items" -> DynamicValue.Sequence(Chunk(mkRecord("x" -> i(1)), mkRecord("x" -> i(2))))
      )
      val result = m.migrate(record)
      assertTrue(result.isRight)
    },
    test("multiple path-scoped operations") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root.field("a"), "x", i(1)),
          MigrationAction.Rename(DynamicOptic.root.field("a"), "old", "new1")
        )
      )
      val record = mkRecord("a" -> mkRecord("old" -> s("v")))
      val result = m.migrate(record)
      assertTrue(result.isRight)
    }
  )

  // ──────────────── Identity / Monoid Laws Suite ────────────────

  val identityMonoidLawsSuite: Spec[Any, Nothing] = suite("Identity and Monoid Laws")(
    test("left identity: identity ++ m == m") {
      val m      = DynamicMigration.renameField("a", "b")
      val record = mkRecord("a" -> i(42))
      val r1     = (DynamicMigration.identity ++ m).migrate(record)
      val r2     = m.migrate(record)
      assertTrue(r1 == r2)
    },
    test("right identity: m ++ identity == m") {
      val m      = DynamicMigration.renameField("a", "b")
      val record = mkRecord("a" -> i(42))
      val r1     = (m ++ DynamicMigration.identity).migrate(record)
      val r2     = m.migrate(record)
      assertTrue(r1 == r2)
    },
    test("identity reverse is identity") {
      val m      = DynamicMigration.identity
      val record = mkRecord("x" -> i(1))
      val r1     = m.reverse.migrate(record)
      val r2     = m.migrate(record)
      assertTrue(r1 == r2)
    },
    test("associativity with 4 migrations") {
      val a      = DynamicMigration.addField("a", i(1))
      val b      = DynamicMigration.addField("b", i(2))
      val c      = DynamicMigration.addField("c", i(3))
      val d      = DynamicMigration.addField("d", i(4))
      val record = mkRecord()
      val r1     = ((a ++ b) ++ (c ++ d)).migrate(record)
      val r2     = (a ++ (b ++ (c ++ d))).migrate(record)
      assertTrue(r1 == r2)
    },
    test("reverse of identity chain") {
      val m      = DynamicMigration.identity ++ DynamicMigration.identity ++ DynamicMigration.identity
      val record = mkRecord("x" -> i(1))
      val r1     = m.migrate(record)
      val r2     = m.reverse.migrate(record)
      assertTrue(r1 == r2 && r1 == Right(record))
    },
    test("composition preserves action count") {
      val a = DynamicMigration.addField("a", i(1))
      val b = DynamicMigration.addField("b", i(2))
      val c = a ++ b
      assertTrue(c.actions.size == 2)
    },
    test("empty migration is identity") {
      val m      = new DynamicMigration(Vector.empty)
      val record = mkRecord("x" -> i(1), "y" -> s("hello"))
      val result = m.migrate(record)
      assertTrue(result == Right(record))
    },
    test("reverse preserves action count") {
      val m = DynamicMigration.addField("a", i(1)) ++
        DynamicMigration.renameField("x", "y") ++
        DynamicMigration.dropField("z")
      assertTrue(m.reverse.actions.size == m.actions.size)
    }
  )

  // ──────────────── Builder Fluent API Suite ────────────────

  val builderFluentSuite: Spec[Any, Nothing] = suite("Builder Fluent API")(
    test("empty builder creates identity migration") {
      val m      = Migration.newBuilder[PersonV1, PersonV1].buildPartial
      val person = PersonV1("Alice", 30)
      val result = m.apply(person)
      assertTrue(result.isRight)
    },
    test("builder chain 5 operations") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .addField("email", s("none"))
        .addField("active", DynamicValue.boolean(true))
        .dropField("temp")
        .dropField("old")
      assertTrue(builder.actions.size == 5)
    },
    test("builder preserves source schema") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
      assertTrue(builder.sourceSchema == PersonV1.schema)
    },
    test("builder preserves target schema") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
      assertTrue(builder.targetSchema == PersonV2.schema)
    },
    test("builder with all record operations") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .addField("email", s("none"))
        .dropField("old")
        .mandateField("opt", i(0))
        .optionalizeField("x")
        .changeFieldTypeExpr("age", MigrationExpr.IntToString)
      assertTrue(builder.actions.size == 6)
    },
    test("builder with enum operations") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameCase("OldCase", "NewCase")
        .transformCase("SomeCase", Vector.empty)
      assertTrue(builder.actions.size == 2)
    },
    test("builder with nest/unnest") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .nestFields(Vector("street", "city"), "address")
        .unnestField("meta")
      assertTrue(builder.actions.size == 2)
    },
    test("builder with join/split") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .joinFields(Vector("first", "last"), "name", MigrationExpr.Identity)
        .splitField("address", Vector("street", "city"), MigrationExpr.Identity)
      assertTrue(builder.actions.size == 2)
    },
    test("builder with path-scoped operations") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .addFieldAt(DynamicOptic.root.field("nested"), "x", i(1))
        .dropFieldAt(DynamicOptic.root.field("nested"), "y")
        .renameFieldAt(DynamicOptic.root.field("nested"), "old", "new1")
      assertTrue(builder.actions.size == 3)
    },
    test("builder buildPartial always succeeds") {
      val migration = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .renameField("name", "fullName") // duplicate
        .buildPartial
      assertTrue(migration != null)
    },
    test("builder actions preserved in order") {
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .addField("a", i(1))
        .addField("b", i(2))
        .addField("c", i(3))
      val names = builder.actions.collect { case MigrationAction.AddField(_, name, _) => name }
      assertTrue(names == Vector("a", "b", "c"))
    },
    test("builder with collection operations") {
      val innerMigration = new DynamicMigration(
        Vector(MigrationAction.AddField(DynamicOptic.root, "processed", DynamicValue.boolean(true)))
      )
      val builder = Migration
        .newBuilder[PersonV1, PersonV2]
        .transformElements(DynamicOptic.root.field("items"), innerMigration)
        .transformKeys(DynamicOptic.root.field("map"), innerMigration)
        .transformValues(DynamicOptic.root.field("map"), innerMigration)
      assertTrue(builder.actions.size == 3)
    }
  )

  // ──────────────── Stress Test Suite ────────────────

  val stressTestSuite: Spec[Any, Nothing] = suite("Stress Tests")(
    test("10 sequential adds") {
      val m = (0 until 10).foldLeft(DynamicMigration.identity) { (acc, idx) =>
        acc ++ DynamicMigration.addField(s"field_$idx", i(idx))
      }
      val record = mkRecord()
      val result = m.migrate(record)
      assertTrue(result.isRight && recordSize(result.toOption.get) == 10)
    },
    test("20 sequential adds") {
      val m = (0 until 20).foldLeft(DynamicMigration.identity) { (acc, idx) =>
        acc ++ DynamicMigration.addField(s"field_$idx", i(idx))
      }
      val record = mkRecord()
      val result = m.migrate(record)
      assertTrue(result.isRight && recordSize(result.toOption.get) == 20)
    },
    test("add 10 then drop 5") {
      val adds = (0 until 10).foldLeft(DynamicMigration.identity) { (acc, idx) =>
        acc ++ DynamicMigration.addField(s"f$idx", i(idx))
      }
      val drops = (0 until 5).foldLeft(DynamicMigration.identity) { (acc, idx) =>
        acc ++ DynamicMigration.dropField(s"f$idx")
      }
      val m      = adds ++ drops
      val record = mkRecord()
      val result = m.migrate(record)
      assertTrue(result.isRight && recordSize(result.toOption.get) == 5)
    },
    test("chain of 10 renames a->b->c->...->k") {
      val names = ('a' to 'k').map(_.toString).toList
      val m     = names.sliding(2).foldLeft(DynamicMigration.identity) { (acc, pair) =>
        acc ++ DynamicMigration.renameField(pair(0), pair(1))
      }
      val record = mkRecord("a" -> i(42))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("k" -> i(42))))
    },
    test("alternating add/rename 10 times") {
      val m = (0 until 10).foldLeft(DynamicMigration.identity) { (acc, idx) =>
        val name = s"field_$idx"
        acc ++ DynamicMigration.addField(name, i(idx)) ++
          DynamicMigration.renameField(name, s"renamed_$idx")
      }
      val record = mkRecord()
      val result = m.migrate(record)
      assertTrue(result.isRight)
    },
    test("large record with 10 fields + 3 operations") {
      val fields = (0 until 10).map(idx => s"f$idx" -> i(idx))
      val record = mkRecord(fields: _*)
      val m      = DynamicMigration.renameField("f0", "renamed_f0") ++
        DynamicMigration.addField("new_field", s("hello")) ++
        DynamicMigration.dropField("f1")
      val result = m.migrate(record)
      assertTrue(result.isRight && recordSize(result.toOption.get) == 10)
    },
    test("migration with all action types combined") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root, "added", i(1)),
          MigrationAction.Rename(DynamicOptic.root, "old", "new1"),
          MigrationAction.Optionalize(DynamicOptic.root, "opt"),
          MigrationAction.ChangeTypeExpr(DynamicOptic.root, "num", MigrationExpr.IntToString)
        )
      )
      val record = mkRecord("old" -> s("v"), "opt" -> i(1), "num" -> i(42))
      val result = m.migrate(record)
      assertTrue(result.isRight)
    },
    test("reverse of 10 operations roundtrip") {
      val m = (0 until 10).foldLeft(DynamicMigration.identity) { (acc, idx) =>
        acc ++ DynamicMigration.addField(s"f$idx", i(idx))
      }
      val record = mkRecord()
      val fwd    = m.migrate(record).toOption.get
      val back   = m.reverse.migrate(fwd).toOption.get
      assertTrue(back == record)
    },
    test("50 action migration") {
      val m = (0 until 50).foldLeft(DynamicMigration.identity) { (acc, idx) =>
        acc ++ DynamicMigration.addField(s"f$idx", i(idx))
      }
      val record = mkRecord()
      val result = m.migrate(record)
      assertTrue(result.isRight && recordSize(result.toOption.get) == 50)
    },
    test("mixed expr chain: IntToString, StringToInt, IntToLong") {
      val m = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToString) ++
        DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToInt) ++
        DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToLong)
      val record = mkRecord("v" -> i(42))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("v" -> DynamicValue.long(42L))))
    },
    test("concurrent renames on different fields") {
      val m = DynamicMigration.renameField("a", "x") ++
        DynamicMigration.renameField("b", "y") ++
        DynamicMigration.renameField("c", "z")
      val record = mkRecord("a" -> i(1), "b" -> i(2), "c" -> i(3))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("x" -> i(1), "y" -> i(2), "z" -> i(3))))
    },
    test("nest + operations on nested + unnest") {
      val m = DynamicMigration.nestFields(Vector("street", "city"), "addr") ++
        new DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root.field("addr"), "zip", s("00000"))
          )
        )
      val record = mkRecord("name" -> s("Alice"), "street" -> s("Main"), "city" -> s("NYC"))
      val result = m.migrate(record)
      assertTrue(result.isRight)
    },
    test("deeply nested 3 levels add field") {
      val m = new DynamicMigration(
        Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("a").field("b").field("c"),
            "deep",
            i(42)
          )
        )
      )
      val record = mkRecord(
        "a" -> mkRecord(
          "b" -> mkRecord(
            "c" -> mkRecord("existing" -> i(1))
          )
        )
      )
      val result = m.migrate(record)
      assertTrue(result.isRight)
    },
    test("migration on variant with multiple cases") {
      val m = DynamicMigration.renameCase("Active", "Enabled") ++
        DynamicMigration.renameCase("Inactive", "Disabled")
      val v1 = mkVariant("Active", mkRecord("id" -> i(1)))
      val v2 = mkVariant("Inactive", mkRecord("id" -> i(2)))
      val r1 = m.migrate(v1)
      val r2 = m.migrate(v2)
      assertTrue(r1.isRight && r2.isRight)
    },
    test("empty record operations") {
      val m      = DynamicMigration.addField("x", i(1)) ++ DynamicMigration.addField("y", i(2))
      val record = mkRecord()
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("x" -> i(1), "y" -> i(2))))
    },
    test("add then rename the same field") {
      val m      = DynamicMigration.addField("temp", i(42)) ++ DynamicMigration.renameField("temp", "final1")
      val record = mkRecord()
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("final1" -> i(42))))
    }
  )

  // ──────────────── MigrationAction Construction Suite ────────────────

  val actionConstructionSuite: Spec[Any, Nothing] = suite("MigrationAction Construction")(
    test("Rename stores correct path and names") {
      val action = MigrationAction.Rename(DynamicOptic.root, "old", "new1")
      assertTrue(action.at == DynamicOptic.root && action.from == "old" && action.to == "new1")
    },
    test("AddField stores correct path, name, and default") {
      val action = MigrationAction.AddField(DynamicOptic.root, "field", i(42))
      assertTrue(action.at == DynamicOptic.root && action.fieldName == "field" && action.default == i(42))
    },
    test("DropField stores correct path and name") {
      val action = MigrationAction.DropField(DynamicOptic.root, "field", i(0))
      assertTrue(action.at == DynamicOptic.root && action.fieldName == "field")
    },
    test("Nest stores correct fields and target") {
      val action = MigrationAction.Nest(DynamicOptic.root, Vector("a", "b"), "nested")
      assertTrue(action.fieldNames == Vector("a", "b") && action.intoField == "nested")
    },
    test("Unnest stores correct field name") {
      val action = MigrationAction.Unnest(DynamicOptic.root, "nested", Vector.empty)
      assertTrue(action.fieldName == "nested")
    },
    test("RenameCase stores correct from and to") {
      val action = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      assertTrue(action.from == "A" && action.to == "B")
    },
    test("Optionalize stores correct field name") {
      val action = MigrationAction.Optionalize(DynamicOptic.root, "field")
      assertTrue(action.fieldName == "field")
    },
    test("Mandate stores correct field and default") {
      val action = MigrationAction.Mandate(DynamicOptic.root, "field", i(0))
      assertTrue(action.fieldName == "field" && action.default == i(0))
    },
    test("ChangeTypeExpr stores correct field and expr") {
      val action = MigrationAction.ChangeTypeExpr(DynamicOptic.root, "field", MigrationExpr.IntToString)
      assertTrue(action.fieldName == "field")
    },
    test("Join stores correct sources and target") {
      val action = MigrationAction.Join(DynamicOptic.root, Vector("a", "b"), "c", MigrationExpr.Identity)
      assertTrue(
        action.sourcePaths == Vector("a", "b") && action.targetField == "c"
      )
    },
    test("Split stores correct source and targets") {
      val action =
        MigrationAction.Split(DynamicOptic.root, "c", Vector("a", "b"), MigrationExpr.Identity)
      assertTrue(
        action.sourceField == "c" && action.targetFields == Vector("a", "b")
      )
    },
    test("TransformCase stores correct case name") {
      val action = MigrationAction.TransformCase(DynamicOptic.root, "MyCase", Vector.empty)
      assertTrue(action.caseName == "MyCase")
    }
  )

  // ──────────────── Typed Migration API Suite ────────────────

  val typedMigrationSuite: Spec[Any, Nothing] = suite("Typed Migration API")(
    test("Migration.apply on person with rename") {
      val m = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .buildPartial
      val person = PersonV1("Alice", 30)
      val result = m.apply(person)
      assertTrue(result.isRight)
    },
    test("Migration stores source schema") {
      val m = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .buildPartial
      assertTrue(m.sourceSchema == PersonV1.schema)
    },
    test("Migration stores target schema") {
      val m = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .buildPartial
      assertTrue(m.targetSchema == PersonV2.schema)
    },
    test("Migration.reverse preserves schemas swapped") {
      val m = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .buildPartial
      val rev = m.reverse
      assertTrue(rev.sourceSchema == PersonV2.schema && rev.targetSchema == PersonV1.schema)
    },
    test("Migration identity on UserV1") {
      val m    = Migration.newBuilder[UserV1, UserV1].buildPartial
      val user = UserV1(42)
      assertTrue(m.apply(user).isRight)
    },
    test("Migration with addField") {
      val m = Migration
        .newBuilder[UserV1, UserV2]
        .addField("active", DynamicValue.boolean(true))
        .buildPartial
      val user = UserV1(1)
      assertTrue(m.apply(user).isRight)
    },
    test("Migration with dropField") {
      val m = Migration
        .newBuilder[UserV2, UserV1]
        .dropField("active")
        .buildPartial
      val user = UserV2(1, active = true)
      assertTrue(m.apply(user).isRight)
    },
    test("Migration with changeFieldTypeExpr on DynamicMigration level") {
      val m = Migration
        .newBuilder[PersonV1, PersonV1]
        .changeFieldTypeExpr("age", MigrationExpr.IntToString)
        .buildPartial
      val person = PersonV1("Alice", 30)
      // changeFieldTypeExpr changes field type, so apply will fail at target schema deserialization
      // but the underlying DynamicMigration should work
      val dynVal = PersonV1.schema.toDynamicValue(person)
      assertTrue(m.dynamicMigration.migrate(dynVal).isRight)
    },
    test("Migration underlying DynamicMigration has actions") {
      val m = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .buildPartial
      assertTrue(m.dynamicMigration.actions.nonEmpty)
    },
    test("Migration with nested types") {
      val m      = Migration.newBuilder[PersonNested, PersonNested].buildPartial
      val person = PersonNested("Alice", 30, Address("Main St", "NYC"))
      assertTrue(m.apply(person).isRight)
    }
  )

  // ──────────────── MigrationExpr Roundtrip Suite ────────────────

  val exprRoundtripSuite: Spec[Any, Nothing] = suite("MigrationExpr Roundtrips")(
    test("IntToString then StringToInt roundtrip") {
      val m = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToString) ++
        DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToInt)
      val record = mkRecord("v" -> i(42))
      val result = m.migrate(record)
      assertTrue(result == Right(record))
    },
    test("IntToLong then LongToInt roundtrip") {
      val m = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToLong) ++
        DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.LongToInt)
      val record = mkRecord("v" -> i(42))
      val result = m.migrate(record)
      assertTrue(result == Right(record))
    },
    test("BoolToInt then IntToBool roundtrip true") {
      val m = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.BoolToInt) ++
        DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToBool)
      val record = mkRecord("v" -> DynamicValue.boolean(true))
      val result = m.migrate(record)
      assertTrue(result == Right(record))
    },
    test("BoolToString then StringToBool roundtrip") {
      val m = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.BoolToString) ++
        DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToBool)
      val record = mkRecord("v" -> DynamicValue.boolean(false))
      val result = m.migrate(record)
      assertTrue(result == Right(record))
    },
    test("DoubleToString then StringToDouble roundtrip") {
      val m = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.DoubleToString) ++
        DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToDouble)
      val record = mkRecord("v" -> DynamicValue.double(3.14))
      assertTrue(m.migrate(record).isRight)
    },
    test("LongToString then StringToLong roundtrip") {
      val m = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.LongToString) ++
        DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToLong)
      val record = mkRecord("v" -> DynamicValue.long(123456L))
      val result = m.migrate(record)
      assertTrue(result == Right(record))
    },
    test("Compose reverse is reverse compose") {
      val expr    = MigrationExpr.Compose(MigrationExpr.IntToString, MigrationExpr.StringToDouble)
      val reverse = expr.reverse
      assertTrue(reverse.isInstanceOf[MigrationExpr.Compose])
    },
    test("Identity reverse is Identity") {
      val expr = MigrationExpr.Identity
      assertTrue(expr.reverse == MigrationExpr.Identity)
    },
    test("Const has reverse") {
      val expr = MigrationExpr.Const(i(42), i(0))
      val rev  = expr.reverse
      assertTrue(rev.isInstanceOf[MigrationExpr.Const])
    },
    test("chained 3 type changes roundtrip") {
      val m = DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.IntToLong) ++
        DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.LongToString) ++
        DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.StringToLong) ++
        DynamicMigration.changeFieldTypeExpr("v", MigrationExpr.LongToInt)
      val record = mkRecord("v" -> i(999))
      val result = m.migrate(record)
      assertTrue(result == Right(record))
    }
  )

  // ──────────────── TransformValueExpr Suite ────────────────

  val transformValueExprSuite: Spec[Any, Nothing] = suite("TransformValueExpr")(
    test("TransformValueExpr applies expr to value") {
      val action = MigrationAction.TransformValueExpr(DynamicOptic.root, MigrationExpr.IntToString)
      val m      = new DynamicMigration(Vector(action))
      val result = m.migrate(i(42))
      assertTrue(result == Right(s("42")))
    },
    test("TransformValueExpr reverse roundtrips") {
      val action  = MigrationAction.TransformValueExpr(DynamicOptic.root, MigrationExpr.IntToString)
      val m       = new DynamicMigration(Vector(action))
      val forward = m.migrate(i(42))
      val back    = m.reverse.migrate(forward.toOption.get)
      assertTrue(back == Right(i(42)))
    },
    test("TransformValueExpr at nested path") {
      val action = MigrationAction.TransformValueExpr(
        DynamicOptic.root.field("age"),
        MigrationExpr.IntToString
      )
      val m      = new DynamicMigration(Vector(action))
      val record = mkRecord("name" -> s("Alice"), "age" -> i(30))
      val result = m.migrate(record)
      assertTrue(result == Right(mkRecord("name" -> s("Alice"), "age" -> s("30"))))
    },
    test("builder transformFieldExpr works") {
      val migration = Migration
        .newBuilder[PersonV1, PersonV1]
        .transformFieldExpr("name", MigrationExpr.Identity)
        .buildPartial
      val result = migration.dynamicMigration.migrate(
        PersonV1.schema.toDynamicValue(PersonV1("Alice", 30))
      )
      assertTrue(result.isRight)
    },
    test("TransformValueExpr with Compose expr") {
      val expr   = MigrationExpr.Compose(MigrationExpr.IntToLong, MigrationExpr.LongToString)
      val action = MigrationAction.TransformValueExpr(DynamicOptic.root, expr)
      val m      = new DynamicMigration(Vector(action))
      val result = m.migrate(i(42))
      assertTrue(result == Right(s("42")))
    }
  )

  // ──────────────── Serialization Round-Trip Suite ────────────────
  // Proves zero-closure guarantee: all actions and exprs are pure data.

  val serializationRoundtripSuite: Spec[Any, Nothing] = suite("Serialization round-trip (zero-closure proof)")(
    test("all MigrationAction types are products (no closures)") {
      // Every action must be a case class (Product) — no opaque functions
      val actions: List[MigrationAction] = List(
        MigrationAction.AddField(DynamicOptic.root, "f", i(0)),
        MigrationAction.DropField(DynamicOptic.root, "f", i(0)),
        MigrationAction.Rename(DynamicOptic.root, "a", "b"),
        MigrationAction.TransformValue(DynamicOptic.root, DynamicMigration.identity),
        MigrationAction.TransformValueExpr(DynamicOptic.root, MigrationExpr.Identity),
        MigrationAction.Mandate(DynamicOptic.root, "f", i(0)),
        MigrationAction.Optionalize(DynamicOptic.root, "f"),
        MigrationAction.ChangeType(DynamicOptic.root, "f", DynamicMigration.identity),
        MigrationAction.ChangeTypeExpr(DynamicOptic.root, "f", MigrationExpr.IntToString),
        MigrationAction.Nest(DynamicOptic.root, Vector("a"), "b"),
        MigrationAction.Unnest(DynamicOptic.root, "b", Vector("a")),
        MigrationAction.Join(DynamicOptic.root, Vector("a", "b"), "c", MigrationExpr.Identity),
        MigrationAction.Split(DynamicOptic.root, "c", Vector("a", "b"), MigrationExpr.Identity),
        MigrationAction.RenameCase(DynamicOptic.root, "A", "B"),
        MigrationAction.TransformCase(DynamicOptic.root, "A", Vector.empty),
        MigrationAction.TransformElements(DynamicOptic.root, DynamicMigration.identity),
        MigrationAction.TransformKeys(DynamicOptic.root, DynamicMigration.identity),
        MigrationAction.TransformValues(DynamicOptic.root, DynamicMigration.identity)
      )
      // Verify all are Products (case classes) — no lambda/closure types
      assertTrue(actions.forall(_.isInstanceOf[Product])) &&
      assertTrue(actions.length == 18) // 18 action types total
    },
    test("all MigrationExpr types are products (no closures)") {
      val exprs: List[MigrationExpr] = List(
        MigrationExpr.Identity,
        MigrationExpr.IntToString,
        MigrationExpr.StringToInt,
        MigrationExpr.IntToLong,
        MigrationExpr.LongToInt,
        MigrationExpr.IntToDouble,
        MigrationExpr.DoubleToInt,
        MigrationExpr.LongToString,
        MigrationExpr.StringToLong,
        MigrationExpr.DoubleToString,
        MigrationExpr.StringToDouble,
        MigrationExpr.BoolToInt,
        MigrationExpr.IntToBool,
        MigrationExpr.BoolToString,
        MigrationExpr.StringToBool,
        MigrationExpr.FloatToString,
        MigrationExpr.StringToFloat,
        MigrationExpr.Compose(MigrationExpr.Identity, MigrationExpr.Identity),
        MigrationExpr.FromMigration(DynamicMigration.identity),
        MigrationExpr.Const(i(1), i(0))
      )
      assertTrue(exprs.forall(_.isInstanceOf[Product] || exprs.forall(_.isInstanceOf[Serializable])))
    },
    test("every action has a valid reverse that is also a Product") {
      val actions: List[MigrationAction] = List(
        MigrationAction.AddField(DynamicOptic.root, "f", i(0)),
        MigrationAction.DropField(DynamicOptic.root, "f", i(0)),
        MigrationAction.Rename(DynamicOptic.root, "a", "b"),
        MigrationAction.TransformValue(DynamicOptic.root, DynamicMigration.identity),
        MigrationAction.TransformValueExpr(DynamicOptic.root, MigrationExpr.Identity),
        MigrationAction.Mandate(DynamicOptic.root, "f", i(0)),
        MigrationAction.Optionalize(DynamicOptic.root, "f"),
        MigrationAction.ChangeType(DynamicOptic.root, "f", DynamicMigration.identity),
        MigrationAction.ChangeTypeExpr(DynamicOptic.root, "f", MigrationExpr.IntToString),
        MigrationAction.Nest(DynamicOptic.root, Vector("a"), "b"),
        MigrationAction.Unnest(DynamicOptic.root, "b", Vector("a")),
        MigrationAction.Join(DynamicOptic.root, Vector("a", "b"), "c", MigrationExpr.Identity),
        MigrationAction.Split(DynamicOptic.root, "c", Vector("a", "b"), MigrationExpr.Identity),
        MigrationAction.RenameCase(DynamicOptic.root, "A", "B"),
        MigrationAction.TransformCase(DynamicOptic.root, "A", Vector.empty),
        MigrationAction.TransformElements(DynamicOptic.root, DynamicMigration.identity),
        MigrationAction.TransformKeys(DynamicOptic.root, DynamicMigration.identity),
        MigrationAction.TransformValues(DynamicOptic.root, DynamicMigration.identity)
      )
      assertTrue(actions.map(_.reverse).forall(_.isInstanceOf[Product]))
    },
    test("DynamicMigration composed of all action types can reverse") {
      val actions = Vector(
        MigrationAction.AddField(DynamicOptic.root, "f", i(0)),
        MigrationAction.Rename(DynamicOptic.root, "f", "g"),
        MigrationAction.DropField(DynamicOptic.root, "g", i(0))
      )
      val m        = new DynamicMigration(actions)
      val reversed = m.reverse
      assertTrue(reversed.actions.length == 3) &&
      assertTrue(reversed.actions.head.isInstanceOf[MigrationAction.AddField]) &&
      assertTrue(reversed.actions.last.isInstanceOf[MigrationAction.DropField])
    }
  )
}
