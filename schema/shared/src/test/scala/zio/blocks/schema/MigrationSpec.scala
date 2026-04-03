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

object MigrationSpec extends ZIOSpecDefault {

  def spec = suite("Migration")(
    suite("typed Migration[A, B]")(
      test("identity migration returns input unchanged") {
        implicit val personSchema: Schema[Person] = Schema.derived[Person]

        val migration = Migration.identity[Person]
        val person    = Person("Alice", 30)

        assertTrue(
          migration(person) == Right(person),
          migration.isEmpty,
          migration.size == 0
        )
      },
      test("typed migration wraps and unwraps values correctly") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val dynamicMigration = DynamicMigration.addField(
          DynamicOptic.root.field("country"),
          DynamicValue.Primitive(PrimitiveValue.String("US"))
        )

        val migration = Migration.fromDynamic[PersonV1, PersonV2](
          dynamicMigration,
          v1Schema,
          v2Schema
        )

        val v1     = PersonV1("Alice", 30)
        val result = migration(v1)

        assertTrue(
          result.isRight,
          result.exists(_.country == "US"),
          result.exists(_.name == "Alice"),
          result.exists(_.age == 30)
        )
      },
      test("migration composition with andThen") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]
        implicit val v3Schema: Schema[PersonV3] = Schema.derived[PersonV3]

        val m1 = Migration.fromDynamic[PersonV1, PersonV2](
          DynamicMigration.addField(
            DynamicOptic.root.field("country"),
            DynamicValue.Primitive(PrimitiveValue.String("US"))
          ),
          v1Schema,
          v2Schema
        )

        val m2 = Migration.fromDynamic[PersonV2, PersonV3](
          DynamicMigration.addField(
            DynamicOptic.root.field("email"),
            DynamicValue.Primitive(PrimitiveValue.String(""))
          ),
          v2Schema,
          v3Schema
        )

        val composed = m1.andThen(m2)
        val v1       = PersonV1("Alice", 30)
        val result   = composed(v1)

        assertTrue(
          result.isRight,
          result.exists(_.country == "US"),
          result.exists(_.email == "")
        )
      },
      test("migration composition with >>> operator") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val m1 = Migration.identity[PersonV1]
        val m2 = Migration.fromDynamic[PersonV1, PersonV2](
          DynamicMigration.addField(
            DynamicOptic.root.field("country"),
            DynamicValue.Primitive(PrimitiveValue.String("US"))
          ),
          v1Schema,
          v2Schema
        )

        val composed = m1 >>> m2
        val v1       = PersonV1("Alice", 30)
        val result   = composed(v1)

        assertTrue(result.isRight)
      },
      test("reverse migration") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val forward = Migration.fromDynamic[PersonV1, PersonV2](
          DynamicMigration.addField(
            DynamicOptic.root.field("country"),
            DynamicValue.Primitive(PrimitiveValue.String("US"))
          ),
          v1Schema,
          v2Schema
        )

        val reverse = forward.reverse

        assertTrue(
          reverse.nonEmpty,
          reverse.toDynamic.actions.head.isInstanceOf[MigrationAction.DropField]
        )
      }
    ),
    suite("MigrationBuilder")(
      test("builder creates identity migration when empty") {
        implicit val personSchema: Schema[Person] = Schema.derived[Person]

        val migration = MigrationBuilder.identity[Person].build

        assertTrue(
          migration.isEmpty,
          migration(Person("Alice", 30)) == Right(Person("Alice", 30))
        )
      },
      test("builder adds field with typed default") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = MigrationBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic.root.field("country"), "US")
          .build

        val v1     = PersonV1("Alice", 30)
        val result = migration(v1)

        assertTrue(
          result.isRight,
          result.exists(_.country == "US")
        )
      },
      test("builder renames field") {
        implicit val renameSchema: Schema[RenameTest] = Schema.derived[RenameTest]

        val migration = MigrationBuilder[RenameTest, RenameTest]
          .renameField(DynamicOptic.root.field("oldName"), "newName")
          .buildPartial

        assertTrue(migration.nonEmpty)
      },
      test("builder transforms value") {
        implicit val personSchema: Schema[Person] = Schema.derived[Person]

        val migration = MigrationBuilder[Person, Person]
          .transformValue(
            DynamicOptic.root.field("name"),
            DynamicTransform.StringUpperCase
          )
          .build

        val person = Person("alice", 30)
        val result = migration(person)

        assertTrue(
          result.isRight,
          result.exists(_.name == "ALICE")
        )
      },
      test("builder transforms elements in sequence") {
        implicit val schema: Schema[NamesHolder] = Schema.derived[NamesHolder]

        val migration = MigrationBuilder[NamesHolder, NamesHolder]
          .transformElements(
            DynamicOptic.root.field("names"),
            DynamicTransform.StringUpperCase
          )
          .build

        val holder = NamesHolder(Chunk("alice", "bob"))
        val result = migration(holder)

        assertTrue(
          result.isRight,
          result.exists(_.names == Chunk("ALICE", "BOB"))
        )
      },
      test("builder changes field type") {
        implicit val stringSchema: Schema[StringHolder] = Schema.derived[StringHolder]
        implicit val intSchema: Schema[IntHolder]       = Schema.derived[IntHolder]

        val migration = MigrationBuilder[StringHolder, IntHolder]
          .changeType(
            DynamicOptic.root.field("value"),
            DynamicTransform.StringToInt
          )
          .buildPartial

        val stringHolder = StringHolder("42")
        val result       = migration(stringHolder)

        assertTrue(
          result.isRight,
          result.exists(_.value == 42)
        )
      },
      test("builder composes actions") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = MigrationBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic.root.field("country"), "US")
          .transformValue(DynamicOptic.root.field("name"), DynamicTransform.StringUpperCase)
          .build

        val v1     = PersonV1("alice", 30)
        val result = migration(v1)

        assertTrue(
          result.isRight,
          result.exists(_.country == "US"),
          result.exists(_.name == "ALICE")
        )
      },
      test("builder size and isEmpty") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val emptyBuilder       = MigrationBuilder[PersonV1, PersonV2]
        val builderWithActions = emptyBuilder
          .addField(DynamicOptic.root.field("country"), "US")

        assertTrue(
          emptyBuilder.isEmpty,
          !emptyBuilder.nonEmpty,
          emptyBuilder.size == 0,
          !builderWithActions.isEmpty,
          builderWithActions.nonEmpty,
          builderWithActions.size == 1
        )
      },
      test("builder toDynamic conversion") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val builder = MigrationBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic.root.field("country"), "US")

        val dynamic = builder.toDynamic

        assertTrue(
          dynamic.nonEmpty,
          dynamic.size == 1
        )
      }
    ),
    suite("Transform[A, B]")(
      test("Transform.identity returns input unchanged") {
        val transform = Transform.identity[Int](using Transform.intReflect)

        assertTrue(transform(42) == Right(42))
      },
      test("Transform.stringToInt converts strings to ints") {
        val transform = Transform.stringToInt

        assertTrue(
          transform("42") == Right(42),
          transform("invalid").isLeft
        )
      },
      test("Transform.intToString converts ints to strings") {
        val transform = Transform.intToString

        assertTrue(transform(42) == Right("42"))
      },
      test("Transform composition with >>>") {
        val transform = Transform.stringToInt >>> Transform.intToString

        assertTrue(transform("42") == Right("42"))
      },
      test("Transform.reverse for type conversions") {
        val forward = Transform.stringToInt
        val reverse = forward.reverse

        assertTrue(reverse(42) == Right("42"))
      },
      test("Transform numeric operations") {
        val add5 = Transform.intAdd(5)

        assertTrue(add5(10) == Right(15))
      },
      test("Transform string operations") {
        val upper = Transform.stringToUpperCase
        val trim  = Transform.stringTrim

        assertTrue(
          upper("hello") == Right("HELLO"),
          trim("  hello  ") == Right("hello")
        )
      }
    ),
    suite("SchemaExprTransform")(
      test("literal creates constant transform") {
        val transform = SchemaExprTransform.literal("hello")
        val input     = DynamicValue.Primitive(PrimitiveValue.String("ignored"))

        val result = transform(input)

        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("hello"))))
      },
      test("fromTransform converts typed transform to dynamic") {
        val typed   = Transform.stringToInt
        val dynamic = SchemaExprTransform.fromTransform(typed)

        val input  = DynamicValue.Primitive(PrimitiveValue.String("42"))
        val result = dynamic(input)

        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      }
    ),
    suite("Transform edge cases")(
      test("Transform.stringToLong converts strings to longs") {
        val transform = Transform.stringToLong

        assertTrue(
          transform("9223372036854775807") == Right(9223372036854775807L),
          transform("invalid").isLeft
        )
      },
      test("Transform.longToString converts longs to strings") {
        val transform = Transform.longToString

        assertTrue(transform(9223372036854775807L) == Right("9223372036854775807"))
      },
      test("Transform.stringToDouble converts strings to doubles") {
        val transform = Transform.stringToDouble

        assertTrue(
          transform("3.14159").exists(d => Math.abs(d - 3.14159) < 0.0001),
          transform("invalid").isLeft
        )
      },
      test("Transform.doubleToString converts doubles to strings") {
        val transform = Transform.doubleToString

        assertTrue(transform(3.14159).exists(_.contains("3.14159")))
      },
      test("Transform.stringToBoolean converts strings to booleans") {
        val transform = Transform.stringToBoolean

        assertTrue(
          transform("true") == Right(true),
          transform("false") == Right(false),
          transform("yes") == Right(true),
          transform("no") == Right(false),
          transform("1") == Right(true),
          transform("0") == Right(false),
          transform("invalid").isLeft
        )
      },
      test("Transform.booleanToString converts booleans to strings") {
        val transform = Transform.booleanToString

        assertTrue(
          transform(true) == Right("true"),
          transform(false) == Right("false")
        )
      },
      test("Transform.intToLong widens int to long") {
        val transform = Transform.intToLong

        assertTrue(transform(42) == Right(42L))
      },
      test("Transform.longToInt narrows long to int") {
        val transform = Transform.longToInt

        assertTrue(transform(42L) == Right(42))
      },
      test("Transform.floatToDouble widens float to double") {
        val transform = Transform.floatToDouble

        assertTrue(transform(3.14f).exists(d => Math.abs(d - 3.14) < 0.01))
      },
      test("Transform.doubleToFloat narrows double to float") {
        val transform = Transform.doubleToFloat

        assertTrue(transform(3.14).exists(f => Math.abs(f - 3.14f) < 0.01f))
      },
      test("Transform.intSubtract subtracts from int") {
        val subtract5 = Transform.intSubtract(5)

        assertTrue(subtract5(10) == Right(5))
      },
      test("Transform.intMultiply multiplies int") {
        val multiply3 = Transform.intMultiply(3)

        assertTrue(multiply3(5) == Right(15))
      },
      test("Transform.intDivide divides int") {
        val divide2 = Transform.intDivide(2)

        assertTrue(divide2(10) == Right(5))
      },
      test("Transform.longAdd adds to long") {
        val add100 = Transform.longAdd(100L)

        assertTrue(add100(42L) == Right(142L))
      },
      test("Transform.longSubtract subtracts from long") {
        val subtract10 = Transform.longSubtract(10L)

        assertTrue(subtract10(50L) == Right(40L))
      },
      test("Transform.doubleAdd adds to double") {
        val addPi = Transform.doubleAdd(3.14)

        assertTrue(addPi(1.0).exists(d => Math.abs(d - 4.14) < 0.001))
      },
      test("Transform.doubleSubtract subtracts from double") {
        val subtractPi = Transform.doubleSubtract(3.14)

        assertTrue(subtractPi(5.0).exists(d => Math.abs(d - 1.86) < 0.001))
      },
      test("Transform.doubleMultiply multiplies double") {
        val multiply2 = Transform.doubleMultiply(2.0)

        assertTrue(multiply2(3.5) == Right(7.0))
      },
      test("Transform.doubleDivide divides double") {
        val divide2 = Transform.doubleDivide(2.0)

        assertTrue(divide2(7.0) == Right(3.5))
      },
      test("Transform.stringToLowerCase converts to lowercase") {
        val transform = Transform.stringToLowerCase

        assertTrue(transform("HELLO") == Right("hello"))
      },
      test("Transform.identity returns same value") {
        val transform = Transform.identity[Int](using Transform.intReflect)

        assertTrue(transform(42) == Right(42))
      },
      test("Transform roundtrip stringToInt and intToString") {
        val original  = "42"
        val roundtrip = Transform.stringToInt >>> Transform.intToString

        assertTrue(roundtrip(original) == Right(original))
      }
    ),
    suite("MigrationBuilder advanced operations")(
      test("builder with nested field operations") {
        implicit val schema: Schema[NestedPerson] = Schema.derived[NestedPerson]

        val migration = MigrationBuilder[NestedPerson, NestedPerson]
          .transformValue(
            DynamicOptic.root.field("address").field("city"),
            DynamicTransform.StringUpperCase
          )
          .build

        val person = NestedPerson("Alice", Address("main street", "seattle"))
        val result = migration(person)

        assertTrue(
          result.isRight,
          result.exists(_.address.city == "SEATTLE")
        )
      },
      test("builder with optional field mandate") {
        implicit val sourceSchema: Schema[OptionalPerson] = Schema.derived[OptionalPerson]
        implicit val targetSchema: Schema[RequiredPerson] = Schema.derived[RequiredPerson]

        val migration = MigrationBuilder[OptionalPerson, RequiredPerson]
          .mandate(DynamicOptic.root.field("nickname"), "Unknown")
          .buildPartial

        val person = OptionalPerson("Alice", Some("Ally"))
        val result = migration(person)

        assertTrue(
          result.isRight,
          result.exists(_.nickname == "Ally")
        )
      },
      test("builder with dropField") {
        implicit val v1Schema: Schema[PersonV2] = Schema.derived[PersonV2]
        implicit val v2Schema: Schema[PersonV1] = Schema.derived[PersonV1]

        val migration = MigrationBuilder[PersonV2, PersonV1]
          .dropField(DynamicOptic.root.field("country"), "US")
          .build

        val v2     = PersonV2("Alice", 30, "US")
        val result = migration(v2)

        assertTrue(
          result.isRight,
          result.exists(_.name == "Alice"),
          result.exists(_.age == 30)
        )
      },
      test("builder with multiple transforms in sequence") {
        implicit val schema: Schema[Person] = Schema.derived[Person]

        val migration = MigrationBuilder[Person, Person]
          .transformValue(DynamicOptic.root.field("name"), DynamicTransform.StringUpperCase)
          .transformValue(DynamicOptic.root.field("name"), DynamicTransform.StringTrim)
          .build

        val person = Person(" alice ", 30)
        val result = migration(person)

        assertTrue(
          result.isRight,
          result.exists(_.name == "ALICE")
        )
      },
      test("builder with changeType for type conversion") {
        implicit val stringSchema: Schema[StringPerson] = Schema.derived[StringPerson]
        implicit val intSchema: Schema[IntPerson]       = Schema.derived[IntPerson]

        val migration = MigrationBuilder[StringPerson, IntPerson]
          .changeType(DynamicOptic.root.field("age"), DynamicTransform.StringToInt)
          .buildPartial

        val stringPerson = StringPerson("Alice", "30")
        val result       = migration(stringPerson)

        assertTrue(
          result.isRight,
          result.exists(_.age == 30)
        )
      },
      test("builder getActions returns all actions") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val builder = MigrationBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic.root.field("country"), "US")
          .transformValue(DynamicOptic.root.field("name"), DynamicTransform.StringUpperCase)

        val actions = builder.getActions

        assertTrue(actions.length == 2)
      },
      test("builder ++ composition") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val builder1 = MigrationBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic.root.field("country"), "US")
        val builder2 = MigrationBuilder[PersonV1, PersonV2]
          .transformValue(DynamicOptic.root.field("name"), DynamicTransform.StringUpperCase)

        val combined = builder1 ++ builder2

        assertTrue(combined.size == 2)
      },
      test("builder ++ with DynamicMigration") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val builder = MigrationBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic.root.field("country"), "US")
        val dynamic = DynamicMigration.transformValue(
          DynamicOptic.root.field("name"),
          DynamicTransform.StringUpperCase
        )

        val combined = builder ++ dynamic

        assertTrue(combined.size == 2)
      },
      test("builder fromDynamic creates builder from DynamicMigration") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val dynamic = DynamicMigration.addField(
          DynamicOptic.root.field("country"),
          DynamicValue.Primitive(PrimitiveValue.String("US"))
        )

        val builder = MigrationBuilder.fromDynamic[PersonV1, PersonV2](dynamic)

        assertTrue(
          builder.nonEmpty,
          builder.size == 1
        )
      },
      test("builder with typed transform value") {
        implicit val schema: Schema[Person] = Schema.derived[Person]

        val migration = MigrationBuilder[Person, Person]
          .transformValue(DynamicOptic.root.field("name"), Transform.stringToUpperCase)
          .build

        val person = Person("alice", 30)
        val result = migration(person)

        assertTrue(
          result.isRight,
          result.exists(_.name == "ALICE")
        )
      }
    ),
    suite("Migration serialization")(
      test("DynamicMigration can be serialized to DynamicValue and back") {
        val migration = DynamicMigration.addField(
          DynamicOptic.root.field("country"),
          DynamicValue.Primitive(PrimitiveValue.String("US"))
        )

        val schema  = implicitly[Schema[DynamicMigration]]
        val encoded = schema.toDynamicValue(migration)
        val decoded = schema.fromDynamicValue(encoded)

        assertTrue(decoded.isRight)
      }
    ),
    suite("Migration empty/nonEmpty")(
      test("empty migration is empty") {
        implicit val schema: Schema[Person] = Schema.derived[Person]

        val migration = Migration.identity[Person]

        assertTrue(
          migration.isEmpty,
          !migration.nonEmpty
        )
      },
      test("non-empty migration is nonEmpty") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromDynamic[PersonV1, PersonV2](
          DynamicMigration.addField(
            DynamicOptic.root.field("country"),
            DynamicValue.Primitive(PrimitiveValue.String("US"))
          ),
          v1Schema,
          v2Schema
        )

        assertTrue(
          migration.nonEmpty,
          !migration.isEmpty
        )
      }
    ),
    suite("Migration error propagation")(
      test("migration fails when transform fails") {
        implicit val stringSchema: Schema[StringPerson] = Schema.derived[StringPerson]
        implicit val intSchema: Schema[IntPerson]       = Schema.derived[IntPerson]

        val migration = MigrationBuilder[StringPerson, IntPerson]
          .changeType(DynamicOptic.root.field("age"), DynamicTransform.StringToInt)
          .buildPartial

        val invalidPerson = StringPerson("Alice", "not-a-number")
        val result        = migration(invalidPerson)

        assertTrue(result.isLeft)
      }
    ),
    suite("Migration ++ operator")(
      test("Migration ++ composes like andThen") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val m1 = Migration.fromDynamic[PersonV1, PersonV2](
          DynamicMigration.addField(
            DynamicOptic.root.field("country"),
            DynamicValue.Primitive(PrimitiveValue.String("US"))
          ),
          v1Schema,
          v2Schema
        )

        val m2 = Migration.fromDynamic[PersonV2, PersonV2](
          DynamicMigration.transformValue(
            DynamicOptic.root.field("name"),
            DynamicTransform.StringUpperCase
          ),
          v2Schema,
          v2Schema
        )

        val composed = m1 ++ m2
        val v1       = PersonV1("alice", 30)
        val result   = composed(v1)

        assertTrue(
          result.isRight,
          result.exists(_.country == "US"),
          result.exists(_.name == "ALICE")
        )
      }
    ),
    suite("Rename reverse round-trip")(
      test("rename reverse correctly restores field name") {
        val input = DynamicValue.Record(
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )
        val migration = DynamicMigration.rename(DynamicOptic.root.field("firstName"), "name")
        val result    = migration(input)

        assertTrue(
          result.isRight,
          result.exists { v =>
            val record = v.asInstanceOf[DynamicValue.Record]
            record.fields.exists(_._1 == "name") &&
            !record.fields.exists(_._1 == "firstName")
          }
        )

        // Test reverse
        val reverse       = migration.reverse
        val reverseResult = reverse(result.toOption.get)

        assertTrue(
          reverseResult.isRight,
          reverseResult.exists { v =>
            val record = v.asInstanceOf[DynamicValue.Record]
            record.fields.exists(_._1 == "firstName") &&
            !record.fields.exists(_._1 == "name")
          }
        )
      }
    ),
    suite("Join action")(
      test("join combines multiple source paths") {
        val input = DynamicValue.Record(
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
          )
        )
        val migration = DynamicMigration.join(
          at = DynamicOptic.root.field("fullName"),
          sourcePaths = Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          combiner = DynamicTransform.StringConcatWith(" ")
        )

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists { v =>
            val record = v.asInstanceOf[DynamicValue.Record]
            record.fields.find(_._1 == "fullName").exists {
              case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "John Doe"
              case _                                                     => false
            }
          }
        )
      }
    ),
    suite("Split action")(
      test("split decomposes a value into multiple targets") {
        val input = DynamicValue.Record(
          Chunk(
            "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
          )
        )
        val migration = DynamicMigration.split(
          at = DynamicOptic.root.field("fullName"),
          targetPaths = Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          splitter = DynamicTransform.StringSplit(" ")
        )

        val result = migration(input)

        assertTrue(
          result.isRight,
          result.exists { v =>
            val record = v.asInstanceOf[DynamicValue.Record]
            record.fields.find(_._1 == "firstName").exists {
              case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "John"
              case _                                                     => false
            } &&
            record.fields.find(_._1 == "lastName").exists {
              case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) => s == "Doe"
              case _                                                     => false
            }
          }
        )
      }
    ),
    suite("Build validation")(
      test("build throws on null source schema") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val builder = MigrationBuilder.fromActions[PersonV1, PersonV2](
          Chunk(
            MigrationAction.AddField(
              DynamicOptic.root.field("country"),
              DynamicValue.Primitive(PrimitiveValue.String("US"))
            )
          )
        )

        val migration = builder.build

        assertTrue(migration.nonEmpty)
      }
    ),
    suite("Migration reverse round-trip")(
      test("addField then reverse drops the field") {
        implicit val v1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val v2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val forward = MigrationBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic.root.field("country"), "US")
          .build

        val v1            = PersonV1("Alice", 30)
        val forwardResult = forward(v1)

        assertTrue(forwardResult.isRight)

        // Apply reverse
        val reverse       = forward.reverse
        val reverseResult = reverse(forwardResult.toOption.get)

        assertTrue(
          reverseResult.isRight,
          reverseResult.exists(_.name == "Alice"),
          reverseResult.exists(_.age == 30)
        )
      },
      test("nested migration with reverse preserves structure") {
        implicit val schema: Schema[NestedPerson] = Schema.derived[NestedPerson]

        val migration = MigrationBuilder[NestedPerson, NestedPerson]
          .transformValue(
            DynamicOptic.root.field("address").field("city"),
            DynamicTransform.StringUpperCase
          )
          .build

        val person = NestedPerson("Alice", Address("main street", "seattle"))
        val result = migration(person)

        assertTrue(
          result.isRight,
          result.exists(_.address.city == "SEATTLE")
        )

        val reverse       = migration.reverse
        val reverseResult = reverse(result.toOption.get)

        assertTrue(
          reverseResult.isRight,
          reverseResult.exists(_.address.city == "seattle")
        )
      }
    )
  )

  // Test data types
  final case class Person(name: String, age: Int)
  final case class PersonV1(name: String, age: Int)
  final case class PersonV2(name: String, age: Int, country: String)
  final case class PersonV3(name: String, age: Int, country: String, email: String)
  final case class StringHolder(value: String)
  final case class IntHolder(value: Int)
  final case class NamesHolder(names: Chunk[String])
  final case class RenameTest(oldName: String)
  final case class Address(street: String, city: String)
  final case class NestedPerson(name: String, address: Address)
  final case class OptionalPerson(name: String, nickname: Option[String])
  final case class RequiredPerson(name: String, nickname: String)
  final case class StringPerson(name: String, age: String)
  final case class IntPerson(name: String, age: Int)
}
