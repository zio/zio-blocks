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
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue                       = DynamicValue.Primitive(PrimitiveValue.Int(n))
  private def stringVal(s: String): DynamicValue                 = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def personRecord(name: String, age: Int): DynamicValue =
    DynamicValue.Record(
      Chunk(
        "name" -> stringVal(name),
        "age"  -> intVal(age)
      )
    )

  private def defaultIntExpr: SchemaExpr[_, _] =
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int"))

  private val genDynamicOpticSimple: Gen[Any, DynamicOptic] = Gen.elements(
    DynamicOptic.root,
    DynamicOptic.root.field("a"),
    DynamicOptic.root.field("a").field("b"),
    DynamicOptic.root.elements,
    DynamicOptic.root.field("m").mapKeys
  )

  private val genCaseOptic: Gen[Any, DynamicOptic] = Gen.elements(
    DynamicOptic.root.caseOf("Foo"),
    DynamicOptic.root.caseOf("Bar"),
    DynamicOptic.root.field("v").caseOf("Baz")
  )

  private val genSchemaRepr: Gen[Any, SchemaRepr] = Gen.elements(
    SchemaRepr.Primitive("int"),
    SchemaRepr.Primitive("string"),
    SchemaRepr.Optional(SchemaRepr.Primitive("int"))
  )

  private val genDefaultExpr: Gen[Any, SchemaExpr[_, _]] = Gen.elements(
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int")),
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("string"))
  )

  // Leaf-action generators — no recursive variant; covers 11 of 12 (TransformCase added below).
  private val genLeafAction: Gen[Any, MigrationAction] = Gen.oneOf(
    // Phase-3 leaf variants (5):
    for { at <- genDynamicOpticSimple; name <- Gen.alphaNumericString; d <- genDefaultExpr } yield MigrationAction
      .AddField(at, name, d),
    for { at <- genDynamicOpticSimple; name <- Gen.alphaNumericString; d <- genDefaultExpr } yield MigrationAction
      .DropField(at, name, d),
    for { at <- genDynamicOpticSimple; s <- Gen.alphaNumericString } yield MigrationAction.Rename(at.field("orig"), s),
    for { at <- genDynamicOpticSimple; d <- genDefaultExpr } yield MigrationAction.TransformValue(at, d),
    for { at <- genDynamicOpticSimple; d <- genDefaultExpr } yield MigrationAction.ChangeType(at, d),
    // Phase-4 leaf variants (6 — TransformCase handled below):
    // Mandate: use DefaultValue(at, s) so reverse->Optionalize(at,s)->reverse->Mandate(at,DefaultValue(at,s)) == original
    for { at <- genDynamicOpticSimple; s <- genSchemaRepr } yield MigrationAction
      .Mandate(at, SchemaExpr.DefaultValue(at, s)),
    for { at <- genDynamicOpticSimple; s <- genSchemaRepr } yield MigrationAction.Optionalize(at, s),
    for { at <- genCaseOptic; from <- Gen.alphaNumericString } yield MigrationAction.RenameCase(at, from, from + "New"),
    for { at <- genDynamicOpticSimple; d <- genDefaultExpr } yield MigrationAction.TransformElements(at, d),
    for { at <- genDynamicOpticSimple; d <- genDefaultExpr } yield MigrationAction.TransformKeys(at, d),
    for { at <- genDynamicOpticSimple; d <- genDefaultExpr } yield MigrationAction.TransformValues(at, d),
    // Phase-5 leaf variants (2):
    for { at <- genDynamicOpticSimple; d <- genDefaultExpr } yield MigrationAction.Join(at, Chunk.empty, d),
    for { at <- genDynamicOpticSimple; d <- genDefaultExpr } yield MigrationAction.Split(at, Chunk.empty, d)
  )

  // Top-level generator — adds TransformCase wrapping a non-empty Chunk of leaf actions (depth 1 sufficient for ).
  private val genMigrationAction: Gen[Any, MigrationAction] = Gen.oneOf(
    genLeafAction,
    for { at <- genCaseOptic; xs <- Gen.listOfBounded(1, 3)(genLeafAction) } yield MigrationAction.TransformCase(
      at,
      Chunk.from(xs)
    )
  )

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    suite("++ / empty")(
      test("empty is left identity for ++") {
        val m = new DynamicMigration(
          Chunk.single(MigrationAction.Rename(DynamicOptic.root.field("a"), "b"))
        )
        val combined = DynamicMigration.empty ++ m
        assertTrue(combined.actions == m.actions)
      },
      test("empty is right identity for ++") {
        val m = new DynamicMigration(
          Chunk.single(MigrationAction.Rename(DynamicOptic.root.field("a"), "b"))
        )
        val combined = m ++ DynamicMigration.empty
        assertTrue(combined.actions == m.actions)
      },
      test("empty is identity for apply") {
        val original = personRecord("Alice", 30)
        val result   = DynamicMigration.empty(original)
        assertTrue(result == Right(original))
      },
      test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val m1    = new DynamicMigration(Chunk.single(MigrationAction.Rename(DynamicOptic.root.field("a"), "x")))
        val m2    = new DynamicMigration(Chunk.single(MigrationAction.Rename(DynamicOptic.root.field("b"), "y")))
        val m3    = new DynamicMigration(Chunk.single(MigrationAction.Rename(DynamicOptic.root.field("c"), "z")))
        val left  = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)
        assertTrue(left.actions == right.actions)
      }
    ),
    suite("reverse")(
      test("reverse reverses both the action sequence and each action's reverse") {
        val a1        = MigrationAction.Rename(DynamicOptic.root.field("x"), "y")
        val a2        = MigrationAction.DropField(DynamicOptic.root, "z", defaultIntExpr)
        val m         = new DynamicMigration(Chunk(a1, a2))
        val mReversed = m.reverse
        assertTrue(mReversed.actions.length == 2) &&
        assertTrue(mReversed.actions(0) == a2.reverse) &&
        assertTrue(mReversed.actions(1) == a1.reverse)
      },
      test("reverse.reverse == m (structural reverse-involution,  >= 1-step)") {
        val a1 = MigrationAction.AddField(DynamicOptic.root, "x", defaultIntExpr)
        val a2 = MigrationAction.TransformValue(
          DynamicOptic.root.field("y"),
          SchemaExpr.Literal[DynamicValue, Int](42, Schema[Int])
        )
        val m = new DynamicMigration(Chunk(a1, a2))
        assertTrue(m.reverse.reverse == m)
      },
      test(" spot-check: m.reverse.reverse == m at 100 samples for all 14 variants") {
        check(genMigrationAction)(a => assertTrue(a.reverse.reverse == a))
      }
    ),
    suite("apply")(
      test("apply short-circuits on first Left — subsequent actions are not evaluated") {
        val ok           = MigrationAction.Rename(DynamicOptic.root.field("age"), "years")
        val fail         = MigrationAction.DropField(DynamicOptic.root, "nonexistent", defaultIntExpr)
        val neverReached = MigrationAction.Rename(DynamicOptic.root.field("years"), "unused")
        val original     = personRecord("Alice", 30)
        val m            = new DynamicMigration(Chunk(ok, fail, neverReached))
        val result       = m.apply(original)
        assertTrue(result.isLeft) &&
        assertTrue(result.swap.toOption.exists(_.isInstanceOf[MigrationError.MissingField]))
      },
      test("apply runs actions left-to-right on the success path") {
        val r1       = MigrationAction.Rename(DynamicOptic.root.field("age"), "years")
        val r2       = MigrationAction.Rename(DynamicOptic.root.field("years"), "months")
        val original = personRecord("Alice", 30)
        val m        = new DynamicMigration(Chunk(r1, r2))
        val result   = m.apply(original)
        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.flatMap(_.get(DynamicOptic.root.field("months")).values.flatMap(_.headOption))
            == Some(intVal(30))
        )
      }
    )
  )
}
