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

import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaBaseSpec}
import zio.blocks.schema.migration.MigrationAction._
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {

  // ── Helpers ─────────────────────────────────────────────────────────────────

  private def str(s: String): DynamicValue   = DynamicValue.string(s)
  private def int(n: Int): DynamicValue      = DynamicValue.int(n)
  private def bool(b: Boolean): DynamicValue = DynamicValue.boolean(b)

  private val root: DynamicOptic = DynamicOptic.root

  // ── Spec ────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    suite("Laws")(
      test("identity returns input unchanged") {
        val value = DynamicValue.Record("name" -> str("Alice"), "age" -> int(30))
        assertTrue(DynamicMigration.identity(value) == Right(value))
      },
      test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val m1 = DynamicMigration(Vector(AddField(root.field("email"), str(""))))
        val m2 = DynamicMigration(Vector(AddField(root.field("active"), bool(true))))
        val m3 = DynamicMigration(Vector(Rename(root.field("name"), "fullName")))
        assertTrue(((m1 ++ m2) ++ m3).actions == (m1 ++ (m2 ++ m3)).actions)
      },
      test("reverse.reverse == original actions") {
        val migration = DynamicMigration(
          Vector(
            AddField(root.field("email"), str("")),
            Rename(root.field("name"), "fullName"),
            RenameCase(root, "Red", "Crimson")
          )
        )
        assertTrue(migration.reverse.reverse.actions == migration.actions)
      }
    ),
    suite("AddField")(
      test("adds field with default when absent") {
        val value  = DynamicValue.Record("name" -> str("Alice"))
        val result = DynamicMigration(Vector(AddField(root.field("email"), str(""))))(value)
        assertTrue(result == Right(DynamicValue.Record("name" -> str("Alice"), "email" -> str(""))))
      },
      test("does not overwrite existing field") {
        val value  = DynamicValue.Record("name" -> str("Alice"), "email" -> str("alice@x.com"))
        val result = DynamicMigration(Vector(AddField(root.field("email"), str(""))))(value)
        assertTrue(result == Right(value))
      },
      test("reverse of AddField is DropField") {
        val action = AddField(root.field("email"), str(""))
        assertTrue(action.reverse == DropField(root.field("email"), str("")))
      }
    ),
    suite("DropField")(
      test("removes the named field") {
        val value  = DynamicValue.Record("name" -> str("Alice"), "temp" -> int(0))
        val result = DynamicMigration(Vector(DropField(root.field("temp"), int(0))))(value)
        assertTrue(result == Right(DynamicValue.Record("name" -> str("Alice"))))
      },
      test("reverse of DropField is AddField") {
        val action = DropField(root.field("temp"), int(0))
        assertTrue(action.reverse == AddField(root.field("temp"), int(0)))
      }
    ),
    suite("Rename")(
      test("renames field in place") {
        val value  = DynamicValue.Record("name" -> str("Alice"), "age" -> int(30))
        val result = DynamicMigration(Vector(Rename(root.field("name"), "fullName")))(value)
        assertTrue(result == Right(DynamicValue.Record("fullName" -> str("Alice"), "age" -> int(30))))
      },
      test("round-trip: forward then reverse restores value") {
        val value   = DynamicValue.Record("name" -> str("Alice"), "age" -> int(30))
        val fwd     = DynamicMigration(Vector(Rename(root.field("name"), "fullName")))
        val renamed = DynamicValue.Record("fullName" -> str("Alice"), "age" -> int(30))
        assertTrue(
          fwd(value) == Right(renamed) &&
            fwd.reverse(renamed) == Right(value)
        )
      },
      test("reverse Rename has swapped path and target name") {
        val action = Rename(root.field("name"), "fullName")
        val rev    = action.reverse.asInstanceOf[Rename]
        assertTrue(
          rev.at.nodes.lastOption.contains(DynamicOptic.Node.Field("fullName")) &&
            rev.to == "name"
        )
      }
    ),
    suite("TransformValue")(
      test("replaces value at path") {
        val value  = DynamicValue.Record("score" -> int(0))
        val result = DynamicMigration(Vector(TransformValue(root.field("score"), int(100), int(0))))(value)
        assertTrue(result == Right(DynamicValue.Record("score" -> int(100))))
      },
      test("reverse restores old value") {
        val action = TransformValue(root.field("score"), int(100), int(0))
        assertTrue(action.reverse == TransformValue(root.field("score"), int(0), int(100)))
      }
    ),
    suite("Mandate")(
      test("unwraps Some(x) to x") {
        val value  = DynamicValue.Record("email" -> DynamicValue.Variant("Some", str("alice@x.com")))
        val result = DynamicMigration(Vector(Mandate(root.field("email"), str(""))))(value)
        assertTrue(result == Right(DynamicValue.Record("email" -> str("alice@x.com"))))
      },
      test("uses default for Null") {
        val value  = DynamicValue.Record("email" -> DynamicValue.Null)
        val result = DynamicMigration(Vector(Mandate(root.field("email"), str("default@x.com"))))(value)
        assertTrue(result == Right(DynamicValue.Record("email" -> str("default@x.com"))))
      },
      test("uses default for Variant(_, Null) i.e. None") {
        val value  = DynamicValue.Record("email" -> DynamicValue.Variant("None", DynamicValue.Null))
        val result = DynamicMigration(Vector(Mandate(root.field("email"), str("default@x.com"))))(value)
        assertTrue(result == Right(DynamicValue.Record("email" -> str("default@x.com"))))
      },
      test("reverse of Mandate is Optionalize") {
        val action = Mandate(root.field("email"), str(""))
        assertTrue(action.reverse == Optionalize(root.field("email"), str("")))
      }
    ),
    suite("Optionalize")(
      test("wraps value in Some") {
        val value  = DynamicValue.Record("score" -> int(42))
        val result = DynamicMigration(Vector(Optionalize(root.field("score"), int(0))))(value)
        assertTrue(result == Right(DynamicValue.Record("score" -> DynamicValue.Variant("Some", int(42)))))
      },
      test("reverse of Optionalize is Mandate") {
        val action = Optionalize(root.field("score"), int(0))
        assertTrue(action.reverse == Mandate(root.field("score"), int(0)))
      }
    ),
    suite("RenameCase")(
      test("renames matching case") {
        val value  = DynamicValue.Record("color" -> DynamicValue.Variant("Red", DynamicValue.Null))
        val result = DynamicMigration(Vector(RenameCase(root.field("color"), "Red", "Crimson")))(value)
        assertTrue(result == Right(DynamicValue.Record("color" -> DynamicValue.Variant("Crimson", DynamicValue.Null))))
      },
      test("leaves non-matching case untouched") {
        val value  = DynamicValue.Record("color" -> DynamicValue.Variant("Blue", DynamicValue.Null))
        val result = DynamicMigration(Vector(RenameCase(root.field("color"), "Red", "Crimson")))(value)
        assertTrue(result == Right(value))
      },
      test("reverse swaps from and to") {
        val action = RenameCase(root.field("color"), "Red", "Crimson")
        assertTrue(action.reverse == RenameCase(root.field("color"), "Crimson", "Red"))
      }
    ),
    suite("TransformCase")(
      test("applies inner actions to matching case body") {
        val inner  = DynamicValue.Record("x" -> int(1), "y" -> int(2))
        val value  = DynamicValue.Record("shape" -> DynamicValue.Variant("Point", inner))
        val result = DynamicMigration(
          Vector(
            TransformCase(root.field("shape"), "Point", Vector(AddField(root.field("z"), int(0))))
          )
        )(value)
        val expected = DynamicValue.Record(
          "shape" -> DynamicValue.Variant("Point", DynamicValue.Record("x" -> int(1), "y" -> int(2), "z" -> int(0)))
        )
        assertTrue(result == Right(expected))
      },
      test("leaves non-matching case untouched") {
        val inner  = DynamicValue.Record("r" -> int(5))
        val value  = DynamicValue.Record("shape" -> DynamicValue.Variant("Circle", inner))
        val result = DynamicMigration(
          Vector(
            TransformCase(root.field("shape"), "Point", Vector(AddField(root.field("z"), int(0))))
          )
        )(value)
        assertTrue(result == Right(value))
      },
      test("reverse reverses inner actions in reverse order") {
        val inner = TransformCase(
          root,
          "A",
          Vector(
            AddField(root.field("x"), int(0)),
            Rename(root.field("y"), "z")
          )
        )
        val rev = inner.reverse.asInstanceOf[TransformCase]
        assertTrue(
          rev.caseName == "A" &&
            rev.actions == Vector(
              Rename(root.field("y"), "z").reverse,
              AddField(root.field("x"), int(0)).reverse
            )
        )
      }
    ),
    suite("Nested paths")(
      test("addField inside nested record") {
        val value = DynamicValue.Record(
          "name"    -> str("Alice"),
          "address" -> DynamicValue.Record("street" -> str("Main St"))
        )
        val result = DynamicMigration(
          Vector(
            AddField(root.field("address").field("city"), str("Unknown"))
          )
        )(value)
        val expected = DynamicValue.Record(
          "name"    -> str("Alice"),
          "address" -> DynamicValue.Record("street" -> str("Main St"), "city" -> str("Unknown"))
        )
        assertTrue(result == Right(expected))
      },
      test("rename inside nested record") {
        val value = DynamicValue.Record(
          "address" -> DynamicValue.Record("street" -> str("Main St"), "zip" -> str("46001"))
        )
        val result = DynamicMigration(
          Vector(
            Rename(root.field("address").field("zip"), "postalCode")
          )
        )(value)
        val expected = DynamicValue.Record(
          "address" -> DynamicValue.Record("street" -> str("Main St"), "postalCode" -> str("46001"))
        )
        assertTrue(result == Right(expected))
      }
    ),
    suite("Composition")(
      test("++ applies actions in order") {
        val value  = DynamicValue.Record("a" -> int(1))
        val m1     = DynamicMigration(Vector(AddField(root.field("b"), int(2))))
        val m2     = DynamicMigration(Vector(AddField(root.field("c"), int(3))))
        val result = (m1 ++ m2)(value)
        assertTrue(result == Right(DynamicValue.Record("a" -> int(1), "b" -> int(2), "c" -> int(3))))
      },
      test("multiple renames chain correctly") {
        val value  = DynamicValue.Record("a" -> int(1), "b" -> int(2))
        val result = DynamicMigration(
          Vector(
            Rename(root.field("a"), "x"),
            Rename(root.field("b"), "y")
          )
        )(value)
        assertTrue(result == Right(DynamicValue.Record("x" -> int(1), "y" -> int(2))))
      }
    ),
    suite("Error cases")(
      test("FieldNotFound when navigating through missing intermediate field") {
        val value  = DynamicValue.Record("name" -> str("Alice"))
        val result = DynamicMigration(
          Vector(
            AddField(root.field("address").field("city"), str("Unknown"))
          )
        )(value)
        assertTrue(result.isLeft && result.left.exists(_.isInstanceOf[MigrationError.FieldNotFound]))
      },
      test("TypeMismatch when navigating Field on non-Record") {
        val value  = DynamicValue.string("not a record")
        val result = DynamicMigration(Vector(AddField(root.field("x").field("y"), int(0))))(value)
        assertTrue(result.isLeft && result.left.exists(_.isInstanceOf[MigrationError.TypeMismatch]))
      },
      test("TypeMismatch on AddField with non-Field last node") {
        val value  = DynamicValue.Record("name" -> str("Alice"))
        val result = DynamicMigration(Vector(AddField(DynamicOptic.elements, int(0))))(value)
        assertTrue(result.isLeft && result.left.exists(_.isInstanceOf[MigrationError.TypeMismatch]))
      },
      test("stops at first error, second action not applied") {
        val value  = DynamicValue.Record("name" -> str("Alice"))
        val result = DynamicMigration(
          Vector(
            AddField(root.field("x").field("nested"), int(0)),
            AddField(root.field("email"), str(""))
          )
        )(value)
        assertTrue(result.isLeft)
      }
    ),
    suite("MigrationBuilder")(
      test("addField then rename") {
        val migration = MigrationBuilder[Any, Any]
          .addField(root.field("email"), str(""))
          .rename(root.field("name"), "fullName")
          .buildDynamic
        val value  = DynamicValue.Record("name" -> str("Alice"))
        val result = migration(value)
        assertTrue(result == Right(DynamicValue.Record("fullName" -> str("Alice"), "email" -> str(""))))
      },
      test("rename then addField") {
        val migration = MigrationBuilder[Any, Any]
          .rename(root.field("name"), "fullName")
          .addField(root.field("email"), str(""))
          .buildDynamic
        val value  = DynamicValue.Record("name" -> str("Bob"))
        val result = migration(value)
        assertTrue(result == Right(DynamicValue.Record("fullName" -> str("Bob"), "email" -> str(""))))
      },
      test("empty builder is identity") {
        val migration = MigrationBuilder[Any, Any].buildDynamic
        val value     = DynamicValue.Record("x" -> int(1))
        assertTrue(migration(value) == Right(value))
      }
    ),
    suite("Collection traversal")(
      test("Elements node applies action to all sequence elements") {
        val value = DynamicValue.Sequence(
          DynamicValue.Record("x" -> int(1)),
          DynamicValue.Record("x" -> int(2))
        )
        val result = DynamicMigration(
          Vector(
            AddField(DynamicOptic.elements.field("y"), int(0))
          )
        )(value)
        val expected = DynamicValue.Sequence(
          DynamicValue.Record("x" -> int(1), "y" -> int(0)),
          DynamicValue.Record("x" -> int(2), "y" -> int(0))
        )
        assertTrue(result == Right(expected))
      },
      test("MapValues node applies action to all map values") {
        val value = DynamicValue.Map(
          str("a") -> DynamicValue.Record("v" -> int(1)),
          str("b") -> DynamicValue.Record("v" -> int(2))
        )
        val result = DynamicMigration(
          Vector(
            AddField(DynamicOptic.mapValues.field("extra"), bool(false))
          )
        )(value)
        val expected = DynamicValue.Map(
          str("a") -> DynamicValue.Record("v" -> int(1), "extra" -> bool(false)),
          str("b") -> DynamicValue.Record("v" -> int(2), "extra" -> bool(false))
        )
        assertTrue(result == Right(expected))
      }
    )
  )
}
