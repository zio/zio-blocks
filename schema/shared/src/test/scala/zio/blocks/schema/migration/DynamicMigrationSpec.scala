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

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.migration.DynamicMigrationExpr._
import zio.blocks.schema.migration.MigrationAction._
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test helpers
  // ─────────────────────────────────────────────────────────────────────────

  def intVal(n: Int): DynamicValue    = new DynamicValue.Primitive(new PrimitiveValue.Int(n))
  def longVal(n: Long): DynamicValue  = new DynamicValue.Primitive(new PrimitiveValue.Long(n))
  def strVal(s: String): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.String(s))
  def boolVal(b: Boolean): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Boolean(b))
  def dblVal(d: Double): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Double(d))

  def record(fields: (String, DynamicValue)*): DynamicValue =
    new DynamicValue.Record(Chunk.from(fields))

  def variant(caseName: String, value: DynamicValue): DynamicValue =
    new DynamicValue.Variant(caseName, value)

  def seq(values: DynamicValue*): DynamicValue =
    new DynamicValue.Sequence(Chunk.from(values))

  def kvMap(entries: (DynamicValue, DynamicValue)*): DynamicValue =
    new DynamicValue.Map(Chunk.from(entries))

  def fieldPath(name: String): DynamicOptic  = DynamicOptic.root.field(name)
  def nestedPath(a: String, b: String): DynamicOptic = DynamicOptic.root.field(a).field(b)

  def migration(actions: MigrationAction*): DynamicMigration =
    new DynamicMigration(actions.toVector)

  // ─────────────────────────────────────────────────────────────────────────
  // Spec
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[Any, Any] = suite("DynamicMigrationSpec")(
    // ───────────────── Identity & Composition ─────────────────
    suite("Identity and composition")(
      test("empty migration is identity") {
        val value  = record("name" -> strVal("Alice"), "age" -> intVal(30))
        val result = DynamicMigration.empty(value)
        assertTrue(result == Right(value))
      },
      test("identity law: empty migration applied to any value returns that value") {
        val values = Seq(
          intVal(42),
          strVal("hello"),
          DynamicValue.Null,
          record("x" -> intVal(1)),
          seq(intVal(1), intVal(2))
        )
        val allPass = values.forall { v => DynamicMigration.empty(v) == Right(v) }
        assertTrue(allPass)
      },
      test("associativity law: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val value = record("a" -> intVal(1), "b" -> strVal("hi"), "c" -> boolVal(true))
        val m1    = migration(Rename(fieldPath("a"), "x"))
        val m2    = migration(Rename(fieldPath("b"), "y"))
        val m3    = migration(Rename(fieldPath("c"), "z"))
        val left  = ((m1 ++ m2) ++ m3)(value)
        val right = (m1 ++ (m2 ++ m3))(value)
        assertTrue(left == right)
      },
      test("empty migration composed with non-empty is non-empty") {
        val m      = migration(Rename(fieldPath("a"), "b"))
        val value  = record("a" -> intVal(1))
        val result = (DynamicMigration.empty ++ m)(value)
        assertTrue(result == Right(record("b" -> intVal(1))))
      },
      test("non-empty migration composed with empty is non-empty") {
        val m      = migration(Rename(fieldPath("a"), "b"))
        val value  = record("a" -> intVal(1))
        val result = (m ++ DynamicMigration.empty)(value)
        assertTrue(result == Right(record("b" -> intVal(1))))
      }
    ),
    // ───────────────── AddField ─────────────────
    suite("AddField")(
      test("adds a field to a record with default value") {
        val value  = record("name" -> strVal("Alice"))
        val m      = migration(AddField(fieldPath("age"), intVal(0)))
        val result = m(value)
        assertTrue(result == Right(record("name" -> strVal("Alice"), "age" -> intVal(0))))
      },
      test("AddField is idempotent when field already exists") {
        val value  = record("name" -> strVal("Alice"), "age" -> intVal(30))
        val m      = migration(AddField(fieldPath("age"), intVal(0)))
        val result = m(value)
        // Should not change existing field
        assertTrue(result == Right(value))
      },
      test("AddField fails on non-record") {
        val value  = intVal(42)
        val m      = migration(AddField(fieldPath("age"), intVal(0)))
        val result = m(value)
        assertTrue(result.isLeft)
      },
      test("AddField at nested path") {
        val inner  = record("street" -> strVal("Main St"))
        val value  = record("address" -> inner)
        val m      = migration(AddField(nestedPath("address", "city"), strVal("NYC")))
        val result = m(value)
        val expected = record("address" -> record("street" -> strVal("Main St"), "city" -> strVal("NYC")))
        assertTrue(result == Right(expected))
      }
    ),
    // ───────────────── DropField ─────────────────
    suite("DropField")(
      test("drops a field from a record") {
        val value  = record("name" -> strVal("Alice"), "email" -> strVal("alice@example.com"))
        val m      = migration(DropField(fieldPath("email"), strVal("")))
        val result = m(value)
        assertTrue(result == Right(record("name" -> strVal("Alice"))))
      },
      test("DropField is idempotent when field does not exist") {
        val value  = record("name" -> strVal("Alice"))
        val m      = migration(DropField(fieldPath("email"), strVal("")))
        val result = m(value)
        assertTrue(result == Right(value))
      },
      test("DropField reverse adds the field back") {
        val value  = record("name" -> strVal("Alice"), "email" -> strVal("alice@example.com"))
        val m      = migration(DropField(fieldPath("email"), strVal("default@example.com")))
        val dropped = m(value).getOrElse(sys.error("unexpected"))
        val restored = m.reverse(dropped).getOrElse(sys.error("unexpected"))
        // Should restore field with the stored default
        assertTrue(restored == record("name" -> strVal("Alice"), "email" -> strVal("default@example.com")))
      },
      test("DropField fails on non-record") {
        val value  = strVal("hello")
        val m      = migration(DropField(fieldPath("x"), intVal(0)))
        val result = m(value)
        assertTrue(result.isLeft)
      }
    ),
    // ───────────────── Rename ─────────────────
    suite("Rename")(
      test("renames a field in a record") {
        val value  = record("firstName" -> strVal("John"), "lastName" -> strVal("Doe"))
        val m      = migration(Rename(fieldPath("firstName"), "fullName"))
        val result = m(value)
        // firstName is now fullName; lastName stays
        val expected = record("fullName" -> strVal("John"), "lastName" -> strVal("Doe"))
        assertTrue(result == Right(expected))
      },
      test("Rename structural reverse swaps back") {
        val value    = record("firstName" -> strVal("John"), "lastName" -> strVal("Doe"))
        val m        = migration(Rename(fieldPath("firstName"), "fullName"))
        val renamed  = m(value).getOrElse(sys.error("unexpected"))
        val original = m.reverse(renamed).getOrElse(sys.error("unexpected"))
        assertTrue(original == value)
      },
      test("Rename reverse.reverse is structurally equal") {
        val m = migration(Rename(fieldPath("a"), "b"))
        assertTrue(m.reverse.reverse == m)
      },
      test("Rename fails when source field does not exist") {
        val value  = record("x" -> intVal(1))
        val m      = migration(Rename(fieldPath("y"), "z"))
        val result = m(value)
        assertTrue(result.isLeft)
      },
      test("Rename at nested path") {
        val inner  = record("firstName" -> strVal("John"))
        val value  = record("person" -> inner)
        val m      = migration(Rename(nestedPath("person", "firstName"), "fullName"))
        val result = m(value)
        val expected = record("person" -> record("fullName" -> strVal("John")))
        assertTrue(result == Right(expected))
      }
    ),
    // ───────────────── TransformValue ─────────────────
    suite("TransformValue")(
      test("transforms Int to String") {
        val value  = record("age" -> intVal(42))
        val m      = migration(TransformValue(fieldPath("age"), IntToString))
        val result = m(value)
        assertTrue(result == Right(record("age" -> strVal("42"))))
      },
      test("transforms String to Int") {
        val value  = record("count" -> strVal("7"))
        val m      = migration(TransformValue(fieldPath("count"), StringToInt))
        val result = m(value)
        assertTrue(result == Right(record("count" -> intVal(7))))
      },
      test("transforms Int to Long") {
        val value  = record("n" -> intVal(100))
        val m      = migration(TransformValue(fieldPath("n"), IntToLong))
        val result = m(value)
        assertTrue(result == Right(record("n" -> longVal(100L))))
      },
      test("transforms Long to Int") {
        val value  = record("n" -> longVal(100L))
        val m      = migration(TransformValue(fieldPath("n"), LongToInt))
        val result = m(value)
        assertTrue(result == Right(record("n" -> intVal(100))))
      },
      test("transforms Boolean to String") {
        val value  = record("flag" -> boolVal(true))
        val m      = migration(TransformValue(fieldPath("flag"), BooleanToString))
        val result = m(value)
        assertTrue(result == Right(record("flag" -> strVal("true"))))
      },
      test("StringToInt fails on non-numeric string") {
        val value  = record("n" -> strVal("not-a-number"))
        val m      = migration(TransformValue(fieldPath("n"), StringToInt))
        val result = m(value)
        assertTrue(result.isLeft)
      },
      test("TransformValue error includes path information") {
        val value  = record("n" -> strVal("bad"))
        val m      = migration(TransformValue(fieldPath("n"), StringToInt))
        val result = m(value)
        result match {
          case Left(err) => assertTrue(err.path.toString.contains("n"))
          case Right(_)  => assertTrue(false)
        }
      },
      test("Identity expression is no-op") {
        val value  = record("x" -> intVal(5))
        val m      = migration(TransformValue(fieldPath("x"), Identity))
        val result = m(value)
        assertTrue(result == Right(value))
      },
      test("Constant expression replaces with literal") {
        val value  = record("x" -> intVal(99))
        val m      = migration(TransformValue(fieldPath("x"), Constant(strVal("replaced"))))
        val result = m(value)
        assertTrue(result == Right(record("x" -> strVal("replaced"))))
      },
      test("Compose chains two expressions") {
        val value  = record("n" -> intVal(42))
        val expr   = IntToString.andThen(Constant(strVal("overridden")))
        val m      = migration(TransformValue(fieldPath("n"), expr))
        val result = m(value)
        assertTrue(result == Right(record("n" -> strVal("overridden"))))
      }
    ),
    // ───────────────── Mandate / Optionalize ─────────────────
    suite("Mandate and Optionalize")(
      test("Mandate unwraps Some variant") {
        val value  = record("name" -> variant("Some", strVal("Alice")))
        val m      = migration(Mandate(fieldPath("name"), strVal("default")))
        val result = m(value)
        assertTrue(result == Right(record("name" -> strVal("Alice"))))
      },
      test("Mandate uses default for None variant") {
        val value  = record("name" -> variant("None", DynamicValue.Null))
        val m      = migration(Mandate(fieldPath("name"), strVal("default")))
        val result = m(value)
        assertTrue(result == Right(record("name" -> strVal("default"))))
      },
      test("Optionalize wraps non-optional value in Some") {
        val value  = record("name" -> strVal("Alice"))
        val m      = migration(Optionalize(fieldPath("name")))
        val result = m(value)
        assertTrue(result == Right(record("name" -> variant("Some", strVal("Alice")))))
      },
      test("Optionalize wraps Null in None") {
        val value  = record("name" -> DynamicValue.Null)
        val m      = migration(Optionalize(fieldPath("name")))
        val result = m(value)
        assertTrue(result == Right(record("name" -> variant("None", DynamicValue.Null))))
      },
      test("Optionalize is idempotent for already-optional values") {
        val value  = record("name" -> variant("Some", strVal("Alice")))
        val m      = migration(Optionalize(fieldPath("name")))
        val result = m(value)
        assertTrue(result == Right(value))
      }
    ),
    // ───────────────── RenameCase ─────────────────
    suite("RenameCase")(
      test("renames a variant case") {
        val value  = variant("OldName", intVal(42))
        val m      = migration(RenameCase(DynamicOptic.root, "OldName", "NewName"))
        val result = m(value)
        assertTrue(result == Right(variant("NewName", intVal(42))))
      },
      test("RenameCase passes through non-matching cases") {
        val value  = variant("Other", strVal("x"))
        val m      = migration(RenameCase(DynamicOptic.root, "OldName", "NewName"))
        val result = m(value)
        assertTrue(result == Right(value))
      },
      test("RenameCase reverse renames back") {
        val value   = variant("OldName", intVal(42))
        val m       = migration(RenameCase(DynamicOptic.root, "OldName", "NewName"))
        val renamed  = m(value).getOrElse(sys.error("unexpected"))
        val restored = m.reverse(renamed).getOrElse(sys.error("unexpected"))
        assertTrue(restored == value)
      },
      test("RenameCase at nested field path") {
        val value  = record("payment" -> variant("CreditCard", record("number" -> strVal("1234"))))
        val m      = migration(RenameCase(fieldPath("payment"), "CreditCard", "Card"))
        val result = m(value)
        val expected = record("payment" -> variant("Card", record("number" -> strVal("1234"))))
        assertTrue(result == Right(expected))
      }
    ),
    // ───────────────── TransformCase ─────────────────
    suite("TransformCase")(
      test("transforms inner value of matching case") {
        val inner  = record("street" -> strVal("Main St"), "zip" -> strVal("10001"))
        val value  = variant("Home", inner)
        val subActions = Vector(Rename(fieldPath("zip"), "postalCode"))
        val m      = migration(TransformCase(DynamicOptic.root, "Home", subActions))
        val result = m(value)
        val expected = variant("Home", record("street" -> strVal("Main St"), "postalCode" -> strVal("10001")))
        assertTrue(result == Right(expected))
      },
      test("TransformCase passes through non-matching cases") {
        val value  = variant("Work", record("floor" -> intVal(3)))
        val subActions = Vector(Rename(fieldPath("zip"), "postalCode"))
        val m      = migration(TransformCase(DynamicOptic.root, "Home", subActions))
        val result = m(value)
        assertTrue(result == Right(value))
      },
      test("TransformCase reverse reverses nested actions") {
        val inner  = record("street" -> strVal("Main St"), "zip" -> strVal("10001"))
        val value  = variant("Home", inner)
        val subActions = Vector(Rename(fieldPath("zip"), "postalCode"))
        val m      = migration(TransformCase(DynamicOptic.root, "Home", subActions))
        val transformed = m(value).getOrElse(sys.error("unexpected"))
        val restored    = m.reverse(transformed).getOrElse(sys.error("unexpected"))
        assertTrue(restored == value)
      }
    ),
    // ───────────────── TransformElements ─────────────────
    suite("TransformElements")(
      test("transforms each element in a sequence") {
        val value  = seq(intVal(1), intVal(2), intVal(3))
        val m      = migration(TransformElements(DynamicOptic.root, IntToLong))
        val result = m(value)
        assertTrue(result == Right(seq(longVal(1L), longVal(2L), longVal(3L))))
      },
      test("TransformElements on a sequence field") {
        val value  = record("nums" -> seq(intVal(1), intVal(2)))
        val m      = migration(TransformElements(fieldPath("nums"), IntToString))
        val result = m(value)
        val expected = record("nums" -> seq(strVal("1"), strVal("2")))
        assertTrue(result == Right(expected))
      },
      test("TransformElements reverse transforms back") {
        val value   = seq(intVal(1), intVal(2), intVal(3))
        val m       = migration(TransformElements(DynamicOptic.root, IntToLong))
        val forward = m(value).getOrElse(sys.error("unexpected"))
        val back    = m.reverse(forward).getOrElse(sys.error("unexpected"))
        assertTrue(back == value)
      },
      test("TransformElements fails on non-sequence") {
        val value  = record("x" -> intVal(1))
        val m      = migration(TransformElements(DynamicOptic.root, IntToLong))
        val result = m(value)
        assertTrue(result.isLeft)
      }
    ),
    // ───────────────── TransformKeys / TransformValues ─────────────────
    suite("TransformKeys and TransformValues")(
      test("transforms map keys") {
        val value  = kvMap(intVal(1) -> strVal("a"), intVal(2) -> strVal("b"))
        val m      = migration(TransformKeys(DynamicOptic.root, IntToString))
        val result = m(value)
        val expected = kvMap(strVal("1") -> strVal("a"), strVal("2") -> strVal("b"))
        assertTrue(result == Right(expected))
      },
      test("transforms map values") {
        val value  = kvMap(strVal("x") -> intVal(10), strVal("y") -> intVal(20))
        val m      = migration(TransformValues(DynamicOptic.root, IntToLong))
        val result = m(value)
        val expected = kvMap(strVal("x") -> longVal(10L), strVal("y") -> longVal(20L))
        assertTrue(result == Right(expected))
      },
      test("TransformValues reverse transforms back") {
        val value   = kvMap(strVal("x") -> intVal(10), strVal("y") -> intVal(20))
        val m       = migration(TransformValues(DynamicOptic.root, IntToLong))
        val forward = m(value).getOrElse(sys.error("unexpected"))
        val back    = m.reverse(forward).getOrElse(sys.error("unexpected"))
        assertTrue(back == value)
      },
      test("TransformKeys fails on non-map") {
        val value  = seq(intVal(1), intVal(2))
        val m      = migration(TransformKeys(DynamicOptic.root, IntToString))
        val result = m(value)
        assertTrue(result.isLeft)
      }
    ),
    // ───────────────── Reverse laws ─────────────────
    suite("Reverse laws")(
      test("structural reverse of reverse is identity for Rename") {
        val m  = migration(Rename(fieldPath("a"), "b"))
        assertTrue(m.reverse.reverse == m)
      },
      test("structural reverse of reverse is identity for RenameCase") {
        val m = migration(RenameCase(DynamicOptic.root, "Old", "New"))
        assertTrue(m.reverse.reverse == m)
      },
      test("structural reverse of reverse is identity for AddField") {
        val m = migration(AddField(fieldPath("x"), intVal(0)))
        assertTrue(m.reverse.reverse == m)
      },
      test("structural reverse of reverse is identity for DropField") {
        val m = migration(DropField(fieldPath("x"), intVal(0)))
        assertTrue(m.reverse.reverse == m)
      },
      test("reverse of empty migration is empty") {
        assertTrue(DynamicMigration.empty.reverse == DynamicMigration.empty)
      },
      test("semantic inverse: rename roundtrip") {
        val value   = record("name" -> strVal("Alice"), "age" -> intVal(30))
        val m       = migration(Rename(fieldPath("name"), "fullName"))
        val forward = m(value).getOrElse(sys.error("unexpected"))
        val back    = m.reverse(forward).getOrElse(sys.error("unexpected"))
        assertTrue(back == value)
      },
      test("semantic inverse: addField + dropField roundtrip") {
        val value   = record("name" -> strVal("Alice"))
        val m       = migration(AddField(fieldPath("age"), intVal(0)))
        val forward = m(value).getOrElse(sys.error("unexpected"))
        val back    = m.reverse(forward).getOrElse(sys.error("unexpected"))
        assertTrue(back == value)
      }
    ),
    // ───────────────── Error handling ─────────────────
    suite("Error handling")(
      test("errors include path information for missing field") {
        val value  = record("x" -> intVal(1))
        val m      = migration(Rename(fieldPath("y"), "z"))
        m(value) match {
          case Left(err) => assertTrue(err.path.toString.contains("y"))
          case Right(_)  => assertTrue(false)
        }
      },
      test("errors include path information for nested missing field") {
        val value = record("person" -> record("name" -> strVal("Alice")))
        val m     = migration(Rename(nestedPath("person", "email"), "emailAddress"))
        m(value) match {
          case Left(err) =>
            assertTrue(err.message.nonEmpty)
          case Right(_) => assertTrue(false)
        }
      },
      test("type mismatch error for Rename on non-record") {
        val value  = intVal(42)
        val m      = migration(Rename(fieldPath("x"), "y"))
        val result = m(value)
        assertTrue(result.isLeft)
      }
    ),
    // ───────────────── Complex/compound migrations ─────────────────
    suite("Complex migrations")(
      test("compose rename + addField migration") {
        val value  = record("firstName" -> strVal("John"), "lastName" -> strVal("Doe"))
        val m      = migration(
          Rename(fieldPath("firstName"), "fullName"),
          AddField(fieldPath("age"), intVal(0))
        )
        val result = m(value)
        val expected = record("fullName" -> strVal("John"), "lastName" -> strVal("Doe"), "age" -> intVal(0))
        assertTrue(result == Right(expected))
      },
      test("multiple renames preserve other fields") {
        val value = record("a" -> intVal(1), "b" -> intVal(2), "c" -> intVal(3))
        val m     = migration(
          Rename(fieldPath("a"), "x"),
          Rename(fieldPath("b"), "y")
        )
        val result = m(value)
        val expected = record("x" -> intVal(1), "y" -> intVal(2), "c" -> intVal(3))
        assertTrue(result == Right(expected))
      },
      test("rename + drop field") {
        val value  = record("name" -> strVal("Alice"), "temp" -> strVal("junk"))
        val m      = migration(
          Rename(fieldPath("name"), "fullName"),
          DropField(fieldPath("temp"), strVal(""))
        )
        val result = m(value)
        assertTrue(result == Right(record("fullName" -> strVal("Alice"))))
      },
      test("ConcatFields expression concatenates record fields") {
        val value  = record("firstName" -> strVal("John"), "lastName" -> strVal("Doe"))
        val expr   = ConcatFields(Vector("firstName", "lastName"), " ")
        // Apply the expression directly to the record
        val result = expr(value)
        assertTrue(result == Right(strVal("John Doe")))
      }
    )
  )
}
