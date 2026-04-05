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

import scala.deriving.Mirror

import zio.blocks.schema._

/**
 * Product-to-product builder that tracks unconsumed field names in type
 * parameters (source and target label tuples from [[Mirror.ProductOf]]).
 * Callers must pass the correct singleton string type `N` for each selector
 * (for example `addField["age"](_.age, ...)`).
 *
 * Renames, nested paths, and sealed traits are not tracked here; use
 * [[MigrationBuilder]] and [[MigrationBuilder.buildPartial]] instead.
 *
 * Nested or hierarchical field trees (issue #519 “hierarchical tracking”) are
 * not modeled: only a flat product field list is removed via [[RemField]].
 */
final class TrackedMigrationBuilder[A, B, SourceRemaining <: Tuple, TargetRemaining <: Tuple] private (
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  private def withActions(
    next: Vector[MigrationAction]
  ): TrackedMigrationBuilder[A, B, SourceRemaining, TargetRemaining] =
    new TrackedMigrationBuilder(sourceSchema, targetSchema, next)

  inline def addField[N <: String & Singleton](
    inline target: B => Any,
    default: MigrationExpr
  )(using schemaB: Schema[B]): TrackedMigrationBuilder[A, B, SourceRemaining, RemField[N, TargetRemaining]] = {
    val at = new CompanionOptics[B] {}.dynamicOptic(target)(using schemaB)
    withActions(actions :+ MigrationAction.AddField(at, default)).asInstanceOf[
      TrackedMigrationBuilder[A, B, SourceRemaining, RemField[N, TargetRemaining]]
    ]
  }

  inline def preserveField[N <: String & Singleton](
    inline source: A => Any,
    inline target: B => Any
  )(using
    schemaA: Schema[A],
    schemaB: Schema[B]
  ): TrackedMigrationBuilder[A, B, RemField[N, SourceRemaining], RemField[N, TargetRemaining]] = {
    val _ = (
      new CompanionOptics[A] {}.dynamicOptic(source)(using schemaA),
      new CompanionOptics[B] {}.dynamicOptic(target)(using schemaB)
    )
    withActions(actions)
      .asInstanceOf[TrackedMigrationBuilder[A, B, RemField[N, SourceRemaining], RemField[N, TargetRemaining]]]
  }

  inline def build(using
    ev: SourceRemaining =:= EmptyTuple,
    ev2: TargetRemaining =:= EmptyTuple
  ): Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
}

object TrackedMigrationBuilder {

  def apply[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B],
    mirrorA: Mirror.ProductOf[A],
    mirrorB: Mirror.ProductOf[B]
  ): TrackedMigrationBuilder[A, B, mirrorA.MirroredElemLabels, mirrorB.MirroredElemLabels] =
    new TrackedMigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}

/**
 * Removes the first occurrence of singleton field name `N` from a tuple of
 * field name singletons.
 */
type RemField[N <: String, Fields <: Tuple] <: Tuple = Fields match {
  case EmptyTuple   => EmptyTuple
  case N *: tail    => tail
  case head *: tail => head *: RemField[N, tail]
}
