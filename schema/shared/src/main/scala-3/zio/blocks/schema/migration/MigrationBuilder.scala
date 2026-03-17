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

import zio.blocks.schema.DynamicValue

// ── Match types ──────────────────────────────────────────────────────────────

/**
 * Remove the entry labelled `Label` from a heterogeneous field list.
 *
 * {{{
 * RemoveField["age", SCons[("name", SPrimitive), SCons[("age", SPrimitive), SNil]]]
 *   = SCons[("name", SPrimitive), SNil]
 * }}}
 */
type RemoveField[Label <: String, Fields] = Fields match {
  case SNil                    => SNil
  case SCons[(Label, ?), tail] => tail
  case SCons[(h, v), tail]     => SCons[(h, v), RemoveField[Label, tail]]
}

/**
 * Strip the entry labelled `Label` from the field list of an [[SRecord]] (or
 * [[SVariant]]). When the last entry is removed the wrapped [[SRecord]] /
 * [[SVariant]] collapses to [[SNil]] so that `.build` can require `=:= SNil`.
 */
type DropFromRecord[Label <: String, Fields] = RemoveField[Label, Fields] match {
  case SNil => SNil
  case _    => SRecord[RemoveField[Label, Fields]]
}

type DropFromVariant[Label <: String, Cases] = RemoveField[Label, Cases] match {
  case SNil => SNil
  case _    => SVariant[RemoveField[Label, Cases]]
}

/**
 * Strip field `Label` from any tree node, collapsing the wrapper to [[SNil]]
 * when all children are consumed.
 */
type DropFromTree[Label <: String, Tree] = Tree match {
  case SNil            => SNil
  case SRecord[fields] => DropFromRecord[Label, fields]
  case SVariant[cases] => DropFromVariant[Label, cases]
  case SSequence[e]    => SSequence[DropFromTree[Label, e]]
  case SMap[k, v]      => SMap[k, v] // map keys/values consumed wholesale
  case SPrimitive      => SNil
  case SWrapper[w]     => SNil
}

// ── MigrationBuilder ─────────────────────────────────────────────────────────

/**
 * A compile-time–tracked, pure-data migration builder.
 *
 * Each call to a field/case mapping method returns a new builder with updated
 * `SrcTree` / `TgtTree` type parameters, reflecting that the named field has
 * been "accounted for". [[build]] compiles only when both trees have been fully
 * consumed (both equal to [[SNil]]).
 *
 * Construct an initial builder via [[Migration.builder]].
 *
 * @tparam A
 *   source type
 * @tparam B
 *   target type
 * @tparam SrcTree
 *   remaining unaccounted fields of the source schema
 * @tparam TgtTree
 *   remaining unaccounted fields of the target schema
 */
final class MigrationBuilder[A, B, SrcTree, TgtTree] private[migration] (
  private[migration] val accumulated: List[MigrationAction]
) {

  // ── Record field operations ─────────────────────────────────────────────────

  /**
   * Rename field `src` (in the source schema) to `tgt` (in the target schema),
   * optionally at a nested `parentPath`.
   *
   * Both `src` and `tgt` must be literal string types inferred from the
   * value-level arguments.
   */
  def renameField[Src <: String & Singleton, Tgt <: String & Singleton](
    src: Src,
    tgt: Tgt,
    parentPath: List[String] = Nil
  ): MigrationBuilder[A, B, DropFromTree[Src, SrcTree], DropFromTree[Tgt, TgtTree]] =
    new MigrationBuilder(
      accumulated :+ MigrationAction.RenameField(parentPath :+ src, parentPath :+ tgt)
    )

  /**
   * Drop field `src` from the source schema (the target schema does not have
   * this field).
   */
  def dropField[Src <: String & Singleton](
    src: Src,
    parentPath: List[String] = Nil
  ): MigrationBuilder[A, B, DropFromTree[Src, SrcTree], TgtTree] =
    new MigrationBuilder(accumulated :+ MigrationAction.DropField(parentPath :+ src))

  /**
   * Add a field `tgt` to the target schema with the constant [[DynamicValue]]
   * `value`. The source schema does not have this field.
   */
  def addField[Tgt <: String & Singleton](
    tgt: Tgt,
    value: DynamicValue,
    parentPath: List[String] = Nil
  ): MigrationBuilder[A, B, SrcTree, DropFromTree[Tgt, TgtTree]] =
    new MigrationBuilder(accumulated :+ MigrationAction.AddField(parentPath :+ tgt, value))

  /**
   * Apply a [[FieldTransform]] to a field that exists with the same name in
   * both schemas (e.g., widening `Int` to `Long`).
   */
  def transformValue[Field <: String & Singleton](
    field: Field,
    transform: FieldTransform,
    parentPath: List[String] = Nil
  ): MigrationBuilder[A, B, DropFromTree[Field, SrcTree], DropFromTree[Field, TgtTree]] =
    new MigrationBuilder(
      accumulated :+ MigrationAction.TransformValue(parentPath :+ field, transform)
    )

  /**
   * Convert an `Option[T]` source field to a plain `T` target field, using
   * `default` when the source value is `None`.
   */
  def mandateField[Field <: String & Singleton](
    field: Field,
    default: DynamicValue,
    parentPath: List[String] = Nil
  ): MigrationBuilder[A, B, DropFromTree[Field, SrcTree], DropFromTree[Field, TgtTree]] =
    new MigrationBuilder(
      accumulated :+ MigrationAction.Mandate(parentPath :+ field, default)
    )

  /**
   * Convert a plain `T` source field to an `Option[T]` target field by wrapping
   * in `Some`.
   */
  def optionalizeField[Field <: String & Singleton](
    field: Field,
    parentPath: List[String] = Nil
  ): MigrationBuilder[A, B, DropFromTree[Field, SrcTree], DropFromTree[Field, TgtTree]] =
    new MigrationBuilder(
      accumulated :+ MigrationAction.Optionalize(parentPath :+ field)
    )

  // ── Variant case operations ─────────────────────────────────────────────────

  /**
   * Rename variant case `src` to `tgt`.
   */
  def renameCase[Src <: String & Singleton, Tgt <: String & Singleton](
    src: Src,
    tgt: Tgt,
    parentPath: List[String] = Nil
  ): MigrationBuilder[A, B, DropFromTree[Src, SrcTree], DropFromTree[Tgt, TgtTree]] =
    new MigrationBuilder(
      accumulated :+ MigrationAction.RenameCase(parentPath :+ src, parentPath :+ tgt)
    )

  /**
   * Declare that variant case `src` no longer exists in the target schema.
   * Encountering this case at runtime will produce a [[SchemaError]].
   */
  def dropCase[Src <: String & Singleton](
    src: Src,
    parentPath: List[String] = Nil
  ): MigrationBuilder[A, B, DropFromTree[Src, SrcTree], TgtTree] =
    new MigrationBuilder(accumulated :+ MigrationAction.DropCase(parentPath :+ src))

  // ── Bulk keep ───────────────────────────────────────────────────────────────

  /**
   * Declare that all remaining fields / cases in both trees are identical and
   * require no migration. Compiles only when `SrcTree =:= TgtTree` — i.e., the
   * remaining structures are provably the same.
   *
   * No runtime action is appended: values for these fields are already correct
   * in the source [[DynamicValue]].
   */
  def keep(implicit ev: SrcTree =:= TgtTree): MigrationBuilder[A, B, SNil, SNil] =
    new MigrationBuilder[A, B, SNil, SNil](accumulated)

  // ── Build ───────────────────────────────────────────────────────────────────

  /**
   * Produce the finished [[Migration]]. Compiles only when both `SrcTree` and
   * `TgtTree` have been fully consumed (both equal to [[SNil]]).
   */
  def build(implicit evSrc: SrcTree =:= SNil, evTgt: TgtTree =:= SNil): Migration[A, B] =
    Migration(accumulated)
}
