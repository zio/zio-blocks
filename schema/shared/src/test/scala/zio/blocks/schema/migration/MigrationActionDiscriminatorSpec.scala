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
import zio.blocks.schema.binding.Matcher
import zio.test._

/**
 * Drives the 14 anonymous [[Matcher]]s wired into [[MigrationAction.schema]]'s
 * variantBinding — one per [[MigrationAction]] constructor. Each `Matcher`
 * implementation has two arms:
 *   - `case x: T => x`                (happy path — recorded by existing
 *     serialization / discriminator tests)
 *   - `case _ => null.asInstanceOf[T]` (null-arm — previously uncovered)
 *
 * This spec targets the null-arm by asking every matcher to downcast every
 * non-matching concrete [[MigrationAction]] instance. For 14 matchers × 14
 * actions, exactly 14 diagonal downcasts succeed; the remaining 14×13 hit the
 * `case _ => null` fall-through. The discriminator contract is also pinned
 * directly against each concrete variant.
 */
object MigrationActionDiscriminatorSpec extends SchemaBaseSpec {

  // --- Concrete instances of every MigrationAction variant ------------------

  private val defaultExpr: SchemaExpr[_, _] =
    SchemaExpr.DefaultValue(DynamicOptic.root, SchemaRepr.Primitive("int"))

  /** Instances declared in the same positional order as
   * [[MigrationAction.schema]]'s `cases` / `discriminator` / `matchers`.
   */
  private val variantInstances: IndexedSeq[(String, MigrationAction)] = IndexedSeq(
    "AddField"          -> MigrationAction.AddField(DynamicOptic.root, "f", defaultExpr),
    "DropField"         -> MigrationAction.DropField(DynamicOptic.root, "f", defaultExpr),
    "Rename"            -> MigrationAction.Rename(DynamicOptic.root.field("f"), "g"),
    "TransformValue"    -> MigrationAction.TransformValue(DynamicOptic.root, defaultExpr),
    "ChangeType"        -> MigrationAction.ChangeType(DynamicOptic.root, defaultExpr),
    "Mandate"           -> MigrationAction.Mandate(DynamicOptic.root, defaultExpr),
    "Optionalize"       -> MigrationAction.Optionalize(DynamicOptic.root, SchemaRepr.Primitive("int")),
    "RenameCase"        -> MigrationAction.RenameCase(DynamicOptic.root.caseOf("Foo"), "Foo", "Bar"),
    "TransformCase"     -> MigrationAction.TransformCase(DynamicOptic.root.caseOf("Foo"), Chunk.empty),
    "TransformElements" -> MigrationAction.TransformElements(DynamicOptic.root.elements, defaultExpr),
    "TransformKeys"     -> MigrationAction.TransformKeys(DynamicOptic.root.mapKeys, defaultExpr),
    "TransformValues"   -> MigrationAction.TransformValues(DynamicOptic.root.mapValues, defaultExpr),
    "Join"              -> MigrationAction.Join(DynamicOptic.root, Chunk.empty, defaultExpr),
    "Split"             -> MigrationAction.Split(DynamicOptic.root, Chunk.empty, defaultExpr)
  )

  // --- Derived variant reflect from MigrationAction.schema ------------------

  private val variant: Reflect.Variant[zio.blocks.schema.binding.Binding, MigrationAction] =
    Schema[MigrationAction].reflect.asInstanceOf[Reflect.Variant[zio.blocks.schema.binding.Binding, MigrationAction]]

  private val matchers: IndexedSeq[Matcher[_ <: MigrationAction]] = variant.matchers.matchers

  private val discriminator = variant.discriminator

  // --- Suite ----------------------------------------------------------------

  def spec: Spec[TestEnvironment, Any] = suite("MigrationActionDiscriminatorSpec")(
    test("MigrationAction.schema wires exactly 14 Matchers (one per constructor)") {
      assertTrue(matchers.length == 14) && assertTrue(variantInstances.length == 14)
    },
    test(
      "each Matcher accepts its own variant (happy path) and returns null on every other variant (null-arm)"
    ) {
      // 14 × 14 = 196 downcasts; 14 succeed, 182 hit the `case _ => null` arm.
      val outcomes: IndexedSeq[(Int, Int, Boolean)] =
        for {
          (matcherIdx, matcher) <- matchers.zipWithIndex.map(_.swap)
          (instanceIdx, (_, instance)) <- variantInstances.zipWithIndex.map(_.swap)
        } yield {
          val result = matcher.downcastOrNull(instance)
          // Diagonal i==j must succeed (non-null & eq). Off-diagonal must be
          // null — this is the `case _ => null.asInstanceOf[T]` arm.
          val expectedSucceeds = matcherIdx == instanceIdx
          val actualSucceeds   = result != null
          (matcherIdx, instanceIdx, expectedSucceeds == actualSucceeds)
        }

      val allConsistent = outcomes.forall(_._3)
      val diagonalCount = outcomes.count { case (i, j, ok) => i == j && ok }
      val nullArmCount  = outcomes.count { case (i, j, ok) => i != j && ok }

      assertTrue(allConsistent) &&
      assertTrue(diagonalCount == 14) &&
      assertTrue(nullArmCount == 14 * 13)
    },
    test("each Matcher returns null when asked to downcast a non-MigrationAction Any payload") {
      // Passing arbitrary non-MigrationAction values also exercises the
      // `case _ => null` arm and guards against accidental widening.
      val foreignValues: List[Any] = List("string", 42, 3.14, (), Nil, Map.empty[String, Int])
      val allNull = matchers.forall { m =>
        foreignValues.forall(v => m.downcastOrNull(v) == null)
      }
      assertTrue(allNull)
    },
    test("Discriminator agrees with the positional order of MigrationAction.schema's cases") {
      // Pins the happy-path discriminator arm for every variant — complements
      // the null-arm coverage above.
      val pairs = variantInstances.zipWithIndex.map { case ((_, instance), expectedIdx) =>
        (expectedIdx, discriminator.discriminate(instance))
      }
      assertTrue(pairs.forall { case (expected, actual) => expected == actual })
    },
    test("each Matcher returns the original instance (reference-eq) on its own variant (happy arm)") {
      val diagonal = matchers.zipWithIndex.zip(variantInstances).forall {
        case ((matcher, idx), (_, instance)) =>
          val out = matcher.downcastOrNull(instance)
          out != null && (out.asInstanceOf[AnyRef] eq instance.asInstanceOf[AnyRef])
      }
      assertTrue(diagonal)
    }
  )
}
