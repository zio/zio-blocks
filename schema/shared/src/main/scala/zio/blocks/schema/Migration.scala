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
 * Typed migration from A to B. Wraps source schema, target schema, and the
 * untyped DynamicMigration. User-facing API; DynamicOptic is not exposed.
 */
final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  underlying: DynamicMigration
) {

  def apply(a: A): Either[MigrationError, B] =
    for {
      dv  <- Right(sourceSchema.toDynamicValue(a))
      dv2 <- underlying.apply(dv)
      b   <- targetSchema.fromDynamicValue(dv2).left.map(se => MigrationError(se.message, DynamicOptic.root))
    } yield b

  def reverse: Migration[B, A] =
    Migration(targetSchema, sourceSchema, underlying.reverse)

  def andThen[C](other: Migration[B, C]): Migration[A, C] =
    Migration(sourceSchema, other.targetSchema, underlying ++ other.underlying)

  def ++[C](other: Migration[B, C]): Migration[A, C] = andThen(other)
}

object Migration {

  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(schema, schema, DynamicMigration.empty)
}
