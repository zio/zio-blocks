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
import zio.blocks.schema.{DynamicValue, PrimitiveValue, SchemaBaseSpec}
import zio.test._
import zio.test.Assertion._

object MigrationSpec extends SchemaBaseSpec {

  // ── Helpers ────────────────────────────────────────────────────────────────

  private val pInt: Int => DynamicValue =
    n => DynamicValue.Primitive(new PrimitiveValue.Int(n))

  private val pLong: Long => DynamicValue =
    n => DynamicValue.Primitive(new PrimitiveValue.Long(n))

  private val pStr: String => DynamicValue =
    s => DynamicValue.Primitive(new PrimitiveValue.String(s))

  private def record(fields: (String, DynamicValue)*): DynamicValue.Record =
    DynamicValue.Record(Chunk.from(fields))

  private def variant(caseName: String, value: DynamicValue): DynamicValue.Variant =
    DynamicValue.Variant(caseName, value)

  private def someOf(v: DynamicValue): DynamicValue =
    variant("Some", record("value" -> v))

  private val noneVal: DynamicValue = variant("None", record())

  // ── Spec ───────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationSpec")(
      suite("Migration.interpreter")(
        suite("Identity")(
          test("passes the value through unchanged") {
            val dv = record("x" -> pInt(1))
            assertZIO(
              zio.ZIO.fromEither(Migration.applyAction(dv, MigrationAction.Identity))
            )(equalTo(dv))
          }
        ),
        suite("DropField")(
          test("removes the named field from a top-level record") {
            val dv     = record("name" -> pStr("Alice"), "age" -> pInt(30))
            val result = Migration.applyAction(dv, MigrationAction.DropField(List("name")))
            assert(result)(isRight(equalTo(record("age" -> pInt(30)))))
          },
          test("fails with empty path") {
            val dv     = record("x" -> pInt(1))
            val result = Migration.applyAction(dv, MigrationAction.DropField(Nil))
            assert(result)(isLeft)
          },
          test("navigates into nested record before dropping") {
            val dv =
              record("addr" -> record("street" -> pStr("Main St"), "zip" -> pStr("12345")))
            val result =
              Migration.applyAction(dv, MigrationAction.DropField(List("addr", "zip")))
            assert(result)(isRight(equalTo(record("addr" -> record("street" -> pStr("Main St"))))))
          }
        ),
        suite("AddField")(
          test("appends a new field to a top-level record") {
            val dv     = record("age" -> pInt(30))
            val result = Migration.applyAction(dv, MigrationAction.AddField(List("name"), pStr("Bob")))
            assert(result)(isRight(equalTo(record("age" -> pInt(30), "name" -> pStr("Bob")))))
          },
          test("fails with empty path") {
            val result = Migration.applyAction(record(), MigrationAction.AddField(Nil, pStr("x")))
            assert(result)(isLeft)
          }
        ),
        suite("RenameField")(
          test("renames a top-level field") {
            val dv     = record("name" -> pStr("Charlie"), "age" -> pInt(25))
            val result = Migration.applyAction(
              dv,
              MigrationAction.RenameField(List("name"), List("fullName"))
            )
            assert(result)(isRight(equalTo(record("fullName" -> pStr("Charlie"), "age" -> pInt(25)))))
          },
          test("fails when source field is missing") {
            val dv     = record("age" -> pInt(25))
            val result = Migration.applyAction(
              dv,
              MigrationAction.RenameField(List("name"), List("fullName"))
            )
            assert(result)(isLeft)
          },
          test("fails with empty path") {
            val result = Migration.applyAction(record(), MigrationAction.RenameField(Nil, Nil))
            assert(result)(isLeft)
          }
        ),
        suite("TransformValue")(
          test("IntToLong widens an Int field") {
            val dv     = record("count" -> pInt(42))
            val result = Migration.applyAction(
              dv,
              MigrationAction.TransformValue(List("count"), FieldTransform.IntToLong)
            )
            assert(result)(isRight(equalTo(record("count" -> pLong(42L)))))
          },
          test("LongToInt truncates a Long field") {
            val dv     = record("n" -> pLong(7L))
            val result = Migration.applyAction(
              dv,
              MigrationAction.TransformValue(List("n"), FieldTransform.LongToInt)
            )
            assert(result)(isRight(equalTo(record("n" -> pInt(7)))))
          },
          test("IntToDouble widens an Int field") {
            val dv     = record("x" -> pInt(3))
            val result = Migration.applyAction(
              dv,
              MigrationAction.TransformValue(List("x"), FieldTransform.IntToDouble)
            )
            assert(result)(
              isRight(
                equalTo(record("x" -> DynamicValue.Primitive(new PrimitiveValue.Double(3.0))))
              )
            )
          },
          test("LongToDouble widens a Long field") {
            val dv     = record("x" -> pLong(5L))
            val result = Migration.applyAction(
              dv,
              MigrationAction.TransformValue(List("x"), FieldTransform.LongToDouble)
            )
            assert(result)(
              isRight(
                equalTo(record("x" -> DynamicValue.Primitive(new PrimitiveValue.Double(5.0))))
              )
            )
          },
          test("IntToString converts Int to decimal string") {
            val dv     = record("code" -> pInt(255))
            val result = Migration.applyAction(
              dv,
              MigrationAction.TransformValue(List("code"), FieldTransform.IntToString(16))
            )
            assert(result)(isRight(equalTo(record("code" -> pStr("ff")))))
          },
          test("StringToInt parses a decimal string") {
            val dv     = record("num" -> pStr("42"))
            val result = Migration.applyAction(
              dv,
              MigrationAction.TransformValue(List("num"), FieldTransform.StringToInt(10))
            )
            assert(result)(isRight(equalTo(record("num" -> pInt(42)))))
          },
          test("StringToInt fails on non-numeric string") {
            val dv     = record("num" -> pStr("abc"))
            val result = Migration.applyAction(
              dv,
              MigrationAction.TransformValue(List("num"), FieldTransform.StringToInt(10))
            )
            assert(result)(isLeft)
          },
          test("Constant replaces any value") {
            val dv     = record("flag" -> pInt(0))
            val result = Migration.applyAction(
              dv,
              MigrationAction.TransformValue(List("flag"), FieldTransform.Constant(pStr("yes")))
            )
            assert(result)(isRight(equalTo(record("flag" -> pStr("yes")))))
          }
        ),
        suite("RenameCase")(
          test("renames a matching variant case") {
            val dv     = variant("Active", record("since" -> pStr("2020")))
            val result = Migration.applyAction(
              dv,
              MigrationAction.RenameCase(List("Active"), List("Enabled"))
            )
            assert(result)(isRight(equalTo(variant("Enabled", record("since" -> pStr("2020"))))))
          },
          test("is a no-op for a non-matching case") {
            val dv     = variant("Inactive", record())
            val result = Migration.applyAction(
              dv,
              MigrationAction.RenameCase(List("Active"), List("Enabled"))
            )
            assert(result)(isRight(equalTo(dv)))
          }
        ),
        suite("DropCase")(
          test("fails when the current case matches") {
            val dv     = variant("Legacy", record())
            val result = Migration.applyAction(dv, MigrationAction.DropCase(List("Legacy")))
            assert(result)(isLeft)
          },
          test("is a no-op for a non-matching case") {
            val dv     = variant("Current", record())
            val result = Migration.applyAction(dv, MigrationAction.DropCase(List("Legacy")))
            assert(result)(isRight(equalTo(dv)))
          }
        ),
        suite("Mandate")(
          test("unwraps Some(v) to v") {
            val inner  = pStr("hello")
            val dv     = record("name" -> someOf(inner))
            val result = Migration.applyAction(
              dv,
              MigrationAction.Mandate(List("name"), pStr("default"))
            )
            assert(result)(isRight(equalTo(record("name" -> inner))))
          },
          test("substitutes default for None") {
            val dv     = record("name" -> noneVal)
            val result = Migration.applyAction(
              dv,
              MigrationAction.Mandate(List("name"), pStr("default"))
            )
            assert(result)(isRight(equalTo(record("name" -> pStr("default")))))
          }
        ),
        suite("Optionalize")(
          test("wraps a value in Some") {
            val dv     = record("x" -> pInt(1))
            val result = Migration.applyAction(dv, MigrationAction.Optionalize(List("x")))
            assert(result)(isRight(equalTo(record("x" -> someOf(pInt(1))))))
          }
        ),
        suite("TransformElements")(
          test("applies action to every sequence element") {
            val dv = DynamicValue.Sequence(Chunk(pInt(1), pInt(2), pInt(3)))
            val result = Migration.applyAction(
              dv,
              MigrationAction.TransformElements(Nil, MigrationAction.TransformValue(Nil, FieldTransform.IntToLong))
            )
            assert(result)(
              isRight(equalTo(DynamicValue.Sequence(Chunk(pLong(1L), pLong(2L), pLong(3L)))))
            )
          },
          test("fails on non-sequence") {
            val result = Migration.applyAction(
              pInt(1),
              MigrationAction.TransformElements(Nil, MigrationAction.Identity)
            )
            assert(result)(isLeft)
          }
        ),
        suite("TransformKeys")(
          test("applies action to every map key") {
            val dv = DynamicValue.Map(
              Chunk(pInt(1) -> pStr("a"), pInt(2) -> pStr("b"))
            )
            val result = Migration.applyAction(
              dv,
              MigrationAction.TransformKeys(Nil, MigrationAction.TransformValue(Nil, FieldTransform.IntToLong))
            )
            assert(result)(
              isRight(
                equalTo(DynamicValue.Map(Chunk(pLong(1L) -> pStr("a"), pLong(2L) -> pStr("b"))))
              )
            )
          }
        ),
        suite("TransformValues")(
          test("applies action to every map value") {
            val dv = DynamicValue.Map(
              Chunk(pStr("a") -> pInt(10), pStr("b") -> pInt(20))
            )
            val result = Migration.applyAction(
              dv,
              MigrationAction.TransformValues(Nil, MigrationAction.TransformValue(Nil, FieldTransform.IntToLong))
            )
            assert(result)(
              isRight(
                equalTo(
                  DynamicValue.Map(Chunk(pStr("a") -> pLong(10L), pStr("b") -> pLong(20L)))
                )
              )
            )
          }
        ),
        suite("Sequence")(
          test("applies sub-actions left-to-right") {
            val dv = record("name" -> pStr("old"), "age" -> pInt(1))
            val result = Migration.applyAction(
              dv,
              MigrationAction.Sequence(
                List(
                  MigrationAction.RenameField(List("name"), List("fullName")),
                  MigrationAction.DropField(List("age"))
                )
              )
            )
            assert(result)(isRight(equalTo(record("fullName" -> pStr("old")))))
          }
        )
      ),
      suite("Migration.andThen")(
        test("composes two migrations sequentially") {
          val m1: Migration[Any, Any] =
            Migration(List(MigrationAction.RenameField(List("a"), List("b"))))
          val m2: Migration[Any, Any] =
            Migration(List(MigrationAction.RenameField(List("b"), List("c"))))
          val dv     = record("a" -> pInt(1))
          val result = (m1.andThen(m2)).apply(dv)
          assert(result)(isRight(equalTo(record("c" -> pInt(1)))))
        }
      ),
      suite("Migration.identity")(
        test("produces Identity action list") {
          val m = Migration.identity[Int]
          assert(m.actions)(equalTo(List(MigrationAction.Identity)))
        },
        test("is a no-op on any DynamicValue") {
          val dv = record("x" -> pInt(42))
          assert(Migration.identity[Int].apply(dv))(isRight(equalTo(dv)))
        }
      )
    )
}
