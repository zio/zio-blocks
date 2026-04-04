/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.binding._
import zio.test._

object DynamicMigrationSpec extends ZIOSpecDefault {

  // Schema instances — derived here because Schema.derived cannot be called
  // from the same compilation unit that defines the macro.
  implicit val dynamicMigrationSchema: Schema[DynamicMigration] = Schema.derived
  implicit val migrationActionSchema: Schema[MigrationAction]   = Schema.derived
  implicit val migrationErrorSchema: Schema[MigrationError]     = Schema.derived
  implicit val dynamicTransformSchema: Schema[DynamicTransform] = Schema.derived

  def spec = suite("DynamicMigration")(
    suite("identity")(
      test("empty migration returns input unchanged") {
        val input     = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        val migration = DynamicMigration.empty
        assertTrue(migration(input) == Right(input))
      }
    ),
    suite("addField")(
      test("adds a field to a record") {
        val input     = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
        val default   = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val migration = DynamicMigration.addField(DynamicOptic.root.field("age"), default)

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists(_.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "age"))
        )
      },
      test("does not add field if it already exists") {
        val input = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val default   = DynamicValue.Primitive(PrimitiveValue.Int(0))
        val migration = DynamicMigration.addField(DynamicOptic.root.field("age"), default)

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists(v =>
            v.asInstanceOf[DynamicValue.Record]
              .fields
              .find(_._1 == "age")
              .exists(_._2 == DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
      }
    ),
    suite("dropField")(
      test("removes a field from a record") {
        val input = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val migration =
          DynamicMigration.dropField(DynamicOptic.root.field("age"), DynamicValue.Primitive(PrimitiveValue.Int(0)))

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists(_.asInstanceOf[DynamicValue.Record].fields.length == 1),
          result.exists(!_.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "age"))
        )
      }
    ),
    suite("rename")(
      test("renames a field in a record") {
        val input = DynamicValue.Record(
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val migration = DynamicMigration.rename(DynamicOptic.root.field("firstName"), "name")

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists(_.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "name")),
          result.exists(!_.asInstanceOf[DynamicValue.Record].fields.exists(_._1 == "firstName"))
        )
      }
    ),
    suite("transformValue")(
      test("transforms a string to uppercase") {
        val input = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("alice"))
          )
        )
        val migration = DynamicMigration.transformValue(
          DynamicOptic.root.field("name"),
          DynamicTransform.StringUpperCase
        )

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists(_.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "name").exists {
            case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "ALICE"
            case _                                                     => false
          })
        )
      }
    ),
    suite("optionalize")(
      test("wraps a value in Some") {
        val input = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
          )
        )
        val migration = DynamicMigration.optionalize(DynamicOptic.root.field("name"))

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists(_.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "name").exists {
            case (_, DynamicValue.Variant("Some", _)) => true
            case _                                    => false
          })
        )
      }
    ),
    suite("mandate")(
      test("unwraps Some to the inner value") {
        val input = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Variant(
              "Some",
              DynamicValue.Record(
                Chunk(
                  "value" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
                )
              )
            )
          )
        )
        val default   = DynamicValue.Primitive(PrimitiveValue.String(""))
        val migration = DynamicMigration.mandate(DynamicOptic.root.field("name"), default)

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists(_.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "name").exists {
            case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "Alice"
            case _                                                     => false
          })
        )
      },
      test("returns default for None") {
        val input = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Variant("None", DynamicValue.Null)
          )
        )
        val default   = DynamicValue.Primitive(PrimitiveValue.String("Unknown"))
        val migration = DynamicMigration.mandate(DynamicOptic.root.field("name"), default)

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists(_.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "name").exists {
            case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "Unknown"
            case _                                                     => false
          })
        )
      }
    ),
    suite("renameCase")(
      test("renames a case in a variant") {
        val input = DynamicValue.Variant(
          "UserCreated",
          DynamicValue.Record(
            Chunk(
              "userId" -> DynamicValue.Primitive(PrimitiveValue.String("123"))
            )
          )
        )
        val migration = DynamicMigration.renameCase(DynamicOptic.root, "UserCreated", "UserAdded")

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Variant("UserAdded", _) => true
            case _                                    => false
          }
        )
      }
    ),
    suite("transformElements")(
      test("transforms each element in a sequence") {
        val input = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("alice")),
            DynamicValue.Primitive(PrimitiveValue.String("bob"))
          )
        )
        val migration = DynamicMigration.transformElements(
          DynamicOptic.root,
          DynamicTransform.StringUpperCase
        )

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Sequence(elements) =>
              elements.forall {
                case DynamicValue.Primitive(PrimitiveValue.String(s)) => s == s.toUpperCase
                case _                                                => false
              }
            case _ => false
          }
        )
      }
    ),
    suite("composition")(
      test("composes migrations sequentially") {
        val input = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("alice"))
          )
        )

        val migration1 = DynamicMigration.addField(
          DynamicOptic.root.field("age"),
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
        val migration2 = DynamicMigration.transformValue(
          DynamicOptic.root.field("name"),
          DynamicTransform.StringUpperCase
        )

        val composed = migration1 ++ migration2
        val result   = composed(input)

        assertTrue(
          result.isRight,
          result.exists { v =>
            val record = v.asInstanceOf[DynamicValue.Record]
            record.fields.exists(_._1 == "age") &&
            record.fields.find(_._1 == "name").exists {
              case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "ALICE"
              case _                                                     => false
            }
          }
        )
      },
      test("composition is associative") {
        val input = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("alice"))))

        val m1 = DynamicMigration.addField(DynamicOptic.root.field("a"), DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val m2 = DynamicMigration.addField(DynamicOptic.root.field("b"), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val m3 = DynamicMigration.addField(DynamicOptic.root.field("c"), DynamicValue.Primitive(PrimitiveValue.Int(3)))

        val leftAssoc  = (m1 ++ m2) ++ m3
        val rightAssoc = m1 ++ (m2 ++ m3)

        val leftResult  = leftAssoc(input)
        val rightResult = rightAssoc(input)

        assertTrue(
          leftResult == rightResult
        )
      }
    ),
    suite("reverse")(
      test("reverse of reverse is identity") {
        val migration = DynamicMigration.addField(
          DynamicOptic.root.field("age"),
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )

        val doubleReverse = migration.reverse.reverse

        assertTrue(doubleReverse.actions == migration.actions)
      },
      test("reverse of addField is dropField") {
        val migration = DynamicMigration.addField(
          DynamicOptic.root.field("age"),
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )

        val reverse = migration.reverse

        assertTrue(
          reverse.actions.length == 1,
          reverse.actions.head.isInstanceOf[MigrationAction.DropField]
        )
      }
    ),
    suite("DynamicTransform")(
      test("StringToInt converts string to int") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("42"))
        val result = DynamicTransform.StringToInt(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Int(42)) => true
            case _                                              => false
          }
        )
      },
      test("IntToString converts int to string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicTransform.IntToString(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.String("42")) => true
            case _                                                   => false
          }
        )
      },
      test("NumericAdd adds two integers") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val add    = DynamicTransform.NumericAdd(DynamicValue.Primitive(PrimitiveValue.Int(5)))
        val result = add(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Int(15)) => true
            case _                                              => false
          }
        )
      },
      test("StringConcat concatenates strings") {
        val input = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("Hello")),
            DynamicValue.Primitive(PrimitiveValue.String("World"))
          )
        )
        val result = DynamicTransform.StringConcat(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.String("HelloWorld")) => true
            case _                                                           => false
          }
        )
      },
      test("StringSplit splits strings") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("Hello,World"))
        val split  = DynamicTransform.StringSplit(",")
        val result = split(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Sequence(elements) =>
              elements.length == 2 &&
              elements.forall {
                case DynamicValue.Primitive(PrimitiveValue.String(_)) => true
                case _                                                => false
              }
            case _ => false
          }
        )
      }
    ),
    suite("Schema instances")(
      test("MigrationAction has schema instance") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("name"),
          DynamicValue.Primitive(PrimitiveValue.String("default"))
        )

        val schema  = implicitly[Schema[MigrationAction]]
        val encoded = schema.toDynamicValue(action)
        val decoded = schema.fromDynamicValue(encoded)

        assertTrue(decoded.isRight)
      },
      test("DynamicMigration has schema instance") {
        val migration = DynamicMigration.addField(
          DynamicOptic.root.field("name"),
          DynamicValue.Primitive(PrimitiveValue.String("default"))
        )

        val schema  = implicitly[Schema[DynamicMigration]]
        val encoded = schema.toDynamicValue(migration)
        val decoded = schema.fromDynamicValue(encoded)

        assertTrue(decoded.isRight)
      },
      test("DynamicTransform has schema instance") {
        val transform = DynamicTransform.StringConcat

        val schema  = implicitly[Schema[DynamicTransform]]
        val encoded = schema.toDynamicValue(transform)
        val decoded = schema.fromDynamicValue(encoded)

        assertTrue(decoded.isRight)
      },
      test("MigrationError has schema instance") {
        val error = MigrationError.notFound(DynamicOptic.root, "test error")

        val schema  = implicitly[Schema[MigrationError]]
        val encoded = schema.toDynamicValue(error)
        val decoded = schema.fromDynamicValue(encoded)

        assertTrue(decoded.isRight)
      }
    ),
    suite("transformKeys")(
      test("transforms all keys in a map") {
        val input = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("key1")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("key2")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val migration = DynamicMigration.transformKeys(
          DynamicOptic.root,
          DynamicTransform.StringUpperCase
        )

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Map(entries) =>
              entries.forall { case (k, _) =>
                k match {
                  case DynamicValue.Primitive(PrimitiveValue.String(s)) => s == s.toUpperCase
                  case _                                                => false
                }
              }
            case _ => false
          }
        )
      }
    ),
    suite("transformValues")(
      test("transforms all values in a map") {
        val input = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val migration = DynamicMigration.transformValues(
          DynamicOptic.root,
          DynamicTransform.IntToString
        )

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Map(entries) =>
              entries.forall { case (_, v) =>
                v match {
                  case DynamicValue.Primitive(PrimitiveValue.String(_)) => true
                  case _                                                => false
                }
              }
            case _ => false
          }
        )
      }
    ),
    suite("transformCase")(
      test("transforms contents of a specific case") {
        val input = DynamicValue.Variant(
          "User",
          DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("alice"))
            )
          )
        )
        val migration = DynamicMigration.transformCase(
          DynamicOptic.root,
          "User",
          Chunk(MigrationAction.TransformValue(DynamicOptic.root.field("name"), DynamicTransform.StringUpperCase))
        )

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Variant("User", caseValue) =>
              caseValue match {
                case DynamicValue.Record(fields) =>
                  fields.find(_._1 == "name").exists {
                    case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "ALICE"
                    case _                                                     => false
                  }
                case _ => false
              }
            case _ => false
          }
        )
      },
      test("does not transform other cases") {
        val input = DynamicValue.Variant(
          "Admin",
          DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("alice"))
            )
          )
        )
        val migration = DynamicMigration.transformCase(
          DynamicOptic.root,
          "User",
          Chunk(MigrationAction.TransformValue(DynamicOptic.root.field("name"), DynamicTransform.StringUpperCase))
        )

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Variant("Admin", caseValue) =>
              caseValue match {
                case DynamicValue.Record(fields) =>
                  fields.find(_._1 == "name").exists {
                    case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "alice"
                    case _                                                     => false
                  }
                case _ => false
              }
            case _ => false
          }
        )
      }
    ),
    suite("nested record operations")(
      test("adds field to nested record") {
        val input = DynamicValue.Record(
          Chunk(
            "user" -> DynamicValue.Record(
              Chunk(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
              )
            )
          )
        )
        val migration = DynamicMigration.addField(
          DynamicOptic.root.field("user").field("age"),
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists { v =>
            v.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "user").exists {
              case (_, DynamicValue.Record(fields)) =>
                fields.exists(_._1 == "age")
              case _ => false
            }
          }
        )
      },
      test("transforms value in nested record") {
        val input = DynamicValue.Record(
          Chunk(
            "user" -> DynamicValue.Record(
              Chunk(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("alice"))
              )
            )
          )
        )
        val migration = DynamicMigration.transformValue(
          DynamicOptic.root.field("user").field("name"),
          DynamicTransform.StringUpperCase
        )

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists { v =>
            v.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "user").exists {
              case (_, DynamicValue.Record(fields)) =>
                fields.find(_._1 == "name").exists {
                  case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "ALICE"
                  case _                                                     => false
                }
              case _ => false
            }
          }
        )
      }
    ),
    suite("more DynamicTransform tests")(
      test("StringToLong converts string to long") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("12345678901234"))
        val result = DynamicTransform.StringToLong(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Long(12345678901234L)) => true
            case _                                                            => false
          }
        )
      },
      test("LongToString converts long to string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Long(12345678901234L))
        val result = DynamicTransform.LongToString(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.String("12345678901234")) => true
            case _                                                               => false
          }
        )
      },
      test("StringToDouble converts string to double") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("3.14159"))
        val result = DynamicTransform.StringToDouble(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Double(d)) => Math.abs(d - 3.14159) < 0.0001
            case _                                                => false
          }
        )
      },
      test("DoubleToString converts double to string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Double(3.14159))
        val result = DynamicTransform.DoubleToString(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => s.contains("3.14159")
            case _                                                => false
          }
        )
      },
      test("StringToBoolean converts valid strings") {
        val trueResult  = DynamicTransform.StringToBoolean(DynamicValue.Primitive(PrimitiveValue.String("true")))
        val falseResult = DynamicTransform.StringToBoolean(DynamicValue.Primitive(PrimitiveValue.String("false")))
        val yesResult   = DynamicTransform.StringToBoolean(DynamicValue.Primitive(PrimitiveValue.String("yes")))
        val noResult    = DynamicTransform.StringToBoolean(DynamicValue.Primitive(PrimitiveValue.String("no")))

        assertTrue(
          trueResult.exists { case DynamicValue.Primitive(PrimitiveValue.Boolean(true)) => true; case _ => false },
          falseResult.exists { case DynamicValue.Primitive(PrimitiveValue.Boolean(false)) => true; case _ => false },
          yesResult.exists { case DynamicValue.Primitive(PrimitiveValue.Boolean(true)) => true; case _ => false },
          noResult.exists { case DynamicValue.Primitive(PrimitiveValue.Boolean(false)) => true; case _ => false }
        )
      },
      test("BooleanToString converts boolean to string") {
        val trueResult  = DynamicTransform.BooleanToString(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        val falseResult = DynamicTransform.BooleanToString(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))

        assertTrue(
          trueResult.exists { case DynamicValue.Primitive(PrimitiveValue.String("true")) => true; case _ => false },
          falseResult.exists { case DynamicValue.Primitive(PrimitiveValue.String("false")) => true; case _ => false }
        )
      },
      test("IntToLong widens int to long") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicTransform.IntToLong(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Long(42L)) => true
            case _                                                => false
          }
        )
      },
      test("LongToInt narrows long to int") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Long(42L))
        val result = DynamicTransform.LongToInt(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Int(42)) => true
            case _                                              => false
          }
        )
      },
      test("FloatToDouble widens float to double") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Float(3.14f))
        val result = DynamicTransform.FloatToDouble(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Double(d)) => Math.abs(d - 3.14) < 0.01
            case _                                                => false
          }
        )
      },
      test("DoubleToFloat narrows double to float") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Double(3.14))
        val result = DynamicTransform.DoubleToFloat(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Float(f)) => Math.abs(f - 3.14f) < 0.01f
            case _                                               => false
          }
        )
      },
      test("StringLowerCase converts to lowercase") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("HELLO"))
        val result = DynamicTransform.StringLowerCase(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.String("hello")) => true
            case _                                                      => false
          }
        )
      },
      test("StringTrim trims whitespace") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("  hello  "))
        val result = DynamicTransform.StringTrim(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.String("hello")) => true
            case _                                                      => false
          }
        )
      },
      test("NumericMultiply multiplies integers") {
        val input    = DynamicValue.Primitive(PrimitiveValue.Int(5))
        val multiply = DynamicTransform.NumericMultiply(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val result   = multiply(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Int(15)) => true
            case _                                              => false
          }
        )
      },
      test("NumericDivide divides integers") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(15))
        val divide = DynamicTransform.NumericDivide(DynamicValue.Primitive(PrimitiveValue.Int(3)))
        val result = divide(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Int(5)) => true
            case _                                             => false
          }
        )
      },
      test("NumericDivide returns error for division by zero") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val divide = DynamicTransform.NumericDivide(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val result = divide(input)

        assertTrue(result.isLeft)
      },
      test("Compose applies transforms sequentially") {
        val input    = DynamicValue.Primitive(PrimitiveValue.String("42"))
        val composed = DynamicTransform.Compose(DynamicTransform.StringToInt, DynamicTransform.IntToString)
        val result   = composed(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.String("42")) => true
            case _                                                   => false
          }
        )
      },
      test("Compose reverse reverses order") {
        val composed = DynamicTransform.Compose(DynamicTransform.StringToInt, DynamicTransform.IntToString)
        val reverse  = composed.reverse

        assertTrue(reverse.isInstanceOf[DynamicTransform.Compose])
      },
      test("MapElements transforms each element") {
        val input = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val mapElements = DynamicTransform.MapElements(DynamicTransform.IntToString)
        val result      = mapElements(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Sequence(elements) =>
              elements.length == 3 &&
              elements.forall {
                case DynamicValue.Primitive(PrimitiveValue.String(_)) => true
                case _                                                => false
              }
            case _ => false
          }
        )
      },
      test("MapElements reverse uses reverse transform") {
        val mapElements = DynamicTransform.MapElements(DynamicTransform.StringToInt)
        val reverse     = mapElements.reverse

        assertTrue(reverse.isInstanceOf[DynamicTransform.MapElements])
      },
      test("Identity returns input unchanged") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicTransform.Identity(input)

        assertTrue(result == Right(input))
      },
      test("Constant returns constant value") {
        val constant = DynamicTransform.Constant(DynamicValue.Primitive(PrimitiveValue.String("always")))
        val input    = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result   = constant(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.String("always")) => true
            case _                                                       => false
          }
        )
      },
      test("WrapSome wraps value in Some") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicTransform.WrapSome(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Variant("Some", _) => true
            case _                               => false
          }
        )
      },
      test("UnwrapOption unwraps Some to inner value") {
        val input = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(
            Chunk(
              "value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
            )
          )
        )
        val unwrap = DynamicTransform.UnwrapOption(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val result = unwrap(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Int(42)) => true
            case _                                              => false
          }
        )
      },
      test("UnwrapOption returns default for None") {
        val input  = DynamicValue.Variant("None", DynamicValue.Null)
        val unwrap = DynamicTransform.UnwrapOption(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val result = unwrap(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Primitive(PrimitiveValue.Int(0)) => true
            case _                                             => false
          }
        )
      },
      test("WrapLeft wraps value in Left") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicTransform.WrapLeft(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Variant("Left", _) => true
            case _                               => false
          }
        )
      },
      test("WrapRight wraps value in Right") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicTransform.WrapRight(input)

        assertTrue(
          result.isRight,
          result.exists {
            case DynamicValue.Variant("Right", _) => true
            case _                                => false
          }
        )
      },
      test("StringToInt fails for invalid string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("not-a-number"))
        val result = DynamicTransform.StringToInt(input)

        assertTrue(result.isLeft)
      },
      test("type mismatch errors") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicTransform.StringToInt(input)

        assertTrue(
          result.isLeft,
          result.swap.exists(_.isInstanceOf[MigrationError.TypeMismatch])
        )
      }
    ),
    suite("MigrationError tests")(
      test("NotFound error has correct message") {
        val error = MigrationError.notFound(DynamicOptic.root.field("name"), "field missing")

        assertTrue(
          error.message.contains("name"),
          error.message.contains("field missing")
        )
      },
      test("TypeMismatch error has correct message") {
        val error = MigrationError.typeMismatch(DynamicOptic.root, "String", "Int")

        assertTrue(
          error.message.contains("String"),
          error.message.contains("Int")
        )
      },
      test("MissingField error has correct message") {
        val error = MigrationError.missingField("age")

        assertTrue(error.message.contains("age"))
      },
      test("UnknownCase error has correct message") {
        val error = MigrationError.unknownCase(DynamicOptic.root, "InvalidCase")

        assertTrue(error.message.contains("InvalidCase"))
      },
      test("TransformFailed error has correct message") {
        val error = MigrationError.transformFailed(DynamicOptic.root, "conversion failed")

        assertTrue(error.message.contains("conversion failed"))
      },
      test("IndexOutOfBounds error has correct message") {
        val error = MigrationError.indexOutOfBounds(5, 3)

        assertTrue(
          error.message.contains("5"),
          error.message.contains("3")
        )
      },
      test("KeyNotFound error has correct message") {
        val key   = DynamicValue.Primitive(PrimitiveValue.String("missing-key"))
        val error = MigrationError.keyNotFound(key)

        assertTrue(error.message.contains("missing-key"))
      },
      test("DefaultFailed error has correct message") {
        val error = MigrationError.defaultFailed("no default available")

        assertTrue(error.message.contains("no default available"))
      },
      test("InvalidAction error has correct message") {
        val error = MigrationError.invalidAction("AddField", "field already exists")

        assertTrue(
          error.message.contains("AddField"),
          error.message.contains("field already exists")
        )
      },
      test("Multiple aggregates errors") {
        val error = MigrationError.multiple(
          MigrationError.notFound("error1"),
          MigrationError.notFound("error2")
        )

        assertTrue(
          error.isInstanceOf[MigrationError.Multiple],
          error.message.contains("error1"),
          error.message.contains("error2")
        )
      },
      test("aggregate returns None for empty") {
        val result = MigrationError.aggregate(Chunk.empty)

        assertTrue(result.isEmpty)
      },
      test("aggregate returns single error for single element") {
        val error  = MigrationError.notFound("test")
        val result = MigrationError.aggregate(Chunk(error))

        assertTrue(
          result.isDefined,
          result.get == error
        )
      },
      test("aggregate returns Multiple for multiple elements") {
        val result = MigrationError.aggregate(
          MigrationError.notFound("error1"),
          MigrationError.notFound("error2")
        )

        assertTrue(
          result.isDefined,
          result.get.isInstanceOf[MigrationError.Multiple]
        )
      }
    ),
    suite("action reverse tests")(
      test("AddField reverse is DropField") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("age"),
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
        val reverse = action.reverse

        assertTrue(reverse.isInstanceOf[MigrationAction.DropField])
      },
      test("DropField reverse is AddField") {
        val action = MigrationAction.DropField(
          DynamicOptic.root.field("age"),
          DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
        val reverse = action.reverse

        assertTrue(reverse.isInstanceOf[MigrationAction.AddField])
      },
      test("Rename reverse swaps names") {
        val action  = MigrationAction.Rename(DynamicOptic.root.field("oldName"), "newName")
        val reverse = action.reverse

        assertTrue(reverse.isInstanceOf[MigrationAction.Rename])
      },
      test("TransformValue reverse uses transform reverse") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root.field("value"),
          DynamicTransform.StringToInt
        )
        val reverse = action.reverse

        assertTrue(
          reverse.isInstanceOf[MigrationAction.TransformValue],
          reverse.asInstanceOf[MigrationAction.TransformValue].transform == DynamicTransform.IntToString
        )
      },
      test("Mandate reverse is Optionalize") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root.field("value"),
          DynamicValue.Primitive(PrimitiveValue.String("default"))
        )
        val reverse = action.reverse

        assertTrue(reverse.isInstanceOf[MigrationAction.Optionalize])
      },
      test("Optionalize reverse is Mandate") {
        val action  = MigrationAction.Optionalize(DynamicOptic.root.field("value"))
        val reverse = action.reverse

        assertTrue(reverse.isInstanceOf[MigrationAction.Mandate])
      },
      test("TransformElements reverse uses transform reverse") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root.field("items"),
          DynamicTransform.StringToInt
        )
        val reverse = action.reverse

        assertTrue(
          reverse.isInstanceOf[MigrationAction.TransformElements],
          reverse.asInstanceOf[MigrationAction.TransformElements].transform == DynamicTransform.IntToString
        )
      },
      test("TransformKeys reverse uses transform reverse") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root.field("map"),
          DynamicTransform.StringUpperCase
        )
        val reverse = action.reverse

        assertTrue(
          reverse.isInstanceOf[MigrationAction.TransformKeys],
          reverse.asInstanceOf[MigrationAction.TransformKeys].transform == DynamicTransform.StringLowerCase
        )
      },
      test("TransformValues reverse uses transform reverse") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root.field("map"),
          DynamicTransform.IntToString
        )
        val reverse = action.reverse

        assertTrue(
          reverse.isInstanceOf[MigrationAction.TransformValues],
          reverse.asInstanceOf[MigrationAction.TransformValues].transform == DynamicTransform.StringToInt
        )
      },
      test("RenameCase reverse swaps case names") {
        val action  = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val reverse = action.reverse

        assertTrue(
          reverse.isInstanceOf[MigrationAction.RenameCase],
          reverse.asInstanceOf[MigrationAction.RenameCase].from == "NewCase",
          reverse.asInstanceOf[MigrationAction.RenameCase].to == "OldCase"
        )
      },
      test("TransformCase reverse reverses nested actions") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "User",
          Chunk(MigrationAction.TransformValue(DynamicOptic.root.field("name"), DynamicTransform.StringUpperCase))
        )
        val reverse = action.reverse

        assertTrue(
          reverse.isInstanceOf[MigrationAction.TransformCase],
          reverse.asInstanceOf[MigrationAction.TransformCase].actions.head.isInstanceOf[MigrationAction.TransformValue],
          reverse
            .asInstanceOf[MigrationAction.TransformCase]
            .actions
            .head
            .asInstanceOf[MigrationAction.TransformValue]
            .transform == DynamicTransform.StringLowerCase
        )
      }
    )
  )
}
