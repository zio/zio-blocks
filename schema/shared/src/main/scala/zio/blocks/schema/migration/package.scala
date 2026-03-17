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

/**
 * Compile-time type-level tree representations for the Schema Migration
 * System.
 *
 * These sealed traits have no member values — they exist only at the type
 * level, as phantoms. They mirror the [[Reflect]] node structure and are used
 * as the `SrcTree` / `TgtTree` type parameters of
 * [[migration.MigrationBuilder]].
 *
 * {{{
 * Tree  ::= SNil                  -- consumed / leaf-done
 *         | SRecord[Fields]       -- Record node
 *         | SVariant[Cases]       -- Variant node
 *         | SSequence[Tree]       -- Sequence node
 *         | SMap[KeyTree,ValTree] -- Map node
 *         | SPrimitive            -- any primitive leaf
 *         | SWrapper[Tree]        -- newtype / wrapper leaf
 *
 * Fields / Cases ::= SNil | SCons[(label, Tree), Tail]
 * }}}
 *
 * `MigrationBuilder.build` compiles only when both `SrcTree` and `TgtTree`
 * reduce to [[SNil]].
 */
package object migration {

  /** The "empty" / "fully consumed" sentinel. */
  sealed trait SNil

  /**
   * Non-empty heterogeneous list cell used for record fields and variant
   * cases. `H` is a (label, Tree) tuple; `T` is the remaining list.
   */
  sealed trait SCons[H, T]

  /** Compile-time representation of a [[zio.blocks.schema.Reflect.Record]]. */
  sealed trait SRecord[Fields]

  /**
   * Compile-time representation of a [[zio.blocks.schema.Reflect.Variant]].
   */
  sealed trait SVariant[Cases]

  /**
   * Compile-time representation of a [[zio.blocks.schema.Reflect.Sequence]].
   */
  sealed trait SSequence[ElemTree]

  /** Compile-time representation of a [[zio.blocks.schema.Reflect.Map]]. */
  sealed trait SMap[KeyTree, ValTree]

  /**
   * Compile-time representation of any
   * [[zio.blocks.schema.Reflect.Primitive]]; treated as a leaf.
   */
  sealed trait SPrimitive

  /**
   * Compile-time representation of a [[zio.blocks.schema.Reflect.Wrapper]].
   */
  sealed trait SWrapper[WrappedTree]
}
