package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.migration.MigrationAction.DynamicOpticOps

/**
 * Test specification for the DynamicMigration migration system.
 *
 * Tests cover:
 *   - Individual migration actions
 *   - Laws (Identity, Associativity, Reversibility)
 *   - Complex migration scenarios
 */
object MigrationSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationSpec")(
      suite("MigrationAction")(
        suite("Rename")(
          test("renames a field in a record") {
            val action = MigrationAction.Rename("oldName", "newName")
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

            assert(result)(isRight) && assert(hasNewName)(isRight(isTrue)) && assert(action.reverse)(isRight)
          },
          test("is fully reversible") {
            val action  = MigrationAction.Rename("a", "b")
            val reverse = action.reverse

            assert(reverse)(isRight) &&
            assert(reverse.map(_.asInstanceOf[MigrationAction.Rename]))(
              isRight(equalTo(MigrationAction.Rename("b", "a")))
            )
          }
        ),
        suite("DropField")(
          test("removes a field from a record") {
            val action = MigrationAction.DropField("toRemove")
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
          test("is not reversible") {
            val action = MigrationAction.DropField("field")
            assert(action.reverse)(isLeft)
          }
        ),
        suite("AddField")(
          test("adds a new field with default value") {
            val action = MigrationAction.AddField("newField", DynamicValue.Primitive(PrimitiveValue.String("default")))
            val record = DynamicValue.Record(
              Vector(
                ("existing", DynamicValue.Primitive(PrimitiveValue.Int(1)))
              )
            )

            val result = action.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) => fields.exists(_._1 == "newField")
              case _                           => false
            })(isRight(isTrue))
          },
          test("reverse is DropField") {
            val action = MigrationAction.AddField("field", DynamicValue.Primitive(PrimitiveValue.Int(0)))
            assert(action.reverse)(isRight(equalTo(MigrationAction.DropField("field"))))
          }
        ),
        suite("Optionalize")(
          test("wraps existing field in Some") {
            val action = MigrationAction.Optionalize("field")
            val record = DynamicValue.Record(
              Vector(
                ("field", DynamicValue.Primitive(PrimitiveValue.Int(42)))
              )
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
            val action = MigrationAction.Mandate("field", DynamicValue.Primitive(PrimitiveValue.Int(0)))
            val record = DynamicValue.Record(
              Vector(
                ("field", DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42))))
              )
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
          test("uses default for None") {
            val action = MigrationAction.Mandate("field", DynamicValue.Primitive(PrimitiveValue.Int(999)))
            val record = DynamicValue.Record(
              Vector(
                ("field", DynamicValue.Variant("None", DynamicValue.Record(Vector.empty)))
              )
            )

            val result = action.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) =>
                fields.find(_._1 == "field").exists {
                  case (_, DynamicValue.Primitive(PrimitiveValue.Int(999))) => true
                  case _                                                    => false
                }
              case _ => false
            })(isRight(isTrue))
          },
          test("reverse is Optionalize") {
            val action = MigrationAction.Mandate("field", DynamicValue.Primitive(PrimitiveValue.Int(0)))
            assert(action.reverse)(isRight(equalTo(MigrationAction.Optionalize("field"))))
          }
        ),
        suite("RenameCase")(
          test("renames a variant case") {
            val action  = MigrationAction.RenameCase("OldCase", "NewCase")
            val variant = DynamicValue.Variant("OldCase", DynamicValue.Primitive(PrimitiveValue.Int(1)))

            val result = action.apply(variant)
            assert(result)(
              isRight(
                equalTo(
                  DynamicValue.Variant("NewCase", DynamicValue.Primitive(PrimitiveValue.Int(1)))
                )
              )
            )
          },
          test("is fully reversible") {
            val action = MigrationAction.RenameCase("A", "B")
            assert(action.reverse)(isRight(equalTo(MigrationAction.RenameCase("B", "A"))))
          }
        ),
        suite("RemoveCase")(
          test("errors when encountering removed case") {
            val action  = MigrationAction.RemoveCase("RemovedCase")
            val variant = DynamicValue.Variant("RemovedCase", DynamicValue.Primitive(PrimitiveValue.Int(1)))

            assert(action.apply(variant))(isLeft)
          },
          test("passes through other cases") {
            val action  = MigrationAction.RemoveCase("RemovedCase")
            val variant = DynamicValue.Variant("OtherCase", DynamicValue.Primitive(PrimitiveValue.Int(1)))

            assert(action.apply(variant))(isRight(equalTo(variant)))
          },
          test("is not reversible") {
            val action = MigrationAction.RemoveCase("case")
            assert(action.reverse)(isLeft)
          }
        ),
        suite("Deep Paths")(
          test("renames a nested field") {
            val action = MigrationAction.Rename(
              DynamicOptic.root.field("address").field("street"),
              "streetName"
            )
            val record = DynamicValue.Record(
              Vector(
                (
                  "address",
                  DynamicValue.Record(
                    Vector(
                      ("street", DynamicValue.Primitive(PrimitiveValue.String("Main St")))
                    )
                  )
                )
              )
            )

            val result  = action.apply(record)
            val updated = result.flatMap(DynamicOptic.root.field("address").field("streetName").getDV)

            assert(result)(isRight) &&
            assert(updated)(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.String("Main St")))))
          }
        ),
        suite("Join")(
          test("joins multiple fields into one") {
            val sources = Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b"))
            val target  = DynamicOptic.root.field("c")
            val action  = MigrationAction.Join(
              target,
              sources,
              SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("joined")), Schema.dynamic)
            )
            val record = DynamicValue.Record(
              Vector(
                "a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
                "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
              )
            )

            val result = action.apply(record)
            val joined = result.flatMap(target.getDV)

            assert(joined)(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.String("joined")))))
          },
          test("fails if source field is missing") {
            val sources = Vector(DynamicOptic.root.field("missing"))
            val action  = MigrationAction.Join(
              DynamicOptic.root.field("target"),
              sources,
              SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)), Schema.dynamic)
            )
            val record = DynamicValue.Record(Vector.empty)

            assert(action.apply(record))(isLeft)
          }
        ),
        suite("Split")(
          test("splits one field into multiple") {
            val targetA = DynamicOptic.root.field("a")
            val targetB = DynamicOptic.root.field("b")
            val action  = MigrationAction.Split(
              DynamicOptic.root.field("source"),
              Vector(targetA, targetB),
              SchemaExpr
                .Literal(
                  Vector(
                    DynamicValue.Primitive(PrimitiveValue.Int(1)),
                    DynamicValue.Primitive(PrimitiveValue.Int(2))
                  ),
                  Schema.vector[DynamicValue]
                )
                .asInstanceOf[SchemaExpr[DynamicValue, DynamicValue]]
            )
            val record =
              DynamicValue.Record(Vector("source" -> DynamicValue.Primitive(PrimitiveValue.Int(0))))

            val result = action.apply(record)
            val valA   = result.flatMap(targetA.getDV)
            val valB   = result.flatMap(targetB.getDV)

            assert(valA)(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(1))))) &&
            assert(valB)(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(2)))))
          }
        ),
        suite("TransformValue")(
          test("transforms a value at a path") {
            val action = MigrationAction.TransformValue(
              DynamicOptic.root.field("age"),
              SchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(31)), Schema.dynamic)
            )
            val record =
              DynamicValue.Record(Vector("age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))))

            val result       = action.apply(record)
            val updatedValue = result.flatMap(DynamicOptic.root.field("age").getDV)

            assert(updatedValue)(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(31)))))
          }
        )
      ),
      suite("DynamicMigration")(
        suite("Composition")(
          test("empty migration is identity") {
            val record = DynamicValue.Record(
              Vector(
                ("field", DynamicValue.Primitive(PrimitiveValue.Int(1)))
              )
            )

            assert(DynamicMigration.empty.apply(record))(isRight(equalTo(record)))
          },
          test("composes actions in sequence") {
            val migration = DynamicMigration.fromActions(
              MigrationAction.Rename("a", "b"),
              MigrationAction.AddField("c", DynamicValue.Primitive(PrimitiveValue.Int(0)))
            )
            val record = DynamicValue.Record(
              Vector(
                ("a", DynamicValue.Primitive(PrimitiveValue.String("test")))
              )
            )

            val result = migration.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) =>
                fields.exists(_._1 == "b") && fields.exists(_._1 == "c")
              case _ => false
            })(isRight(isTrue))
          },
          test("associativity law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
            val m1 =
              DynamicMigration.single(MigrationAction.AddField("a", DynamicValue.Primitive(PrimitiveValue.Int(1))))
            val m2 =
              DynamicMigration.single(MigrationAction.AddField("b", DynamicValue.Primitive(PrimitiveValue.Int(2))))
            val m3 =
              DynamicMigration.single(MigrationAction.AddField("c", DynamicValue.Primitive(PrimitiveValue.Int(3))))

            val left  = (m1 ++ m2) ++ m3
            val right = m1 ++ (m2 ++ m3)

            assert(left.actions)(equalTo(right.actions))
          },
          test("identity law: empty ++ m == m && m ++ empty == m") {
            val m = DynamicMigration.single(MigrationAction.Rename("a", "b"))

            assert((DynamicMigration.empty ++ m).actions)(equalTo(m.actions)) &&
            assert((m ++ DynamicMigration.empty).actions)(equalTo(m.actions))
          }
        ),
        suite("Reversibility")(
          test("reversible migration round-trips correctly") {
            val migration = DynamicMigration.single(MigrationAction.Rename("old", "new"))
            val record    = DynamicValue.Record(
              Vector(
                ("old", DynamicValue.Primitive(PrimitiveValue.String("value")))
              )
            )

            val result = for {
              reversed <- migration.reverse
              migrated <- migration.apply(record)
              restored <- reversed.apply(migrated)
            } yield restored

            assert(result)(isRight(equalTo(record)))
          },
          test("identity law: m.reverse.reverse == Right(m)") {
            val m = DynamicMigration.fromActions(
              MigrationAction.Rename("a", "b"),
              MigrationAction.RenameCase("X", "Y")
            )

            val result = m.reverse.flatMap(_.reverse)
            assert(result.map(_.actions))(isRight(equalTo(m.actions)))
          },
          test("structural reverse law: (m1 ++ m2).reverse == m2.reverse ++ m1.reverse") {
            val m1 = DynamicMigration.single(MigrationAction.Rename("a", "b"))
            val m2 = DynamicMigration.single(MigrationAction.Rename("c", "d"))

            val left  = (m1 ++ m2).reverse
            val right = for {
              r1 <- m1.reverse
              r2 <- m2.reverse
            } yield r2 ++ r1

            assert(left.map(_.actions))(equalTo(right.map(_.actions)))
          },
          test("fails to reverse non-reversible migration") {
            val migration = DynamicMigration.single(MigrationAction.DropField("field"))

            assert(migration.reverse)(isLeft) &&
            assert(migration.isReversible)(isFalse)
          }
        )
      ),
      suite("Migration[A, B]")(
        test("applies typed migration using schemas") {
          case class Person(name: String, age: Int)
          case class PersonV2(fullName: String, age: Int)

          implicit val personSchema: Schema[Person]     = Schema.derived
          implicit val personV2Schema: Schema[PersonV2] = Schema.derived

          val migration = Migration[Person, PersonV2](
            DynamicMigration.single(MigrationAction.Rename("name", "fullName"))
          )

          val person = Person("Alice", 30)
          val result = migration.apply(person)

          assert(result)(isRight) &&
          assert(result.map(_.fullName))(isRight(equalTo("Alice"))) &&
          assert(result.map(_.age))(isRight(equalTo(30)))
        },
        test("complex chaining: V1 -> V2 -> V3") {
          case class V1(a: Int)
          case class V2(a: Int, b: String)
          case class V3(a: Int, c: String)

          implicit val s1: Schema[V1] = Schema.derived
          implicit val s2: Schema[V2] = Schema.derived
          implicit val s3: Schema[V3] = Schema.derived

          val m1 = Migration.builder[V1, V2].addField("b", "init").build.toOption.get
          val m2 = Migration.builder[V2, V3].renameField("b", "c").build.toOption.get

          val v1     = V1(1)
          val result = for {
            v2 <- m1.apply(v1)
            v3 <- m2.apply(v2)
          } yield v3

          assert(result)(isRight(equalTo(V3(1, "init"))))
        },
        test("semantic inverse law: m(m.reverse(v)) == v for reversible migrations") {
          case class Data(x: Int, y: String)
          case class DataRenamed(z: Int, y: String)

          implicit val dataSchema: Schema[Data]           = Schema.derived
          implicit val renamedSchema: Schema[DataRenamed] = Schema.derived

          val migration = Migration
            .builder[Data, DataRenamed]
            .renameField("x", "z")
            .build
            .toOption
            .get

          val original = Data(42, "hello")

          val result = for {
            reversed    <- migration.reverse
            transformed <- migration.apply(original)
            restored    <- reversed.apply(transformed)
          } yield restored

          assert(result)(isRight(equalTo(original)))
        }
      )
    )
}
