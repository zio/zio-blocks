package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object CollectionMigrationSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Collection Migration")(
    suite("Sequence migrations")(
      test("Apply migration to each element in a sequence") {
        val original = DynamicValue.Sequence(
          DynamicValue.Record("name" -> DynamicValue.string("Alice")),
          DynamicValue.Record("name" -> DynamicValue.string("Bob"))
        )
        val elementMigration = DynamicMigration.record(_.addField("active", DynamicValue.boolean(true)))
        val migration        = DynamicMigration.sequence(elementMigration)
        val result           = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Sequence(
              DynamicValue.Record(
                "name"   -> DynamicValue.string("Alice"),
                "active" -> DynamicValue.boolean(true)
              ),
              DynamicValue.Record(
                "name"   -> DynamicValue.string("Bob"),
                "active" -> DynamicValue.boolean(true)
              )
            )
          )
        )
      }
    ),
    suite("Sequence element migration failure")(
      test("Sequence migration fails if any element fails") {
        val original = DynamicValue.Sequence(
          DynamicValue.Record("name" -> DynamicValue.string("Alice")),
          DynamicValue.int(42),
          DynamicValue.Record("name" -> DynamicValue.string("Bob"))
        )
        val elementMigration = DynamicMigration.record(_.addField("age", DynamicValue.int(0)))
        val migration        = DynamicMigration.sequence(elementMigration)
        val result           = migration(original)

        assertTrue(result.isLeft)
      },
      test("Sequence migration fails when applied to non-Sequence value") {
        val original  = DynamicValue.Record("field" -> DynamicValue.int(1))
        val migration =
          DynamicMigration(MigrationStep.Sequence(MigrationStep.Record.empty.addField("x", DynamicValue.int(0))))
        val result = migration(original)

        result match {
          case Left(MigrationError.TypeMismatch(_, expected, _)) => assertTrue(expected == "Sequence")
          case _                                                 => assertTrue(false)
        }
      }
    ),
    suite("Map migrations")(
      test("MapEntries applies key and value transformations") {
        val original = DynamicValue.Map(
          DynamicValue.string("key1") -> DynamicValue.Record("count" -> DynamicValue.int(10)),
          DynamicValue.string("key2") -> DynamicValue.Record("count" -> DynamicValue.int(20))
        )
        val keyMigration   = DynamicMigration.identity
        val valueMigration = DynamicMigration.record(_.addField("active", DynamicValue.boolean(true)))
        val migration      = DynamicMigration(
          MigrationStep.MapEntries(keyMigration.step, valueMigration.step)
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Map(
              DynamicValue.string("key1") -> DynamicValue.Record(
                "count"  -> DynamicValue.int(10),
                "active" -> DynamicValue.boolean(true)
              ),
              DynamicValue.string("key2") -> DynamicValue.Record(
                "count"  -> DynamicValue.int(20),
                "active" -> DynamicValue.boolean(true)
              )
            )
          )
        )
      },
      test("MapEntries key transformation works correctly") {
        val original = DynamicValue.Map(
          DynamicValue.Record("id" -> DynamicValue.string("a")) -> DynamicValue.int(1),
          DynamicValue.Record("id" -> DynamicValue.string("b")) -> DynamicValue.int(2)
        )
        val keyMigration = DynamicMigration.record(_.renameField("id", "key"))
        val migration    = DynamicMigration(
          MigrationStep.MapEntries(keyMigration.step, MigrationStep.NoOp)
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Map(
              DynamicValue.Record("key" -> DynamicValue.string("a")) -> DynamicValue.int(1),
              DynamicValue.Record("key" -> DynamicValue.string("b")) -> DynamicValue.int(2)
            )
          )
        )
      },
      test("MapEntries fails when key migration produces duplicate keys") {
        val original = DynamicValue.Map(
          DynamicValue.Record("x" -> DynamicValue.int(1), "y" -> DynamicValue.int(2)) -> DynamicValue.string("a"),
          DynamicValue.Record("x" -> DynamicValue.int(1), "y" -> DynamicValue.int(3)) -> DynamicValue.string("b")
        )
        val keyMigration = DynamicMigration.record(_.removeField("y", DynamicValue.int(0)))
        val migration    = DynamicMigration(
          MigrationStep.MapEntries(keyMigration.step, MigrationStep.NoOp)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.DuplicateMapKey(_)) => assertTrue(true)
          case _                                       => assertTrue(false)
        }
      },
      test("MapEntries is reversible") {
        val original = DynamicValue.Map(
          DynamicValue.string("a") -> DynamicValue.Record("v" -> DynamicValue.int(1)),
          DynamicValue.string("b") -> DynamicValue.Record("v" -> DynamicValue.int(2))
        )
        val valueMigration = DynamicMigration.record(_.addField("extra", DynamicValue.boolean(true)))
        val migration      = DynamicMigration(
          MigrationStep.MapEntries(MigrationStep.NoOp, valueMigration.step)
        )
        val migrated = migration(original)
        val reversed = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      },
      test("MapEntries with empty map returns empty map") {
        val original  = DynamicValue.Map()
        val migration = DynamicMigration(MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.NoOp))
        val result    = migration(original)

        assertTrue(result == Right(DynamicValue.Map()))
      },
      test("MapEntries fails when applied to non-Map value") {
        val original  = DynamicValue.int(42)
        val migration = DynamicMigration(MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.NoOp))
        val result    = migration(original)

        result match {
          case Left(MigrationError.TypeMismatch(_, _, _)) => assertTrue(true)
          case _                                          => assertTrue(false)
        }
      },
      test("DuplicateMapKey error can be serialized") {
        val error  = MigrationError.DuplicateMapKey(DynamicOptic.root)
        val schema = Schema[MigrationError]
        val dv     = schema.toDynamicValue(error)
        val result = schema.fromDynamicValue(dv)
        assertTrue(result == Right(error))
      }
    ),
    suite("Map type mismatch")(
      test("Map migration fails when applied to non-Map value") {
        val original  = DynamicValue.Record("field" -> DynamicValue.int(1))
        val migration = DynamicMigration(MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.NoOp))
        val result    = migration(original)

        result match {
          case Left(MigrationError.TypeMismatch(_, expected, _)) => assertTrue(expected == "Map")
          case _                                                 => assertTrue(false)
        }
      }
    ),
    suite("Builder transformElements")(
      test("transformElements applies nested migration to sequence elements") {
        val original = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            DynamicValue.Record("name" -> DynamicValue.string("item1")),
            DynamicValue.Record("name" -> DynamicValue.string("item2"))
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "items" -> MigrationStep.Sequence(
                MigrationStep.Record.empty.addField("count", DynamicValue.int(0))
              )
            )
          )
        val migration = DynamicMigration(step)
        val result    = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "items" -> DynamicValue.Sequence(
                DynamicValue.Record("name" -> DynamicValue.string("item1"), "count" -> DynamicValue.int(0)),
                DynamicValue.Record("name" -> DynamicValue.string("item2"), "count" -> DynamicValue.int(0))
              )
            )
          )
        )
      },
      test("transformElements is reversible") {
        val original = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            DynamicValue.Record("name" -> DynamicValue.string("a")),
            DynamicValue.Record("name" -> DynamicValue.string("b"))
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "items" -> MigrationStep.Sequence(
                MigrationStep.Record.empty.addField("extra", DynamicValue.boolean(true))
              )
            )
          )
        val migration = DynamicMigration(step)
        val migrated  = migration(original)
        val reversed  = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      },
      test("transformElements with nested path") {
        val original = DynamicValue.Record(
          "container" -> DynamicValue.Record(
            "items" -> DynamicValue.Sequence(
              DynamicValue.Record("value" -> DynamicValue.int(1))
            )
          )
        )
        val step = MigrationStep.Record.empty
          .nested("container") { nested =>
            nested.copy(nestedFields =
              nested.nestedFields + ("items" -> MigrationStep.Sequence(
                MigrationStep.Record.empty.addField("flag", DynamicValue.boolean(false))
              ))
            )
          }
        val migration = DynamicMigration(step)
        val result    = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "container" -> DynamicValue.Record(
                "items" -> DynamicValue.Sequence(
                  DynamicValue.Record("value" -> DynamicValue.int(1), "flag" -> DynamicValue.boolean(false))
                )
              )
            )
          )
        )
      }
    ),
    suite("Builder transformValues")(
      test("transformValues applies nested migration to map values") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.string("key1") -> DynamicValue.Record("value" -> DynamicValue.string("a")),
            DynamicValue.string("key2") -> DynamicValue.Record("value" -> DynamicValue.string("b"))
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.NoOp,
                MigrationStep.Record.empty.addField("version", DynamicValue.int(1))
              )
            )
          )
        val migration = DynamicMigration(step)
        val result    = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "data" -> DynamicValue.Map(
                DynamicValue.string("key1") -> DynamicValue.Record(
                  "value"   -> DynamicValue.string("a"),
                  "version" -> DynamicValue.int(1)
                ),
                DynamicValue.string("key2") -> DynamicValue.Record(
                  "value"   -> DynamicValue.string("b"),
                  "version" -> DynamicValue.int(1)
                )
              )
            )
          )
        )
      },
      test("transformValues is reversible") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.string("x") -> DynamicValue.Record("v" -> DynamicValue.int(10))
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.NoOp,
                MigrationStep.Record.empty.addField("added", DynamicValue.string("new"))
              )
            )
          )
        val migration = DynamicMigration(step)
        val migrated  = migration(original)
        val reversed  = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      }
    ),
    suite("Builder transformKeys")(
      test("transformKeys applies nested migration to map keys") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.Record("id" -> DynamicValue.string("k1")) -> DynamicValue.int(100),
            DynamicValue.Record("id" -> DynamicValue.string("k2")) -> DynamicValue.int(200)
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.Record.empty.renameField("id", "key"),
                MigrationStep.NoOp
              )
            )
          )
        val migration = DynamicMigration(step)
        val result    = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "data" -> DynamicValue.Map(
                DynamicValue.Record("key" -> DynamicValue.string("k1")) -> DynamicValue.int(100),
                DynamicValue.Record("key" -> DynamicValue.string("k2")) -> DynamicValue.int(200)
              )
            )
          )
        )
      },
      test("transformKeys is reversible") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.Record("name" -> DynamicValue.string("a")) -> DynamicValue.int(1)
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.Record.empty.renameField("name", "label"),
                MigrationStep.NoOp
              )
            )
          )
        val migration = DynamicMigration(step)
        val migrated  = migration(original)
        val reversed  = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      }
    ),
    suite("MigrationBuilder collection methods")(
      test("transformElements via MigrationBuilder internal method") {
        val builder = MigrationBuilder[ContainerWithItemsV1, ContainerWithItemsV2, Nothing, Nothing](
          ContainerWithItemsV1.schema,
          ContainerWithItemsV2.schema,
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
        val updatedBuilder = builder.transformElements("items")(_.addField("count", DynamicValue.int(0)))
        val migration      = updatedBuilder.toDynamicMigration

        val original = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            DynamicValue.Record("name" -> DynamicValue.string("item1"))
          )
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "items" -> DynamicValue.Sequence(
                DynamicValue.Record("name" -> DynamicValue.string("item1"), "count" -> DynamicValue.int(0))
              )
            )
          )
        )
      },
      test("transformValues via MigrationBuilder internal method") {
        val builder = MigrationBuilder[ContainerWithMapV1, ContainerWithMapV2, Nothing, Nothing](
          ContainerWithMapV1.schema,
          ContainerWithMapV2.schema,
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
        val updatedBuilder = builder.transformValues("data")(_.addField("version", DynamicValue.int(1)))
        val migration      = updatedBuilder.toDynamicMigration

        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.string("key1") -> DynamicValue.Record("value" -> DynamicValue.string("a"))
          )
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "data" -> DynamicValue.Map(
                DynamicValue.string("key1") -> DynamicValue.Record(
                  "value"   -> DynamicValue.string("a"),
                  "version" -> DynamicValue.int(1)
                )
              )
            )
          )
        )
      },
      test("transformKeys via MigrationBuilder internal method") {
        case class KeyV1(id: String)
        case class KeyV2(key: String)
        case class ContainerK1(data: scala.collection.immutable.Map[KeyV1, Int])
        case class ContainerK2(data: scala.collection.immutable.Map[KeyV2, Int])

        val builder = MigrationBuilder[ContainerK1, ContainerK2, Nothing, Nothing](
          Schema.derived[ContainerK1],
          Schema.derived[ContainerK2],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
        val updatedBuilder = builder.transformKeys("data")(_.renameField("id", "key"))
        val migration      = updatedBuilder.toDynamicMigration

        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.Record("id" -> DynamicValue.string("k1")) -> DynamicValue.int(42)
          )
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "data" -> DynamicValue.Map(
                DynamicValue.Record("key" -> DynamicValue.string("k1")) -> DynamicValue.int(42)
              )
            )
          )
        )
      }
    ),
    suite("Compose Sequence and MapEntries steps")(
      test("andThen composes two Sequence migrations") {
        val m1       = DynamicMigration(MigrationStep.Sequence(MigrationStep.Record.empty.addField("a", DynamicValue.int(1))))
        val m2       = DynamicMigration(MigrationStep.Sequence(MigrationStep.Record.empty.addField("b", DynamicValue.int(2))))
        val composed = m1.andThen(m2)

        val original = DynamicValue.Sequence(DynamicValue.Record("x" -> DynamicValue.string("test")))
        val result   = composed(original)

        result match {
          case Right(DynamicValue.Sequence(elems)) =>
            val fields = elems.head.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              fields.contains("a"),
              fields.contains("b"),
              fields.contains("x")
            )
          case _ => assertTrue(false)
        }
      },
      test("andThen composes two MapEntries migrations") {
        val m1 = DynamicMigration(
          MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.Record.empty.addField("v1", DynamicValue.int(1)))
        )
        val m2 = DynamicMigration(
          MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.Record.empty.addField("v2", DynamicValue.int(2)))
        )
        val composed = m1.andThen(m2)

        val original = DynamicValue.Map(
          DynamicValue.string("k") -> DynamicValue.Record("existing" -> DynamicValue.string("x"))
        )
        val result = composed(original)

        result match {
          case Right(DynamicValue.Map(entries)) =>
            val (_, value) = entries.head
            val fields     = value.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              fields.contains("v1"),
              fields.contains("v2"),
              fields.contains("existing")
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Sequence transform error propagation")(
      test("Sequence transform stops at first failure and propagates error") {
        val failingFirst = DynamicValueTransform.sequence(
          DynamicValueTransform.stringAppend(" suffix"),
          DynamicValueTransform.identity,
          DynamicValueTransform.stringPrepend("prefix ")
        )
        val result = failingFirst(DynamicValue.int(10))
        assertTrue(result.isLeft)
      }
    ),
    suite("Map key migration error propagation")(
      test("Map migration fails on second entry and propagates to third") {
        val original = DynamicValue.Map(
          DynamicValue.Record("id" -> DynamicValue.string("a"))    -> DynamicValue.int(1),
          DynamicValue.Record("wrong" -> DynamicValue.string("b")) -> DynamicValue.int(2),
          DynamicValue.Record("id" -> DynamicValue.string("c"))    -> DynamicValue.int(3)
        )
        val keyMigration = DynamicMigration.record(_.removeField("id", DynamicValue.Null))
        val migration    = DynamicMigration(MigrationStep.MapEntries(keyMigration.step, MigrationStep.NoOp))
        val result       = migration(original)

        assertTrue(result.isLeft)
      }
    ),
    suite("Sequence element error propagation")(
      test("Sequence element error propagates on later element failure") {
        val original = DynamicValue.Sequence(
          DynamicValue.Record("field" -> DynamicValue.int(1)),
          DynamicValue.Record("field" -> DynamicValue.int(2)),
          DynamicValue.Record("wrong" -> DynamicValue.int(3))
        )
        val migration = DynamicMigration.sequence(
          DynamicMigration.record(_.removeField("field", DynamicValue.Null))
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(path, "field")) =>
            assertTrue(path.toScalaString.contains(".at(2)"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Map entry error propagation")(
      test("Map entry error propagates on second entry failure") {
        val original = DynamicValue.Map(
          DynamicValue.string("key1") -> DynamicValue.Record("required" -> DynamicValue.int(1)),
          DynamicValue.string("key2") -> DynamicValue.Record("other" -> DynamicValue.int(2))
        )
        val migration = DynamicMigration(
          MigrationStep.MapEntries(
            MigrationStep.NoOp,
            MigrationStep.Record.empty.withFieldAction(FieldAction.Remove("required", DynamicValue.Null))
          )
        )
        val result = migration(original)

        assertTrue(result.isLeft)
      }
    )
  )
}
