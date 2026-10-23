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

package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId

/**
 * How [[Patch]] and [[DynamicPatch]] treat an operation whose precondition
 * fails: an index or range outside a sequence, a map key that is missing (or
 * present, for an add), a value in another case than the path expects, a
 * missing record field, a string edit out of range, or a value of the wrong
 * kind.
 *
 * No mode checks that the value still holds what it held when the patch was
 * made: a delete by index removes whatever is at that index now, and a delta
 * adds to the current number (see `docs/dev/issues.md`). Check that the value
 * is unchanged, for example with a version number, before applying a stored
 * patch.
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/patch/ Patching]]
 */
sealed trait PatchMode

/** The three modes, and their schemas. */
object PatchMode {

  /**
   * Stops at the first operation whose precondition fails and returns its
   * error. A traversal over an empty sequence and a schema search that matches
   * nothing also fail. Elements of a traversal that are in another case than
   * the one it selects are left alone rather than failed.
   */
  case object Strict extends PatchMode

  /**
   * Skips each operation whose precondition fails and applies the others. What
   * is skipped is one operation: a top-level operation, one step of a sequence
   * or map edit, or one operation of a nested patch. A list of string edits
   * counts as one operation, so when one edit is out of range none of them is
   * applied.
   */
  case object Lenient extends PatchMode

  /**
   * Behaves like [[Lenient]], except in three cases: a map add of a key that is
   * present replaces its value, a map remove of a missing key does nothing, and
   * a sequence insert or delete out of range is clamped to the sequence.
   * Everything else that `Lenient` skips, `Clobber` skips too, such as a modify
   * at a missing index or key, a string edit out of range, or a case mismatch.
   */
  case object Clobber extends PatchMode

  // Schema Definitions, Manual derivation for Scala 2 compatability
  // In Scala 3, You can derive Schema using `Schema.derive`

  /** The schema of `PatchMode.Strict.type`. */
  implicit lazy val strictSchema: Schema[PatchMode.Strict.type] = new Schema(
    reflect = new Reflect.Record[Binding, PatchMode.Strict.type](
      fields = Chunk.empty,
      typeId = TypeId.of[PatchMode.Strict.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[PatchMode.Strict.type](PatchMode.Strict),
        deconstructor = new ConstantDeconstructor[PatchMode.Strict.type]
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `PatchMode.Lenient.type`. */
  implicit lazy val lenientSchema: Schema[PatchMode.Lenient.type] = new Schema(
    reflect = new Reflect.Record[Binding, PatchMode.Lenient.type](
      fields = Chunk.empty,
      typeId = TypeId.of[PatchMode.Lenient.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[PatchMode.Lenient.type](PatchMode.Lenient),
        deconstructor = new ConstantDeconstructor[PatchMode.Lenient.type]
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `PatchMode.Clobber.type`. */
  implicit lazy val clobberSchema: Schema[PatchMode.Clobber.type] = new Schema(
    reflect = new Reflect.Record[Binding, PatchMode.Clobber.type](
      fields = Chunk.empty,
      typeId = TypeId.of[PatchMode.Clobber.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[PatchMode.Clobber.type](PatchMode.Clobber),
        deconstructor = new ConstantDeconstructor[PatchMode.Clobber.type]
      ),
      modifiers = Chunk.empty
    )
  )

  /** The schema of `PatchMode`. */
  implicit lazy val schema: Schema[PatchMode] = new Schema(
    reflect = new Reflect.Variant[Binding, PatchMode](
      cases = Chunk(
        strictSchema.reflect.asTerm("Strict"),
        lenientSchema.reflect.asTerm("Lenient"),
        clobberSchema.reflect.asTerm("Clobber")
      ),
      typeId = TypeId.of[PatchMode],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[PatchMode] {
          def discriminate(a: PatchMode): Int = a match {
            case _: PatchMode.Strict.type  => 0
            case _: PatchMode.Lenient.type => 1
            case _: PatchMode.Clobber.type => 2
          }
        },
        matchers = Matchers(
          new Matcher[PatchMode.Strict.type] {
            def downcastOrNull(a: Any): PatchMode.Strict.type = a match {
              case x: PatchMode.Strict.type => x
              case _                        => null.asInstanceOf[PatchMode.Strict.type]
            }
          },
          new Matcher[PatchMode.Lenient.type] {
            def downcastOrNull(a: Any): PatchMode.Lenient.type = a match {
              case x: PatchMode.Lenient.type => x
              case _                         => null.asInstanceOf[PatchMode.Lenient.type]
            }
          },
          new Matcher[PatchMode.Clobber.type] {
            def downcastOrNull(a: Any): PatchMode.Clobber.type = a match {
              case x: PatchMode.Clobber.type => x
              case _                         => null.asInstanceOf[PatchMode.Clobber.type]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
