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
import zio.blocks.schema.{DynamicValue, Reflect, Schema}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

/**
 * Ordered sequence of [[MigrationAction]]s applied left-to-right to a
 * [[DynamicValue]]. Pure data — no closures, no `Schema[_]` fields — so the ADT
 * round-trips through its own [[DynamicMigration.schema]].
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  /** Composes two migrations: runs `this` first, then `that`. */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  /** True when this migration contains no actions. */
  def isEmpty: Boolean = actions.isEmpty

  /**
   * Structural reverse: reverses the action sequence and calls `.reverse` on
   * each action. Total by construction because every [[MigrationAction]]
   * variant has a total `reverse`.
   */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.reverse.map(_.reverse))

  /**
   * Applies this migration to `dv`, iterating actions left-to-right and
   * short-circuiting on the first [[MigrationError]].
   */
  def apply(dv: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current: DynamicValue = dv
    val len                   = actions.length
    var idx                   = 0
    while (idx < len) {
      Interpreter(actions(idx), current) match {
        case Right(updated) => current = updated
        case l              => return l.asInstanceOf[Either[MigrationError, DynamicValue]]
      }
      idx += 1
    }
    new Right(current)
  }
}

object DynamicMigration {

  /** Identity element for [[DynamicMigration.++]]. */
  val empty: DynamicMigration = new DynamicMigration(Chunk.empty)

  /**
   * Hand-rolled schema for the pure-data migration ADT. Wraps
   * `Chunk[MigrationAction]` via [[MigrationAction.schema]] and threads through
   * the migration-used [[zio.blocks.schema.SchemaExpr.migrationSchema]] bridge.
   */
  implicit lazy val schema: Schema[DynamicMigration] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicMigration](
      fields = Chunk(
        Reflect.Deferred(() => Schema[Chunk[MigrationAction]].reflect).asTerm("actions")
      ),
      typeId = TypeId.of[DynamicMigration],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                      = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicMigration =
            new DynamicMigration(in.getObject(offset + 0).asInstanceOf[Chunk[MigrationAction]])
        },
        deconstructor = new Deconstructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                                   = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicMigration): Unit =
            out.setObject(offset + 0, in.actions)
        }
      ),
      modifiers = Chunk.empty
    )
  )
}
