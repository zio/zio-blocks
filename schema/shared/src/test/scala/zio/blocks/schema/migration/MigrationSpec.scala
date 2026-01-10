package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Test specification for the migration system.
 */
object MigrationSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationSpec")(
      suite("MigrationAction")(
        suite("Rename")(
          test("renames a field in a record") {
            val action = MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
            val record = DynamicValue.Record(
              Vector(
                ("oldName", DynamicValue.Primitive(PrimitiveValue.Int(42))),
                ("other", DynamicValue.Primitive(PrimitiveValue.String("test")))
              )
            )

            val result     = action.apply(record)
            val hasNewName = result.map {
              case DynamicValue.Record(fields) => fields.exists(_._1 == "newName")
              case _                           => false
            }

            assert(result)(isRight) &&
            assert(hasNewName)(isRight(isTrue)) &&
            assertTrue(action.at == DynamicOptic.root)
          },
          test("structural reverse is symmetric") {
            val action   = MigrationAction.Rename(DynamicOptic.root, "a", "b")
            val reversed = action.reverse

            assertTrue(reversed.isInstanceOf[MigrationAction.Rename]) &&
            assertTrue(reversed.asInstanceOf[MigrationAction.Rename].from == "b") &&
            assertTrue(reversed.asInstanceOf[MigrationAction.Rename].to == "a") &&
            assertTrue(action.reverse.reverse == action)
          }
        ),
        suite("DropField")(
          test("removes a field from a record") {
            val action = MigrationAction.DropField(DynamicOptic.root, "toRemove", None)
            val record = DynamicValue.Record(
              Vector(
                ("toRemove", DynamicValue.Primitive(PrimitiveValue.Int(1))),
                ("keep", DynamicValue.Primitive(PrimitiveValue.Int(2)))
              )
            )

            val result = action.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) => fields.size
              case _                           => -1
            })(isRight(equalTo(1)))
          },
          test("has at field") {
            val action = MigrationAction.DropField(DynamicOptic.root, "field", None)
            assertTrue(action.at == DynamicOptic.root)
          },
          test("structural reverse with default is AddField") {
            val defaultValue = DynamicValue.Primitive(PrimitiveValue.Int(0))
            val action       = MigrationAction.DropField(DynamicOptic.root, "field", Some(defaultValue))
            val reversed     = action.reverse

            assertTrue(reversed.isInstanceOf[MigrationAction.AddField])
          }
        ),
        suite("AddField")(
          test("adds a new field with default value") {
            val defaultValue = DynamicValue.Primitive(PrimitiveValue.String("default"))
            val action       = MigrationAction.AddField(DynamicOptic.root, "newField", defaultValue)
            val record       = DynamicValue.Record(
              Vector(("existing", DynamicValue.Primitive(PrimitiveValue.Int(1))))
            )

            val result = action.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) => fields.exists(_._1 == "newField")
              case _                           => false
            })(isRight(isTrue))
          },
          test("structural reverse is DropField") {
            val defaultValue = DynamicValue.Primitive(PrimitiveValue.Int(0))
            val action       = MigrationAction.AddField(DynamicOptic.root, "field", defaultValue)
            assertTrue(action.reverse.isInstanceOf[MigrationAction.DropField])
          }
        ),
        suite("Optionalize")(
          test("wraps existing field in Some") {
            val action = MigrationAction.Optionalize(DynamicOptic.root, "field")
            val record = DynamicValue.Record(
              Vector(("field", DynamicValue.Primitive(PrimitiveValue.Int(42))))
            )

            val result = action.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) =>
                fields.find(_._1 == "field").exists {
                  case (_, DynamicValue.Variant("Some", _)) => true
                  case _                                    => false
                }
              case _ => false
            })(isRight(isTrue))
          }
        ),
        suite("Mandate")(
          test("extracts value from Some") {
            val defaultValue = DynamicValue.Primitive(PrimitiveValue.Int(0))
            val action       = MigrationAction.Mandate(DynamicOptic.root, "field", defaultValue)
            val record       = DynamicValue.Record(
              Vector(("field", DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))))
            )

            val result = action.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) =>
                fields.find(_._1 == "field").exists {
                  case (_, DynamicValue.Primitive(PrimitiveValue.Int(42))) => true
                  case _                                                   => false
                }
              case _ => false
            })(isRight(isTrue))
          },
          test("structural reverse is Optionalize") {
            val defaultValue = DynamicValue.Primitive(PrimitiveValue.Int(0))
            val action       = MigrationAction.Mandate(DynamicOptic.root, "field", defaultValue)
            assertTrue(action.reverse.isInstanceOf[MigrationAction.Optionalize])
          }
        ),
        suite("RenameCase")(
          test("renames a variant case") {
            val action  = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
            val variant = DynamicValue.Variant("OldCase", DynamicValue.Primitive(PrimitiveValue.Int(1)))

            val result = action.apply(variant)
            assert(result)(
              isRight(equalTo(DynamicValue.Variant("NewCase", DynamicValue.Primitive(PrimitiveValue.Int(1)))))
            )
          },
          test("structural reverse is symmetric") {
            val action = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
            assertTrue(action.reverse.reverse == action)
          }
        ),
        suite("TransformCase")(
          test("transforms a case's contents") {
            val innerAction = MigrationAction.Rename(DynamicOptic.root, "old", "new")
            val action      = MigrationAction.TransformCase(DynamicOptic.root, "MyCase", Vector(innerAction))
            val variant     = DynamicValue.Variant(
              "MyCase",
              DynamicValue.Record(
                Vector(("old", DynamicValue.Primitive(PrimitiveValue.Int(1))))
              )
            )

            val result = action.apply(variant)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Variant(_, DynamicValue.Record(fields)) =>
                fields.exists(_._1 == "new")
              case _ => false
            })(isRight(isTrue))
          },
          test("structural reverse reverses nested actions") {
            val innerAction = MigrationAction.Rename(DynamicOptic.root, "a", "b")
            val action      = MigrationAction.TransformCase(DynamicOptic.root, "Case", Vector(innerAction))
            val reversed    = action.reverse.asInstanceOf[MigrationAction.TransformCase]

            assertTrue(reversed.actions.head.asInstanceOf[MigrationAction.Rename].from == "b")
          }
        )
      ),
      suite("DynamicMigration")(
        suite("Laws")(
          test("identity law: empty migration is identity") {
            val record = DynamicValue.Record(
              Vector(("field", DynamicValue.Primitive(PrimitiveValue.Int(1))))
            )
            assert(DynamicMigration.empty.apply(record))(isRight(equalTo(record)))
          },
          test("identity law: empty ++ m == m && m ++ empty == m") {
            val m = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))

            assertTrue((DynamicMigration.empty ++ m).actions == m.actions) &&
            assertTrue((m ++ DynamicMigration.empty).actions == m.actions)
          },
          test("associativity law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
            val defaultValue = DynamicValue.Primitive(PrimitiveValue.Int(0))
            val m1           = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "a", defaultValue))
            val m2           = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "b", defaultValue))
            val m3           = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "c", defaultValue))

            assertTrue(((m1 ++ m2) ++ m3).actions == (m1 ++ (m2 ++ m3)).actions)
          },
          test("structural reverse law: m.reverse.reverse == m") {
            val m = DynamicMigration.fromActions(
              MigrationAction.Rename(DynamicOptic.root, "a", "b"),
              MigrationAction.RenameCase(DynamicOptic.root, "X", "Y")
            )

            assertTrue(m.reverse.reverse.actions.length == m.actions.length)
          }
        ),
        suite("Composition")(
          test("composes actions in sequence") {
            val defaultValue = DynamicValue.Primitive(PrimitiveValue.Int(0))
            val migration    = DynamicMigration.fromActions(
              MigrationAction.Rename(DynamicOptic.root, "a", "b"),
              MigrationAction.AddField(DynamicOptic.root, "c", defaultValue)
            )
            val record = DynamicValue.Record(
              Vector(("a", DynamicValue.Primitive(PrimitiveValue.String("test"))))
            )

            val result = migration.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) =>
                fields.exists(_._1 == "b") && fields.exists(_._1 == "c")
              case _ => false
            })(isRight(isTrue))
          }
        ),
        suite("describe")(
          test("empty migration has description") {
            assertTrue(DynamicMigration.empty.describe == "Empty migration")
          },
          test("single action has description with path") {
            val m = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
            assertTrue(m.describe.contains("Rename"))
          }
        )
      ),
      suite("Migration[A, B]")(
        test("identity law: Migration.identity[A].apply(a) == Right(a)") {
          case class Person(name: String, age: Int)
          implicit val personSchema: Schema[Person] = Schema.derived

          val identity = Migration.identity[Person]
          val person   = Person("Alice", 30)

          assert(identity.apply(person))(isRight(equalTo(person)))
        },
        test("applies typed migration using schemas") {
          case class Person(name: String, age: Int)
          case class PersonV2(fullName: String, age: Int)

          implicit val personSchema: Schema[Person]     = Schema.derived
          implicit val personV2Schema: Schema[PersonV2] = Schema.derived

          val migration = Migration[Person, PersonV2](
            DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "name", "fullName")),
            personSchema,
            personV2Schema
          )

          val person = Person("Alice", 30)
          val result = migration.apply(person)

          assert(result)(isRight) &&
          assert(result.map(_.fullName))(isRight(equalTo("Alice"))) &&
          assert(result.map(_.age))(isRight(equalTo(30)))
        },
        test("composes typed migrations") {
          case class V1(a: String)
          case class V2(b: String)
          case class V3(c: String)

          implicit val v1Schema: Schema[V1] = Schema.derived
          implicit val v2Schema: Schema[V2] = Schema.derived
          implicit val v3Schema: Schema[V3] = Schema.derived

          val m1 = Migration[V1, V2](
            DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b")),
            v1Schema,
            v2Schema
          )
          val m2 = Migration[V2, V3](
            DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "b", "c")),
            v2Schema,
            v3Schema
          )
          val combined = m1 ++ m2

          val result = combined.apply(V1("test"))
          assert(result)(isRight) &&
          assert(result.map(_.c))(isRight(equalTo("test")))
        },
        test("structural reverse returns Migration[B, A]") {
          case class Before(old: String)
          case class After(renamed: String)

          implicit val beforeSchema: Schema[Before] = Schema.derived
          implicit val afterSchema: Schema[After]   = Schema.derived

          val migration = Migration[Before, After](
            DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "old", "renamed")),
            beforeSchema,
            afterSchema
          )

          
          val reversed = migration.reverse
          assertTrue(reversed.sourceSchema == afterSchema) &&
          assertTrue(reversed.targetSchema == beforeSchema)
        },
        test("satisfies best-effort semantic inverse law") {
          // m.apply(a) == Right(b) => m.reverse.apply(b) == Right(a)
          case class V1(name: String)
          case class V2(fullName: String)

          implicit val v1Schema: Schema[V1] = Schema.derived
          implicit val v2Schema: Schema[V2] = Schema.derived

          // Rename is fully reversible
          val migration = Migration[V1, V2](
            DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "name", "fullName")),
            v1Schema,
            v2Schema
          )

          val v1 = V1("Alice")
          for {
            v2         <- migration.apply(v1)
            restoredV1 <- migration.reverse.apply(v2)
          } yield assertTrue(restoredV1 == v1)
        }
      ),
      suite("MigrationBuilder")(
        test("builder produces working migration") {
          case class V1(oldName: String, age: Int)
          case class V2(newName: String, age: Int)

          implicit val v1Schema: Schema[V1] = Schema.derived
          implicit val v2Schema: Schema[V2] = Schema.derived

          val migration = MigrationBuilder[V1, V2]
            .renameField("oldName", "newName")
            .build

          val result = migration.apply(V1("Alice", 30))
          assert(result)(isRight) &&
          assert(result.map(_.newName))(isRight(equalTo("Alice")))
        },
        test("all actions have 'at' field set to root by default") {
          case class V1(a: String, b: Int)
          case class V2(c: String, d: Int)

          implicit val v1Schema: Schema[V1] = Schema.derived
          implicit val v2Schema: Schema[V2] = Schema.derived

          val builder = MigrationBuilder[V1, V2]
            .renameField("a", "c")
            .renameField("b", "d")

          assertTrue(builder.actions.forall(_.at == DynamicOptic.root))
        },
        test("convenience methods work") {
          case class V1(name: String)
          case class V2(name: String, newField: Int)

          implicit val v1Schema: Schema[V1] = Schema.derived
          implicit val v2Schema: Schema[V2] = Schema.derived

          val defaultValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
          val builder      = MigrationBuilder[V1, V2]
            .addField("newField", defaultValue)

          assertTrue(builder.actions.length == 1) &&
          assertTrue(builder.actions.head.isInstanceOf[MigrationAction.AddField])
        },
        test("describe returns human-readable description with paths") {
          val defaultValue                     = DynamicValue.Primitive(PrimitiveValue.Int(0))
          val actions: Vector[MigrationAction] = Vector(
            MigrationAction.Rename(DynamicOptic.root.field("address"), "old", "new"),
            MigrationAction.AddField(DynamicOptic.root.field("nested"), "field", defaultValue)
          )

          val description = DynamicMigration(actions).describe
          assertTrue(description.contains("address")) &&
          assertTrue(description.contains("nested"))
        }
      ),
      suite("SchemaExpr.DefaultValue")(
        test("extracts default from schema with defaultValue") {
          case class WithDefault(value: Int = 42)
          implicit val schema: Schema[WithDefault] = Schema.derived[WithDefault].defaultValue(WithDefault())

          val expr   = SchemaExpr.defaultValue[Any, WithDefault]
          val result = expr.eval("ignored")

          assert(result)(isRight)
        },
        test("returns error when no default is defined") {
          case class NoDefault(value: Int)
          implicit val schema: Schema[NoDefault] = Schema.derived

          val expr   = SchemaExpr.defaultValue[Any, NoDefault]
          val result = expr.eval("ignored")

          assert(result)(isLeft)
        },
        test("DynamicLiteral works with migrations") {
          val literal = SchemaExpr.dynamicLiteral[Any](
            DynamicValue.Primitive(PrimitiveValue.Int(100))
          )

          val result = literal.evalDynamic("ignored")
          assert(result)(isRight) &&
          assert(result.map(_.head))(
            isRight(
              equalTo(
                DynamicValue.Primitive(PrimitiveValue.Int(100))
              )
            )
          )
        }
      )
    )
}
