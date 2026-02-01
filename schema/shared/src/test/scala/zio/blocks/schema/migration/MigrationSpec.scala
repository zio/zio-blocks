package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object MigrationSpec extends SchemaBaseSpec {

  // Helper to create DynamicValue from primitives
  private def str(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def int(i: Int): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Int(i))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("DynamicMigration")(
      test("identity migration returns value unchanged") {
        val value  = DynamicValue.Record(Chunk(("name", str("John"))))
        val result = DynamicMigrationInterpreter(DynamicMigration.identity, value)
        assertTrue(result == Right(value))
      },
      test("composition of migrations is applied in order") {
        val migration1 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "age", Resolved.Literal.int(25))
        )
        val migration2 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "active", Resolved.Literal.boolean(true))
        )
        val combined = migration1 ++ migration2

        val initial = DynamicValue.Record(Chunk(("name", str("John"))))
        val result  = DynamicMigrationInterpreter(combined, initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "age")) &&
            assertTrue(fields.exists(_._1 == "active"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationAction.AddField")(
      test("adds a field with literal default value") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "email",
          Resolved.Literal.string("default@example.com")
        )
        val migration = DynamicMigration.single(action)
        val initial   = DynamicValue.Record(Chunk(("name", str("John"))))
        val result    = DynamicMigrationInterpreter(migration, initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "email"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationAction.DropField")(
      test("removes a field from a record") {
        val action = MigrationAction.DropField(
          DynamicOptic.root,
          "temporary",
          Resolved.Fail("Cannot reverse drop")
        )
        val migration = DynamicMigration.single(action)
        val initial   = DynamicValue.Record(
          Chunk(
            ("name", str("John")),
            ("temporary", str("to-be-removed"))
          )
        )
        val result = DynamicMigrationInterpreter(migration, initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(!fields.exists(_._1 == "temporary")) &&
            assertTrue(fields.exists(_._1 == "name"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationAction.Rename")(
      test("renames a field in a record") {
        val action    = MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
        val migration = DynamicMigration.single(action)
        val initial   = DynamicValue.Record(Chunk(("oldName", str("value"))))
        val result    = DynamicMigrationInterpreter(migration, initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(!fields.exists(_._1 == "oldName")) &&
            assertTrue(fields.exists(_._1 == "newName"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationAction.Mandate")(
      test("converts None to default value") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "optional",
          Resolved.Literal.string("fallback")
        )
        val migration = DynamicMigration.single(action)
        val initial   = DynamicValue.Record(
          Chunk(("optional", DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))))
        )
        val result = DynamicMigrationInterpreter(migration, initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists { case (k, v) =>
              k == "optional" && v == str("fallback")
            })
          case _ => assertTrue(false)
        }
      },
      test("unwraps Some value") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "optional",
          Resolved.Literal.string("unused")
        )
        val migration = DynamicMigration.single(action)
        val initial   = DynamicValue.Record(
          Chunk(("optional", DynamicValue.Variant("Some", DynamicValue.Record(Chunk(("value", str("actual")))))))
        )
        val result = DynamicMigrationInterpreter(migration, initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists { case (k, v) =>
              k == "optional" && v == str("actual")
            })
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationAction.Optionalize")(
      test("wraps a value in Some") {
        val action    = MigrationAction.Optionalize(DynamicOptic.root, "required")
        val migration = DynamicMigration.single(action)
        val initial   = DynamicValue.Record(Chunk(("required", str("value"))))
        val result    = DynamicMigrationInterpreter(migration, initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            fields.find(_._1 == "required") match {
              case Some((_, DynamicValue.Variant(name, _))) =>
                assertTrue(name == "Some")
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationAction.RenameCase")(
      test("renames an enum case") {
        val action    = MigrationAction.RenameCase(DynamicOptic.root.field("status"), "Active", "Enabled")
        val migration = DynamicMigration.single(action)
        val initial   = DynamicValue.Record(
          Chunk(("status", DynamicValue.Variant("Active", DynamicValue.Record(Chunk.empty))))
        )
        val result = DynamicMigrationInterpreter(migration, initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            fields.find(_._1 == "status") match {
              case Some((_, DynamicValue.Variant(name, _))) =>
                assertTrue(name == "Enabled")
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationAction.TransformCase")(
      test("applies nested actions to case contents") {
        val nestedAction = MigrationAction.AddField(
          DynamicOptic.root,
          "nestedField",
          Resolved.Literal.string("added")
        )
        val action = MigrationAction.TransformCase(
          DynamicOptic.root.field("variant"),
          "MyCase",
          Chunk(nestedAction)
        )
        val migration = DynamicMigration.single(action)
        val initial   = DynamicValue.Record(
          Chunk(
            (
              "variant",
              DynamicValue.Variant(
                "MyCase",
                DynamicValue.Record(Chunk(("existing", str("data"))))
              )
            )
          )
        )
        val result = DynamicMigrationInterpreter(migration, initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            fields.find(_._1 == "variant") match {
              case Some((_, DynamicValue.Variant(_, inner))) =>
                inner match {
                  case DynamicValue.Record(innerFields) =>
                    assertTrue(innerFields.exists(_._1 == "nestedField")) &&
                    assertTrue(innerFields.exists(_._1 == "existing"))
                  case _ => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("skips non-matching case") {
        val nestedAction = MigrationAction.AddField(
          DynamicOptic.root,
          "nestedField",
          Resolved.Literal.string("added")
        )
        val action = MigrationAction.TransformCase(
          DynamicOptic.root.field("variant"),
          "OtherCase",
          Chunk(nestedAction)
        )
        val migration = DynamicMigration.single(action)
        val initial   = DynamicValue.Record(
          Chunk(
            (
              "variant",
              DynamicValue.Variant(
                "MyCase",
                DynamicValue.Record(Chunk(("existing", str("data"))))
              )
            )
          )
        )
        val result = DynamicMigrationInterpreter(migration, initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            fields.find(_._1 == "variant") match {
              case Some((_, DynamicValue.Variant(name, inner))) =>
                // Should be unchanged
                assertTrue(name == "MyCase") &&
                assertTrue(!inner.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "nestedField"))
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationAction.TransformElements")(
      test("transforms all elements in a sequence") {
        // Identity transform - just tests the mechanics
        val action = MigrationAction.TransformElements(
          DynamicOptic.root.field("items"),
          Resolved.Identity,
          Resolved.Identity
        )
        val migration = DynamicMigration.single(action)
        val initial   = DynamicValue.Record(
          Chunk(
            (
              "items",
              DynamicValue.Sequence(
                Chunk(str("a"), str("b"))
              )
            )
          )
        )
        val result = DynamicMigrationInterpreter(migration, initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            fields.find(_._1 == "items") match {
              case Some((_, DynamicValue.Sequence(elements))) =>
                assertTrue(elements.size == 2)
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationBuilder")(
      test("builds a migration with addField") {
        val builder = MigrationBuilder[TestPerson, TestPersonV2]
          .addFieldLiteral("email", "test@example.com")

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1)
      },
      test("builds a migration with dropField") {
        val builder = MigrationBuilder[TestPersonV2, TestPerson]
          .dropFieldNoReverse("email")

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1)
      },
      test("builds a migration with renameField") {
        val builder = MigrationBuilder[TestPerson, TestPersonV2]
          .renameField("name", "fullName")

        val migration = builder.buildPartial
        assertTrue(migration.dynamicMigration.actions.size == 1)
      }
    ),
    suite("MigrationError")(
      test("PathNotFound includes available fields") {
        val error = MigrationError.PathNotFound(
          DynamicOptic.root.field("missing"),
          Set("name", "age")
        )
        assertTrue(error.render.contains("missing")) &&
        assertTrue(error.render.contains("name")) &&
        assertTrue(error.render.contains("age"))
      },
      test("CaseNotFound includes available cases") {
        val error = MigrationError.CaseNotFound(
          DynamicOptic.root.field("status"),
          "Unknown",
          Set("Active", "Inactive")
        )
        assertTrue(error.render.contains("Unknown")) &&
        assertTrue(error.render.contains("Active"))
      }
    ),
    suite("Migration.reverse")(
      test("reverses a Rename migration") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
        )
        val reversed = migration.reverse

        assertTrue(reversed.actions.size == 1) &&
        assertTrue(reversed.actions.head match {
          case MigrationAction.Rename(_, "newName", "oldName") => true
          case _                                               => false
        })
      },
      test("reverses an AddField migration to DropField") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string("default"))
        )
        val reversed = migration.reverse

        assertTrue(reversed.actions.size == 1) &&
        assertTrue(reversed.actions.head match {
          case MigrationAction.DropField(_, "email", _) => true
          case _                                        => false
        })
      }
    ),
    suite("Resolved evaluation")(
      test("Literal evaluates to constant value") {
        val expr   = Resolved.Literal.int(42)
        val result = expr.evalDynamic(str("ignored"))
        assertTrue(result == Right(int(42)))
      },
      test("Identity passes through input") {
        val result = Resolved.Identity.evalDynamic(str("hello"))
        assertTrue(result == Right(str("hello")))
      },
      test("Convert changes Int to Long") {
        val expr   = Resolved.Convert("Int", "Long", Resolved.Identity)
        val result = expr.evalDynamic(int(42))
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))))
      },
      test("Fail returns error") {
        val expr   = Resolved.Fail("test error")
        val result = expr.evalDynamic(str("ignored"))
        assertTrue(result == Left("test error"))
      }
    ),
    suite("Serialization")(
      test("Resolved.Literal round-trips through Schema") {
        val original  = Resolved.Literal.string("test")
        val dynamic   = Resolved.schema.toDynamicValue(original)
        val roundTrip = Resolved.schema.fromDynamicValue(dynamic)
        assertTrue(roundTrip.isRight)
      }
    ),
    suite("MigrationAction.Join")(
      test("Join action structure is correct") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          Resolved.Literal.string(" "), // combiner placeholder
          Resolved.Literal.int(0)       // splitter placeholder
        )
        assertTrue(action.targetFieldName == "fullName") &&
        assertTrue(action.sourcePaths.size == 2)
      },
      test("Join reverse creates Split") {
        val join = MigrationAction.Join(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          Resolved.Literal.string("combined"),
          Resolved.Literal.string("split")
        )
        val reverse = join.reverse
        reverse match {
          case MigrationAction.Split(_, name, paths, _, _) =>
            assertTrue(name == "fullName") &&
            assertTrue(paths.size == 2)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationAction.Split")(
      test("Split action structure is correct") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          Resolved.Literal.string(" "), // splitter placeholder
          Resolved.Literal.int(0)       // combiner placeholder
        )
        assertTrue(action.sourceFieldName == "fullName") &&
        assertTrue(action.targetPaths.size == 2)
      },
      test("Split reverse creates Join") {
        val split = MigrationAction.Split(
          DynamicOptic.root,
          "fullName",
          Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          Resolved.Literal.string("split"),
          Resolved.Literal.string("combined")
        )
        val reverse = split.reverse
        reverse match {
          case MigrationAction.Join(_, name, paths, _, _) =>
            assertTrue(name == "fullName") &&
            assertTrue(paths.size == 2)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationAction.execute()")(
      test("execute() on AddField works directly") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "newField",
          Resolved.Literal.string("default")
        )
        val initial = DynamicValue.Record(Chunk(("name", str("John"))))
        val result  = action.execute(initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "newField")) &&
            assertTrue(fields.exists(_._1 == "name"))
          case _ => assertTrue(false)
        }
      },
      test("execute() on DropField removes field") {
        val action = MigrationAction.DropField(
          DynamicOptic.root,
          "toRemove",
          Resolved.Literal.string("default")
        )
        val initial = DynamicValue.Record(
          Chunk(
            ("name", str("John")),
            ("toRemove", str("gone"))
          )
        )
        val result = action.execute(initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(!fields.exists(_._1 == "toRemove")) &&
            assertTrue(fields.exists(_._1 == "name"))
          case _ => assertTrue(false)
        }
      },
      test("execute() on Rename changes field name") {
        val action  = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val initial = DynamicValue.Record(Chunk(("old", str("value"))))
        val result  = action.execute(initial)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(!fields.exists(_._1 == "old")) &&
            assertTrue(fields.exists(_._1 == "new"))
          case _ => assertTrue(false)
        }
      },
      test("execute() on RenameCase renames variant case") {
        val action  = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val initial = DynamicValue.Variant("OldCase", DynamicValue.Record(Chunk.empty))
        val result  = action.execute(initial)

        result match {
          case Right(DynamicValue.Variant(name, _)) =>
            assertTrue(name == "NewCase")
          case _ => assertTrue(false)
        }
      },
      test("execute() returns error on path not found") {
        val action  = MigrationAction.Rename(DynamicOptic.root.field("nested"), "old", "new")
        val initial = DynamicValue.Record(Chunk(("unrelated", str("value"))))
        val result  = action.execute(initial)

        assertTrue(result.isLeft)
      }
    ),
    suite("Resolved.inverse")(
      test("Convert inverse swaps from/to types") {
        val convert = Resolved.Convert("Int", "Long", Resolved.Identity)
        val inverse = convert.inverse

        inverse match {
          case Resolved.Convert(from, to, _) =>
            assertTrue(from == "Long") &&
            assertTrue(to == "Int")
          case _ => assertTrue(false)
        }
      },
      test("WrapOption inverse is UnwrapOption") {
        val wrap    = Resolved.WrapOption(Resolved.Identity)
        val inverse = wrap.inverse

        assertTrue(inverse.isInstanceOf[Resolved.UnwrapOption])
      },
      test("UnwrapOption inverse is WrapOption") {
        val unwrap  = Resolved.UnwrapOption(Resolved.Identity, Resolved.Fail("fallback"))
        val inverse = unwrap.inverse

        assertTrue(inverse.isInstanceOf[Resolved.WrapOption])
      },
      test("Identity inverse is Identity") {
        val identity = Resolved.Identity
        val inverse  = identity.inverse

        assertTrue(inverse == Resolved.Identity)
      },
      test("Literal inverse is itself") {
        val literal = Resolved.Literal.string("test")
        val inverse = literal.inverse

        assertTrue(inverse == literal)
      },
      test("Convert inverse is composable") {
        // Int -> Long -> String should invert to String -> Long -> Int
        val intToLong     = Resolved.Convert("Int", "Long", Resolved.Identity)
        val doubleInverse = intToLong.inverse.inverse

        doubleInverse match {
          case Resolved.Convert(from, to, _) =>
            assertTrue(from == "Int") &&
            assertTrue(to == "Long")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationOptimizer")(
      test("removeNoOps filters identity renames") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "field", "field"),
            MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.int(1))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1) &&
        assertTrue(optimized.actions.head.isInstanceOf[MigrationAction.AddField])
      },
      test("collapseRenames chains consecutive renames") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.Rename(DynamicOptic.root, "b", "c")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 1)
      },
      test("removeAddThenDrop removes add-drop pairs") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "temp", Resolved.Literal.int(0)),
            MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal.int(0))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.isEmpty)
      },
      test("removeDropThenAdd with different fields preserves non-overlapping") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.DropField(DynamicOptic.root, "oldField", Resolved.Fail("no reverse")),
            MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.string("newValue"))
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 2)
      },
      test("preserves non-redundant actions") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string("")),
            MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
          )
        )
        val optimized = MigrationOptimizer.optimize(migration)
        assertTrue(optimized.actions.size == 2)
      },
      test("report generates optimization stats") {
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "a", "a"),
            MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal.int(1))
          )
        )
        val report = MigrationOptimizer.report(migration)
        assertTrue(report.originalCount == 2) &&
        assertTrue(report.optimizedCount == 1) &&
        assertTrue(report.actionsRemoved == 1)
      },
      test("report render produces formatted output") {
        val migration = DynamicMigration(
          Chunk(MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal.int(1)))
        )
        val report = MigrationOptimizer.report(migration)
        assertTrue(report.render.contains("Optimization Report"))
      },
      test("report handles empty migration") {
        val migration = DynamicMigration(Chunk.empty)
        val report    = MigrationOptimizer.report(migration)
        assertTrue(report.originalCount == 0) &&
        assertTrue(report.percentReduced == 0.0)
      },
      test("optimizer is idempotent") {
        val migration = DynamicMigration(
          Chunk(MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal.int(1)))
        )
        val once  = MigrationOptimizer.optimize(migration)
        val twice = MigrationOptimizer.optimize(once)
        assertTrue(once.actions == twice.actions)
      }
    )
  )

  // Test types
  case class TestPerson(name: String, age: Int)
  object TestPerson {
    implicit val schema: Schema[TestPerson] = Schema.derived
  }

  case class TestPersonV2(name: String, age: Int, email: String)
  object TestPersonV2 {
    implicit val schema: Schema[TestPersonV2] = Schema.derived
  }
}
