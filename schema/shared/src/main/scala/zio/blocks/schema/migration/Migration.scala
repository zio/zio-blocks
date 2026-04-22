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
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Typed envelope around a [[DynamicMigration]] paired with its source and
 * target [[zio.blocks.schema.Schema]]s. Compose via [[++]] / [[andThen]] and
 * reverse structurally via [[reverse]].
 */
final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  dynamic: DynamicMigration
) {

  /**
   * Applies this migration to `a`. Encodes `a` through `sourceSchema`, runs
   * the dynamic actions, then decodes through `targetSchema`. Decode failures
   * surface as [[MigrationError.ActionFailed]] at [[DynamicOptic.root]].
   */
  def apply(a: A): Either[MigrationError, B] = {
    val dv: zio.blocks.schema.DynamicValue = sourceSchema.toDynamicValue(a)
    dynamic.apply(dv) match {
      case Right(dv2) =>
        targetSchema.fromDynamicValue(dv2) match {
          case Right(b)  => new Right(b)
          case Left(err) =>
            new Left(new MigrationError.ActionFailed(DynamicOptic.root, "decode", new Some(err.toString)))
        }
      case l => l.asInstanceOf[Either[MigrationError, B]]
    }
  }

  /** Composes two migrations: runs `this` first, then `that`. */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    new Migration(sourceSchema, that.targetSchema, this.dynamic ++ that.dynamic)

  /** Alias for [[++]]. */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /** Structural reverse of this migration. Total by construction. */
  def reverse: Migration[B, A] =
    new Migration(targetSchema, sourceSchema, dynamic.reverse)
}

object Migration {

  /**
   * Identity migration at `Schema[A]`. Source and target schemas coincide and
   * the underlying dynamic migration is empty, so `apply(a)` returns
   * `Right(a)` whenever `fromDynamicValue(toDynamicValue(a))` round-trips.
   */
  def identity[A](implicit s: Schema[A]): Migration[A, A] =
    new Migration(s, s, DynamicMigration.empty)

  /** Starts a typed [[MigrationBuilder]] from `Schema[A]` to `Schema[B]`. */
  def builder[A, B](implicit sa: Schema[A], sb: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(sa, sb, Chunk.empty)
}

private[migration] object MigrationSentinel {

  /** Root path used by migration actions that apply at the value root. */
  private[migration] val Root: DynamicOptic = DynamicOptic.root
}
