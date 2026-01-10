package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Test specification for the DynamicMigration migration system.
 * 
 * Tests cover:
 * - Individual migration actions
 * - Laws (Identity, Associativity, Reversibility)
 * - Complex migration scenarios
 */
object MigrationSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationSpec")(
      suite("MigrationAction")(
        suite("RenameField")(
          test("renames a field in a record") {
            val action = MigrationAction.RenameField("oldName", "newName")
            val record = DynamicValue.Record(Vector(
              ("oldName", DynamicValue.Primitive(PrimitiveValue.Int(42))),
              ("other", DynamicValue.Primitive(PrimitiveValue.String("test")))
            ))
            
            val result = action.apply(record)
            val hasNewName = result.map {
              case DynamicValue.Record(fields) => fields.exists(_._1 == "newName")
              case _ => false
            }
            
            assert(result)(isRight) && assert(hasNewName)(isRight(isTrue)) && assert(action.reverse)(isRight)
          },
          test("is fully reversible") {
            val action = MigrationAction.RenameField("a", "b")
            val reverse = action.reverse
            
            assert(reverse)(isRight) &&
            assert(reverse.map(_.asInstanceOf[MigrationAction.RenameField]))(
              isRight(equalTo(MigrationAction.RenameField("b", "a")))
            )
          }
        ),
        suite("DropField")(
          test("removes a field from a record") {
            val action = MigrationAction.DropField("toRemove")
            val record = DynamicValue.Record(Vector(
              ("toRemove", DynamicValue.Primitive(PrimitiveValue.Int(1))),
              ("keep", DynamicValue.Primitive(PrimitiveValue.Int(2)))
            ))
            
            val result = action.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) => fields.size
              case _ => -1
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
            val record = DynamicValue.Record(Vector(
              ("existing", DynamicValue.Primitive(PrimitiveValue.Int(1)))
            ))
            
            val result = action.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) => fields.exists(_._1 == "newField")
              case _ => false
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
            val record = DynamicValue.Record(Vector(
              ("field", DynamicValue.Primitive(PrimitiveValue.Int(42)))
            ))
            
            val result = action.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) =>
                fields.find(_._1 == "field").exists {
                  case (_, DynamicValue.Variant("Some", _)) => true
                  case _ => false
                }
              case _ => false
            })(isRight(isTrue))
          }
        ),
        suite("Mandate")(
          test("extracts value from Some") {
            val action = MigrationAction.Mandate("field", DynamicValue.Primitive(PrimitiveValue.Int(0)))
            val record = DynamicValue.Record(Vector(
              ("field", DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42))))
            ))
            
            val result = action.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) =>
                fields.find(_._1 == "field").exists {
                  case (_, DynamicValue.Primitive(PrimitiveValue.Int(42))) => true
                  case _ => false
                }
              case _ => false
            })(isRight(isTrue))
          },
          test("uses default for None") {
            val action = MigrationAction.Mandate("field", DynamicValue.Primitive(PrimitiveValue.Int(999)))
            val record = DynamicValue.Record(Vector(
              ("field", DynamicValue.Variant("None", DynamicValue.Record(Vector.empty)))
            ))
            
            val result = action.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) =>
                fields.find(_._1 == "field").exists {
                  case (_, DynamicValue.Primitive(PrimitiveValue.Int(999))) => true
                  case _ => false
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
            val action = MigrationAction.RenameCase("OldCase", "NewCase")
            val variant = DynamicValue.Variant("OldCase", DynamicValue.Primitive(PrimitiveValue.Int(1)))
            
            val result = action.apply(variant)
            assert(result)(isRight(equalTo(
              DynamicValue.Variant("NewCase", DynamicValue.Primitive(PrimitiveValue.Int(1)))
            )))
          },
          test("is fully reversible") {
            val action = MigrationAction.RenameCase("A", "B")
            assert(action.reverse)(isRight(equalTo(MigrationAction.RenameCase("B", "A"))))
          }
        ),
        suite("RemoveCase")(
          test("errors when encountering removed case") {
            val action = MigrationAction.RemoveCase("RemovedCase")
            val variant = DynamicValue.Variant("RemovedCase", DynamicValue.Primitive(PrimitiveValue.Int(1)))
            
            assert(action.apply(variant))(isLeft)
          },
          test("passes through other cases") {
            val action = MigrationAction.RemoveCase("RemovedCase")
            val variant = DynamicValue.Variant("OtherCase", DynamicValue.Primitive(PrimitiveValue.Int(1)))
            
            assert(action.apply(variant))(isRight(equalTo(variant)))
          },
          test("is not reversible") {
            val action = MigrationAction.RemoveCase("case")
            assert(action.reverse)(isLeft)
          }
        )
      ),
      suite("DynamicMigration")(
        suite("Composition")(
          test("empty migration is identity") {
            val record = DynamicValue.Record(Vector(
              ("field", DynamicValue.Primitive(PrimitiveValue.Int(1)))
            ))
            
            assert(DynamicMigration.empty.apply(record))(isRight(equalTo(record)))
          },
          test("composes actions in sequence") {
            val migration = DynamicMigration.fromActions(
              MigrationAction.RenameField("a", "b"),
              MigrationAction.AddField("c", DynamicValue.Primitive(PrimitiveValue.Int(0)))
            )
            val record = DynamicValue.Record(Vector(
              ("a", DynamicValue.Primitive(PrimitiveValue.String("test")))
            ))
            
            val result = migration.apply(record)
            assert(result)(isRight) &&
            assert(result.map {
              case DynamicValue.Record(fields) =>
                fields.exists(_._1 == "b") && fields.exists(_._1 == "c")
              case _ => false
            })(isRight(isTrue))
          },
          test("associativity law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
            val m1 = DynamicMigration.single(MigrationAction.AddField("a", DynamicValue.Primitive(PrimitiveValue.Int(1))))
            val m2 = DynamicMigration.single(MigrationAction.AddField("b", DynamicValue.Primitive(PrimitiveValue.Int(2))))
            val m3 = DynamicMigration.single(MigrationAction.AddField("c", DynamicValue.Primitive(PrimitiveValue.Int(3))))
            
            val left = (m1 ++ m2) ++ m3
            val right = m1 ++ (m2 ++ m3)
            
            assert(left.actions)(equalTo(right.actions))
          },
          test("identity law: empty ++ m == m && m ++ empty == m") {
            val m = DynamicMigration.single(MigrationAction.RenameField("a", "b"))
            
            assert((DynamicMigration.empty ++ m).actions)(equalTo(m.actions)) &&
            assert((m ++ DynamicMigration.empty).actions)(equalTo(m.actions))
          }
        ),
        suite("Reversibility")(
          test("reversible migration round-trips correctly") {
            val migration = DynamicMigration.single(MigrationAction.RenameField("old", "new"))
            val record = DynamicValue.Record(Vector(
              ("old", DynamicValue.Primitive(PrimitiveValue.String("value")))
            ))
            
            val result = for {
              reversed <- migration.reverse
              migrated <- migration.apply(record)
              restored <- reversed.apply(migrated)
            } yield restored
            
            assert(result)(isRight(equalTo(record)))
          },
          test("identity law: m.reverse.reverse == Right(m)") {
            val m = DynamicMigration.fromActions(
              MigrationAction.RenameField("a", "b"),
              MigrationAction.RenameCase("X", "Y")
            )
            
            val result = m.reverse.flatMap(_.reverse)
            assert(result.map(_.actions))(isRight(equalTo(m.actions)))
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
          
          implicit val personSchema: Schema[Person] = Schema.derived
          implicit val personV2Schema: Schema[PersonV2] = Schema.derived
          
          val migration = Migration[Person, PersonV2](
            DynamicMigration.single(MigrationAction.RenameField("name", "fullName"))
          )
          
          val person = Person("Alice", 30)
          val result = migration.apply(person)
          
          assert(result)(isRight) &&
          assert(result.map(_.fullName))(isRight(equalTo("Alice"))) &&
          assert(result.map(_.age))(isRight(equalTo(30)))
        }
      )
    )
}

