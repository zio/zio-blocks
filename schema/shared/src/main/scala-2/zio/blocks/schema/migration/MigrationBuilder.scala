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

// ── Type-class chains (Scala 2 alternative to match types) ───────────────────

/**
 * Evidence that field `Label` can be removed from `Fields`, yielding `Out`.
 *
 * Instances are synthesized by implicit resolution, mirroring the Scala 3
 * match type `RemoveField`.
 */
sealed trait RemoveField[Label, Fields, Out]

object RemoveField extends RemoveFieldLow {

  // Base case: label found at the head of the list
  implicit def foundHead[Label, V, Tail]: RemoveField[Label, SCons[(Label, V), Tail], Tail] =
    new RemoveField[Label, SCons[(Label, V), Tail], Tail] {}
}

trait RemoveFieldLow {
  // Inductive case: label not at head — keep head and recurse
  implicit def notHead[Label, H, V, Tail, Out](implicit
    rest: RemoveField[Label, Tail, Out]
  ): RemoveField[Label, SCons[(H, V), Tail], SCons[(H, V), Out]] =
    new RemoveField[Label, SCons[(H, V), Tail], SCons[(H, V), Out]] {}
}

/**
 * Evidence that stripping `Label` from `Tree` yields `Out`.
 *
 * Rules:
 *  - `SRecord[fields]` → `SRecord[RemoveField[Label, fields]]`, collapsing to
 *    [[SNil]] when the result is [[SNil]].
 *  - `SVariant[cases]` → analogous.
 *  - `SPrimitive`, `SWrapper[_]` → [[SNil]] (considered wholly consumed).
 *  - `SSequence[_]`, `SMap[_,_]` → pass through unchanged (consumed
 *    wholesale by the corresponding TransformElements / TransformKeys /
 *    TransformValues actions).
 *  - [[SNil]] → [[SNil]].
 */
sealed trait DropFromTree[Label, Tree, Out]

object DropFromTree extends DropFromTreeInstances

trait DropFromTreeInstances extends DropFromTreeLow {

  // SNil stays SNil
  implicit def snil[Label]: DropFromTree[Label, SNil, SNil] =
    new DropFromTree[Label, SNil, SNil] {}

  // SPrimitive is a leaf — consumed entirely
  implicit def primitive[Label]: DropFromTree[Label, SPrimitive, SNil] =
    new DropFromTree[Label, SPrimitive, SNil] {}

  // SWrapper is a leaf — consumed entirely
  implicit def wrapper[Label, W]: DropFromTree[Label, SWrapper[W], SNil] =
    new DropFromTree[Label, SWrapper[W], SNil] {}

  // SRecord: remove field from fields list; collapse to SNil if empty
  implicit def recordCollapse[Label, Fields](implicit
    rm: RemoveField[Label, Fields, SNil]
  ): DropFromTree[Label, SRecord[Fields], SNil] =
    new DropFromTree[Label, SRecord[Fields], SNil] {}

  // SVariant: remove case from cases list; collapse to SNil if empty
  implicit def variantCollapse[Label, Cases](implicit
    rm: RemoveField[Label, Cases, SNil]
  ): DropFromTree[Label, SVariant[Cases], SNil] =
    new DropFromTree[Label, SVariant[Cases], SNil] {}
}

trait DropFromTreeLow {

  // SRecord: remove field from fields list; result is SRecord[rest]
  implicit def recordKeep[Label, Fields, Rest](implicit
    rm: RemoveField[Label, Fields, Rest],
    ev: Rest =:!= SNil
  ): DropFromTree[Label, SRecord[Fields], SRecord[Rest]] =
    new DropFromTree[Label, SRecord[Fields], SRecord[Rest]] {}

  // SVariant: remove case from cases list; result is SVariant[rest]
  implicit def variantKeep[Label, Cases, Rest](implicit
    rm: RemoveField[Label, Cases, Rest],
    ev: Rest =:!= SNil
  ): DropFromTree[Label, SVariant[Cases], SVariant[Rest]] =
    new DropFromTree[Label, SVariant[Cases], SVariant[Rest]] {}
}

/** Negative evidence: `A` is not `B`. Provided by implicit resolution. */
sealed trait =:!=[A, B]

object =:!= {
  implicit def neq[A, B]: A =:!= B = new =:!=[A, B] {}
  // Ambiguity eliminates the case A =:= B
  implicit def neqAmbig[A]: A =:!= A = new =:!=[A, A] {}
}

// ── MigrationBuilder ─────────────────────────────────────────────────────────

/**
 * Scala 2 implementation of [[MigrationBuilder]].
 *
 * Uses three-param type-class chains ([[RemoveField]], [[DropFromTree]])
 * instead of Scala 3 match types to compute the updated `SrcTree` / `TgtTree`
 * after each operation.
 *
 * Construct via [[Migration.builder]] (Scala 2 companion).
 *
 * @tparam A        source type
 * @tparam B        target type
 * @tparam SrcTree  remaining unaccounted source fields
 * @tparam TgtTree  remaining unaccounted target fields
 */
final class MigrationBuilder[A, B, SrcTree, TgtTree] private[migration] (
  private[migration] val accumulated: List[MigrationAction]
) {

  def renameField[Src <: String, Tgt <: String, SrcOut, TgtOut](
    src: Src,
    tgt: Tgt,
    parentPath: List[String] = Nil
  )(implicit
    dropSrc: DropFromTree[Src, SrcTree, SrcOut],
    dropTgt: DropFromTree[Tgt, TgtTree, TgtOut]
  ): MigrationBuilder[A, B, SrcOut, TgtOut] =
    new MigrationBuilder(accumulated :+ MigrationAction.RenameField(parentPath :+ src, parentPath :+ tgt))

  def dropField[Src <: String, SrcOut](
    src: Src,
    parentPath: List[String] = Nil
  )(implicit
    dropSrc: DropFromTree[Src, SrcTree, SrcOut]
  ): MigrationBuilder[A, B, SrcOut, TgtTree] =
    new MigrationBuilder(accumulated :+ MigrationAction.DropField(parentPath :+ src))

  def addField[Tgt <: String, TgtOut](
    tgt: Tgt,
    value: DynamicValue,
    parentPath: List[String] = Nil
  )(implicit
    dropTgt: DropFromTree[Tgt, TgtTree, TgtOut]
  ): MigrationBuilder[A, B, SrcTree, TgtOut] =
    new MigrationBuilder(accumulated :+ MigrationAction.AddField(parentPath :+ tgt, value))

  def transformValue[Field <: String, SrcOut, TgtOut](
    field: Field,
    transform: FieldTransform,
    parentPath: List[String] = Nil
  )(implicit
    dropSrc: DropFromTree[Field, SrcTree, SrcOut],
    dropTgt: DropFromTree[Field, TgtTree, TgtOut]
  ): MigrationBuilder[A, B, SrcOut, TgtOut] =
    new MigrationBuilder(accumulated :+ MigrationAction.TransformValue(parentPath :+ field, transform))

  def renameCase[Src <: String, Tgt <: String, SrcOut, TgtOut](
    src: Src,
    tgt: Tgt,
    parentPath: List[String] = Nil
  )(implicit
    dropSrc: DropFromTree[Src, SrcTree, SrcOut],
    dropTgt: DropFromTree[Tgt, TgtTree, TgtOut]
  ): MigrationBuilder[A, B, SrcOut, TgtOut] =
    new MigrationBuilder(accumulated :+ MigrationAction.RenameCase(parentPath :+ src, parentPath :+ tgt))

  def dropCase[Src <: String, SrcOut](
    src: Src,
    parentPath: List[String] = Nil
  )(implicit
    dropSrc: DropFromTree[Src, SrcTree, SrcOut]
  ): MigrationBuilder[A, B, SrcOut, TgtTree] =
    new MigrationBuilder(accumulated :+ MigrationAction.DropCase(parentPath :+ src))

  def mandateField[Field <: String, SrcOut, TgtOut](
    field: Field,
    default: DynamicValue,
    parentPath: List[String] = Nil
  )(implicit
    dropSrc: DropFromTree[Field, SrcTree, SrcOut],
    dropTgt: DropFromTree[Field, TgtTree, TgtOut]
  ): MigrationBuilder[A, B, SrcOut, TgtOut] =
    new MigrationBuilder(accumulated :+ MigrationAction.Mandate(parentPath :+ field, default))

  def optionalizeField[Field <: String, SrcOut, TgtOut](
    field: Field,
    parentPath: List[String] = Nil
  )(implicit
    dropSrc: DropFromTree[Field, SrcTree, SrcOut],
    dropTgt: DropFromTree[Field, TgtTree, TgtOut]
  ): MigrationBuilder[A, B, SrcOut, TgtOut] =
    new MigrationBuilder(accumulated :+ MigrationAction.Optionalize(parentPath :+ field))

  /**
   * Declare that all remaining source and target fields are identical.
   * Compiles only when `SrcTree =:= TgtTree`.
   */
  def keep(implicit ev: SrcTree =:= TgtTree): MigrationBuilder[A, B, SNil, SNil] =
    new MigrationBuilder[A, B, SNil, SNil](accumulated)

  /**
   * Produce the finished [[Migration]]. Compiles only when both trees are
   * [[SNil]].
   */
  def build(implicit evSrc: SrcTree =:= SNil, evTgt: TgtTree =:= SNil): Migration[A, B] =
    Migration(accumulated)
}
