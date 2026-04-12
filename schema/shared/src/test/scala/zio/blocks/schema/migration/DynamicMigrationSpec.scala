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

package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema, SchemaBaseSpec, SchemaExpr}
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {
  private def string(value: String): DynamicValue                        = Schema[String].toDynamicValue(value)
  private def int(value: Int): DynamicValue                              = Schema[Int].toDynamicValue(value)
  private def bool(value: Boolean): DynamicValue                         = Schema[Boolean].toDynamicValue(value)
  private def defaultExpr[A](schema: Schema[A]): SchemaExpr.DefaultValue =
    SchemaExpr.DefaultValue(schema.getDefaultValue.map(schema.toDynamicValue).getOrElse(DynamicValue.Null))

  def spec: Spec[TestEnvironment, Any] =
    suite("DynamicMigrationSpec")(
      test("AddField adds a field with default value") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val migration = DynamicMigration(
          Vector(
            AddField(DynamicOptic.root.field("age"), defaultExpr(ageSchema))
          )
        )

        val expected =
          DynamicValue.Record(
            "name" -> string("Alice"),
            "age"  -> int(0)
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("DropField removes a field") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice"),
            "age"  -> int(42)
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val migration =
          DynamicMigration(
            Vector(
              DropField(DynamicOptic.root.field("age"), defaultExpr(ageSchema))
            )
          )

        val expected =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("Rename renames a field") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val migration =
          DynamicMigration(
            Vector(
              Rename(DynamicOptic.root.field("name"), to = "fullName")
            )
          )

        val expected =
          DynamicValue.Record(
            "fullName" -> string("Alice")
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("RenameCase renames an enum case") {
        val in =
          DynamicValue.Record(
            "color" -> DynamicValue.Variant("Red", DynamicValue.Record())
          )

        val migration =
          DynamicMigration(
            Vector(
              RenameCase(DynamicOptic.root.field("color"), from = "Red", to = "Crimson")
            )
          )

        val expected =
          DynamicValue.Record(
            "color" -> DynamicValue.Variant("Crimson", DynamicValue.Record())
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("TransformCase applies nested actions to a variant") {
        val in =
          DynamicValue.Variant(
            "Person",
            DynamicValue.Record(
              "name" -> string("Alice")
            )
          )

        val nested = Vector(Rename(DynamicOptic.root.field("name"), to = "fullName"))

        val migration =
          DynamicMigration(
            Vector(
              TransformCase(DynamicOptic.root.caseOf("Person"), nested)
            )
          )

        val expected =
          DynamicValue.Variant(
            "Person",
            DynamicValue.Record(
              "fullName" -> string("Alice")
            )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("Rename.reverse is correct (round-trip)") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val action    = Rename(DynamicOptic.root.field("name"), to = "fullName")
        val migration = DynamicMigration(Vector(action))

        val roundTrip =
          migration(in).flatMap(out => migration.reverse(out))

        assertTrue(roundTrip == Right(in))
      },
      test("DynamicMigration.reverse of sequence reverses correctly") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val migration =
          DynamicMigration(
            Vector(
              AddField(DynamicOptic.root.field("age"), defaultExpr(ageSchema)),
              Rename(DynamicOptic.root.field("name"), to = "fullName")
            )
          )

        val roundTrip =
          migration(in).flatMap(out => migration.reverse(out))

        assertTrue(roundTrip == Right(in))
      },
      test("DropField fails with FieldNotFound when field missing") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val migration =
          DynamicMigration(
            Vector(
              DropField(DynamicOptic.root.field("age"), defaultExpr(ageSchema))
            )
          )

        assertTrue(migration(in) == Left(FieldNotFound(DynamicOptic.root.field("age"), "age")))
      },
      test("AddField is idempotent (adding same field twice keeps first)") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val schema1   = Schema[Int].defaultValue(1)
        val schema2   = Schema[Int].defaultValue(2)
        val migration =
          DynamicMigration(
            Vector(
              AddField(DynamicOptic.root.field("age"), defaultExpr(schema1)),
              AddField(DynamicOptic.root.field("age"), defaultExpr(schema2))
            )
          )

        val expected =
          DynamicValue.Record(
            "name" -> string("Alice"),
            "age"  -> int(1)
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("Nested field path: rename field inside nested record") {
        val in =
          DynamicValue.Record(
            "address" ->
              DynamicValue.Record(
                "street" -> string("Main St")
              )
          )

        val migration =
          DynamicMigration(
            Vector(
              Rename(DynamicOptic.root.field("address").field("street"), to = "streetName")
            )
          )

        val expected =
          DynamicValue.Record(
            "address" ->
              DynamicValue.Record(
                "streetName" -> string("Main St")
              )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("Rename fails with FieldNotFound when field missing") {
        val in = DynamicValue.Record("name" -> string("Alice"))

        val migration =
          DynamicMigration(
            Vector(
              Rename(DynamicOptic.root.field("age"), to = "years")
            )
          )

        assertTrue(migration(in) == Left(FieldNotFound(DynamicOptic.root.field("age"), "age")))
      },
      test("AddField fails with TypeMismatch when parent is not a record") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val schema = Schema[String].defaultValue("x")
        val action = AddField(DynamicOptic.root.field("name").field("first"), defaultExpr(schema))

        val out = DynamicMigration(Vector(action))(in)

        assertTrue(out match {
          case Left(TypeMismatch(path, expected, _)) =>
            path == action.at && expected == "Record"
          case _ => false
        })
      },
      test("DropField fails with TypeMismatch when parent is not a record") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val schema = Schema[String].defaultValue("x")
        val action = DropField(DynamicOptic.root.field("name").field("first"), defaultExpr(schema))

        val out = DynamicMigration(Vector(action))(in)

        assertTrue(out match {
          case Left(TypeMismatch(path, expected, _)) =>
            path == action.at && expected == "Record"
          case _ => false
        })
      },
      test("Rename fails with TypeMismatch when parent is not a record") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val action = Rename(DynamicOptic.root.field("name").field("first"), to = "given")

        val out = DynamicMigration(Vector(action))(in)

        assertTrue(out match {
          case Left(TypeMismatch(path, expected, _)) =>
            path == action.at && expected == "Record"
          case _ => false
        })
      },
      test("AddField works inside a sequence element addressed by at(index)") {
        val in =
          DynamicValue.Record(
            "people" -> DynamicValue.Sequence(
              DynamicValue.Record("name" -> string("Alice")),
              DynamicValue.Record("name" -> string("Bob"))
            )
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val migration =
          DynamicMigration(
            Vector(
              AddField(DynamicOptic.root.field("people").at(0).field("age"), defaultExpr(ageSchema))
            )
          )

        val expected =
          DynamicValue.Record(
            "people" -> DynamicValue.Sequence(
              DynamicValue.Record("name" -> string("Alice"), "age" -> int(0)),
              DynamicValue.Record("name" -> string("Bob"))
            )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("Rename works inside a sequence element addressed by at(index)") {
        val in =
          DynamicValue.Record(
            "people" -> DynamicValue.Sequence(
              DynamicValue.Record("name" -> string("Alice")),
              DynamicValue.Record("name" -> string("Bob"))
            )
          )

        val migration =
          DynamicMigration(
            Vector(
              Rename(DynamicOptic.root.field("people").at(1).field("name"), to = "fullName")
            )
          )

        val expected =
          DynamicValue.Record(
            "people" -> DynamicValue.Sequence(
              DynamicValue.Record("name"     -> string("Alice")),
              DynamicValue.Record("fullName" -> string("Bob"))
            )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("AddField works inside all sequence elements addressed by each") {
        val in =
          DynamicValue.Record(
            "people" -> DynamicValue.Sequence(
              DynamicValue.Record("name" -> string("Alice")),
              DynamicValue.Record("name" -> string("Bob"))
            )
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val migration =
          DynamicMigration(
            Vector(
              AddField(DynamicOptic.root.field("people").each.field("age"), defaultExpr(ageSchema))
            )
          )

        val expected =
          DynamicValue.Record(
            "people" -> DynamicValue.Sequence(
              DynamicValue.Record("name" -> string("Alice"), "age" -> int(0)),
              DynamicValue.Record("name" -> string("Bob"), "age"   -> int(0))
            )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("Rename works inside sequence elements addressed by atIndices") {
        val in =
          DynamicValue.Record(
            "people" -> DynamicValue.Sequence(
              DynamicValue.Record("name" -> string("Alice")),
              DynamicValue.Record("name" -> string("Bob")),
              DynamicValue.Record("name" -> string("Carol"))
            )
          )

        val migration =
          DynamicMigration(
            Vector(
              Rename(DynamicOptic.root.field("people").atIndices(0, 2).field("name"), to = "fullName")
            )
          )

        val expected =
          DynamicValue.Record(
            "people" -> DynamicValue.Sequence(
              DynamicValue.Record("fullName" -> string("Alice")),
              DynamicValue.Record("name"     -> string("Bob")),
              DynamicValue.Record("fullName" -> string("Carol"))
            )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("AddField works inside a map value addressed by atKey") {
        val in =
          DynamicValue.Record(
            "peopleById" -> DynamicValue.Map(
              string("a") -> DynamicValue.Record("name" -> string("Alice")),
              string("b") -> DynamicValue.Record("name" -> string("Bob"))
            )
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val migration =
          DynamicMigration(
            Vector(
              AddField(DynamicOptic.root.field("peopleById").atKey("a").field("age"), defaultExpr(ageSchema))
            )
          )

        val expected =
          DynamicValue.Record(
            "peopleById" -> DynamicValue.Map(
              string("a") -> DynamicValue.Record("name" -> string("Alice"), "age" -> int(0)),
              string("b") -> DynamicValue.Record("name" -> string("Bob"))
            )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("Rename works inside all map values addressed by mapValues") {
        val in =
          DynamicValue.Record(
            "peopleById" -> DynamicValue.Map(
              string("a") -> DynamicValue.Record("name" -> string("Alice")),
              string("b") -> DynamicValue.Record("name" -> string("Bob"))
            )
          )

        val migration =
          DynamicMigration(
            Vector(
              Rename(DynamicOptic.root.field("peopleById").mapValues.field("name"), to = "fullName")
            )
          )

        val expected =
          DynamicValue.Record(
            "peopleById" -> DynamicValue.Map(
              string("a") -> DynamicValue.Record("fullName" -> string("Alice")),
              string("b") -> DynamicValue.Record("fullName" -> string("Bob"))
            )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("AddField works inside map values addressed by atKeys") {
        val in =
          DynamicValue.Record(
            "peopleById" -> DynamicValue.Map(
              string("a") -> DynamicValue.Record("name" -> string("Alice")),
              string("b") -> DynamicValue.Record("name" -> string("Bob")),
              string("c") -> DynamicValue.Record("name" -> string("Carol"))
            )
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val migration =
          DynamicMigration(
            Vector(
              AddField(DynamicOptic.root.field("peopleById").atKeys("a", "c").field("age"), defaultExpr(ageSchema))
            )
          )

        val expected =
          DynamicValue.Record(
            "peopleById" -> DynamicValue.Map(
              string("a") -> DynamicValue.Record("name" -> string("Alice"), "age" -> int(0)),
              string("b") -> DynamicValue.Record("name" -> string("Bob")),
              string("c") -> DynamicValue.Record("name" -> string("Carol"), "age" -> int(0))
            )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("Rename fails with FieldNotFound when intermediate field is missing") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val action = Rename(DynamicOptic.root.field("address").field("street"), to = "streetName")
        val out    = DynamicMigration(Vector(action))(in)

        assertTrue(out match {
          case Left(FieldNotFound(path, field)) =>
            path == action.at && field == "address"
          case _ => false
        })
      },
      test("AddField fails with MigrationFailed when sequence index is out of bounds") {
        val in =
          DynamicValue.Record(
            "people" -> DynamicValue.Sequence(
              DynamicValue.Record("name" -> string("Alice"))
            )
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val action    = AddField(DynamicOptic.root.field("people").at(5).field("age"), defaultExpr(ageSchema))
        val out       = DynamicMigration(Vector(action))(in)

        assertTrue(out match {
          case Left(MigrationFailed(path, cause)) =>
            path == action.at && cause.contains("out of bounds")
          case _ => false
        })
      },
      test("TransformCase fails when variant case does not match") {
        val in =
          DynamicValue.Variant(
            "Dog",
            DynamicValue.Record(
              "name" -> string("Fido")
            )
          )

        val nested = Vector(Rename(DynamicOptic.root.field("name"), to = "fullName"))
        val action = TransformCase(DynamicOptic.root.caseOf("Cat"), nested)

        val out = DynamicMigration(Vector(action))(in)

        assertTrue(out match {
          case Left(MigrationFailed(path, _)) => path == action.at
          case _                              => false
        })
      },
      test("RenameCase fails with TypeMismatch when target is not a variant") {
        val in =
          DynamicValue.Record(
            "color" -> string("Red")
          )

        val action = RenameCase(DynamicOptic.root.field("color"), from = "Red", to = "Crimson")
        val out    = DynamicMigration(Vector(action))(in)

        assertTrue(out match {
          case Left(TypeMismatch(path, expected, _)) =>
            path == action.at && expected == "Variant"
          case _ => false
        })
      },
      test("Unsupported action fails with MigrationFailed") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val action = Optionalize(DynamicOptic.root.field("name"))
        val out    = DynamicMigration(Vector(action))(in)

        assertTrue(out match {
          case Left(MigrationFailed(path, cause)) =>
            path == action.at && cause.contains("Unsupported migration action")
          case _ => false
        })
      },
      test("TransformElements transforms each element of a sequence") {
        val in =
          DynamicValue.Record(
            "nums" -> DynamicValue.Sequence(int(1), int(2), int(3))
          )

        val migration =
          DynamicMigration(
            Vector(
              TransformElements(
                DynamicOptic.root.field("nums"),
                SchemaExpr.Literal[Any, Int](42, Schema[Int])
              )
            )
          )

        val expected =
          DynamicValue.Record(
            "nums" -> DynamicValue.Sequence(int(42), int(42), int(42))
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("TransformValues transforms each value of a map") {
        val in =
          DynamicValue.Record(
            "counts" -> DynamicValue.Map(
              string("a") -> int(1),
              string("b") -> int(2)
            )
          )

        val migration =
          DynamicMigration(
            Vector(
              TransformValues(
                DynamicOptic.root.field("counts"),
                SchemaExpr.Literal[Any, Int](0, Schema[Int])
              )
            )
          )

        val expected =
          DynamicValue.Record(
            "counts" -> DynamicValue.Map(
              string("a") -> int(0),
              string("b") -> int(0)
            )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("TransformCase.reverse reverses nested actions") {
        val ageSchema = Schema[Int].defaultValue(0)
        val expr      = defaultExpr(ageSchema)

        val nested =
          Vector(
            AddField(DynamicOptic.root.field("age"), expr),
            Rename(DynamicOptic.root.field("name"), to = "fullName")
          )

        val action = TransformCase(DynamicOptic.root.caseOf("Person"), nested)

        val expected =
          TransformCase(
            DynamicOptic.root.caseOf("Person"),
            Vector(
              Rename(DynamicOptic.root.field("fullName"), to = "name"),
              DropField(DynamicOptic.root.field("age"), expr)
            )
          )

        assertTrue(action.reverse == expected)
      },
      test("RenameCase.reverse swaps from/to") {
        val action = RenameCase(DynamicOptic.root, from = "Red", to = "Crimson")
        assertTrue(action.reverse == RenameCase(DynamicOptic.root, from = "Crimson", to = "Red"))
      },
      test("Wrapped node traversal (DynamicOptic.root.wrapped.field)") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val migration =
          DynamicMigration(
            Vector(
              Rename(DynamicOptic.root.wrapped.field("name"), to = "fullName")
            )
          )

        val expected =
          DynamicValue.Record(
            "fullName" -> string("Alice")
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("AddField at nested path inside a variant") {
        val in =
          DynamicValue.Record(
            "person" ->
              DynamicValue.Variant(
                "V1",
                DynamicValue.Record("name" -> string("Alice"))
              )
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val migration =
          DynamicMigration(
            Vector(
              AddField(
                DynamicOptic.root.field("person").caseOf("V1").field("age"),
                defaultExpr(ageSchema)
              )
            )
          )

        val expected =
          DynamicValue.Record(
            "person" ->
              DynamicValue.Variant(
                "V1",
                DynamicValue.Record("name" -> string("Alice"), "age" -> int(0))
              )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("Multiple actions on same field (rename then transform)") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val migration =
          DynamicMigration(
            Vector(
              Rename(DynamicOptic.root.field("name"), to = "fullName"),
              TransformValue(
                DynamicOptic.root.field("fullName"),
                SchemaExpr.Literal[Any, String]("Bob", Schema[String])
              )
            )
          )

        val expected =
          DynamicValue.Record(
            "fullName" -> string("Bob")
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("Empty migration on empty record") {
        val in = DynamicValue.Record()
        assertTrue(DynamicMigration(Vector.empty)(in) == Right(in))
      },
      test("Empty migration on sequence") {
        val in = DynamicValue.Sequence(int(1), int(2))
        assertTrue(DynamicMigration(Vector.empty)(in) == Right(in))
      },
      test("Empty migration on map") {
        val in = DynamicValue.Map(string("a") -> int(1))
        assertTrue(DynamicMigration(Vector.empty)(in) == Right(in))
      },
      test("Deeply nested path (3 levels)") {
        val in =
          DynamicValue.Record(
            "a" ->
              DynamicValue.Record(
                "b" ->
                  DynamicValue.Record(
                    "c" -> string("x")
                  )
              )
          )

        val migration =
          DynamicMigration(
            Vector(
              Rename(DynamicOptic.root.field("a").field("b").field("c"), to = "d")
            )
          )

        val expected =
          DynamicValue.Record(
            "a" ->
              DynamicValue.Record(
                "b" ->
                  DynamicValue.Record(
                    "d" -> string("x")
                  )
              )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("TransformCase with multiple nested actions") {
        val in =
          DynamicValue.Variant(
            "Person",
            DynamicValue.Record(
              "name" -> string("Alice")
            )
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val nested    =
          Vector(
            Rename(DynamicOptic.root.field("name"), to = "fullName"),
            AddField(DynamicOptic.root.field("age"), defaultExpr(ageSchema))
          )

        val migration =
          DynamicMigration(
            Vector(
              TransformCase(DynamicOptic.root.caseOf("Person"), nested)
            )
          )

        val expected =
          DynamicValue.Variant(
            "Person",
            DynamicValue.Record(
              "fullName" -> string("Alice"),
              "age"      -> int(0)
            )
          )

        assertTrue(migration(in) == Right(expected))
      },
      test("RenameCase on root variant (no parent path)") {
        val in        = DynamicValue.Variant("Red", DynamicValue.Record())
        val migration = DynamicMigration(Vector(RenameCase(DynamicOptic.root, from = "Red", to = "Crimson")))
        val expected  = DynamicValue.Variant("Crimson", DynamicValue.Record())
        assertTrue(migration(in) == Right(expected))
      },
      test("DropField then AddField round-trip on same field") {
        val in =
          DynamicValue.Record(
            "age" -> int(0)
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val expr      = defaultExpr(ageSchema)

        val migration =
          DynamicMigration(
            Vector(
              DropField(DynamicOptic.root.field("age"), expr),
              AddField(DynamicOptic.root.field("age"), expr)
            )
          )

        val roundTrip = migration(in).flatMap(out => migration.reverse(out))

        assertTrue(roundTrip == Right(in))
      },
      test("DynamicMigration with 10 chained renames associativity") {
        val in =
          DynamicValue.Record(
            "f0" -> string("x")
          )

        val m1 = DynamicMigration(
          Vector(Rename(DynamicOptic.root.field("f0"), to = "f1"), Rename(DynamicOptic.root.field("f1"), to = "f2"))
        )
        val m2 = DynamicMigration(
          Vector(
            Rename(DynamicOptic.root.field("f2"), to = "f3"),
            Rename(DynamicOptic.root.field("f3"), to = "f4"),
            Rename(DynamicOptic.root.field("f4"), to = "f5")
          )
        )
        val m3 = DynamicMigration(
          Vector(
            Rename(DynamicOptic.root.field("f5"), to = "f6"),
            Rename(DynamicOptic.root.field("f6"), to = "f7"),
            Rename(DynamicOptic.root.field("f7"), to = "f8"),
            Rename(DynamicOptic.root.field("f8"), to = "f9"),
            Rename(DynamicOptic.root.field("f9"), to = "f10")
          )
        )

        val left  = ((m1 ++ m2) ++ m3)(in)
        val right = (m1 ++ (m2 ++ m3))(in)

        assertTrue(left == right)
      },
      test("error message content for FieldNotFound includes field name") {
        val err = FieldNotFound(DynamicOptic.root.field("age"), "age")
        assertTrue(err.toString.contains("age"))
      },
      test("error message content for TypeMismatch includes expected type") {
        val err = TypeMismatch(DynamicOptic.root.field("nums"), expected = "Sequence", got = "Record")
        assertTrue(err.toString.contains("Sequence"))
      },
      test("MigrationFailed path is preserved correctly") {
        val action = Optionalize(DynamicOptic.root.field("name"))
        val out    = DynamicMigration(Vector(action))(DynamicValue.Record("name" -> string("Alice")))

        assertTrue(out match {
          case Left(MigrationFailed(path, _)) => path == action.at
          case _                              => false
        })
      },
      test("reverse of RenameCase is correct") {
        val action = RenameCase(DynamicOptic.root, from = "Red", to = "Crimson")
        assertTrue(action.reverse.reverse == action)
      },
      test("reverse of TransformCase reverses action order") {
        val ageSchema = Schema[Int].defaultValue(0)
        val expr      = defaultExpr(ageSchema)

        val nested =
          Vector(
            AddField(DynamicOptic.root.field("age"), expr),
            Rename(DynamicOptic.root.field("name"), to = "fullName")
          )

        val action = TransformCase(DynamicOptic.root.caseOf("Person"), nested)

        val reversed = action.reverse

        assertTrue(reversed match {
          case TransformCase(_, actions) => actions == nested.reverse.map(_.reverse)
          case _                         => false
        })
      },
      test("reverse of AddField is DropField and vice versa") {
        val at        = DynamicOptic.root.field("age")
        val ageSchema = Schema[Int].defaultValue(0)
        val expr      = defaultExpr(ageSchema)

        assertTrue(AddField(at, expr).reverse == DropField(at, expr)) &&
        assertTrue(DropField(at, expr).reverse == AddField(at, expr))
      },
      test("++ associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3) on a sample value") {
        val in =
          DynamicValue.Record(
            "name" -> string("Alice")
          )

        val ageSchema = Schema[Int].defaultValue(0)
        val addAge    = DynamicMigration(Vector(AddField(DynamicOptic.root.field("age"), defaultExpr(ageSchema))))
        val rename    = DynamicMigration(Vector(Rename(DynamicOptic.root.field("name"), to = "fullName")))
        val dropAge   = DynamicMigration(Vector(DropField(DynamicOptic.root.field("age"), defaultExpr(ageSchema))))

        val left  = ((addAge ++ rename) ++ dropAge)(in)
        val right = (addAge ++ (rename ++ dropAge))(in)

        assertTrue(left == right)
      },
      suite("Generated")(
        ((0 until 25).map { i =>
          test(s"generated rename round-trip $i") {
            val from = s"f$i"
            val to   = s"g$i"

            val in =
              DynamicValue.Record(
                from -> string("x")
              )

            val migration =
              DynamicMigration(
                Vector(
                  Rename(DynamicOptic.root.field(from), to = to)
                )
              )

            val out = migration(in).flatMap(migration.reverse.apply)

            assertTrue(out == Right(in))
          }
        } ++ (0 until 25).map { i =>
          test(s"generated add then drop is no-op $i") {
            val field = s"age$i"

            val in =
              DynamicValue.Record(
                "name" -> string("Alice")
              )

            val schema    = Schema[Int].defaultValue(i)
            val expr      = defaultExpr(schema)
            val migration =
              DynamicMigration(
                Vector(
                  AddField(DynamicOptic.root.field(field), expr),
                  DropField(DynamicOptic.root.field(field), expr)
                )
              )

            assertTrue(migration(in) == Right(in))
          }
        }): _*
      ),
      test("identity law holds for arbitrary records") {
        check(
          Gen.listOf(Gen.string.zip(Gen.int)).map { fields =>
            DynamicValue.Record(
              fields.map { case (k, v) =>
                k -> Schema[Int].toDynamicValue(v)
              }: _*
            )
          }
        ) { record =>
          val m = DynamicMigration(Vector.empty)
          assertTrue(m(record) == Right(record))
        }
      }
    )
}
