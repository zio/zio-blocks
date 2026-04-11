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
import zio.blocks.schema.{DynamicOptic, DynamicValue, Reflect, Schema}

/**
 * A fully typed, schema-aware migration from values of type `A` to values of
 * type `B`.
 *
 * `Migration` wraps a [[DynamicMigration]] together with the source schema
 * `fromSchema` and target schema `toSchema`. The typed layer adds two
 * capabilities absent from `DynamicMigration`:
 *
 *   1. '''Type-safe apply''': converts `A → DynamicValue`, delegates to the
 *      inner `DynamicMigration`, then converts `DynamicValue → B`, surfacing
 *      [[SchemaError]]s as [[MigrationError]]s.
 *   2. '''Default-value resolution''': any [[ValueExpr.DefaultValue]] inside
 *      the migration (including nested [[MigrationAction.ApplyMigration]] /
 *      [[MigrationAction.TransformCase]] steps) is resolved against `toSchema`
 *      before the inner `DynamicMigration` runs. The macro DSL surfaces this
 *      as [[SchemaExpr.DefaultValue]] for `addField` and `mandate`. After
 *      resolution, `DynamicMigration` only sees concrete [[ValueExpr.Constant]]
 *      nodes (or fails before execution).
 *
 * ==Laws==
 *   - Identity: `Migration.identity[A].apply(a) == Right(a)` for all `a` and
 *     any `Schema[A]`.
 *   - Reverse: if `m: Migration[A, B]` is structurally invertible then
 *     `m.reverse.apply(m.apply(a)) ≈ Right(a)` (modulo lossy operations such as
 *     `DropField`).
 *   - Composition: `m1.andThen(m2).apply(a) == m1.apply(a).flatMap(m2.apply)`
 *     for all `a`.
 *
 * @tparam A
 *   The source type.
 * @tparam B
 *   The target type.
 */
final class Migration[A, B](
  val fromSchema: Schema[A],
  val toSchema: Schema[B],
  val migration: DynamicMigration
) {

  /**
   * Applies the migration to a value of type `A`, producing a `B` on success or
   * a [[MigrationError]] on failure.
   *
   * Steps:
   *   1. Encode `a` to [[DynamicValue]] using `fromSchema`.
   *   2. Resolve [[ValueExpr.DefaultValue]] expressions in each action against
   *      `toSchema`.
   *   3. Execute the resolved [[DynamicMigration]].
   *   4. Decode the resulting [[DynamicValue]] to `B` using `toSchema`;
   *      [[SchemaError]]s are converted to [[MigrationError]]s.
   */
  def apply(a: A): Either[MigrationError, B] = {
    val sourceDv = fromSchema.toDynamicValue(a)
    resolveActions(migration.actions).flatMap { resolvedActions =>
      val resolved = new DynamicMigration(resolvedActions)
      resolved.apply(sourceDv).flatMap { resultDv =>
        toSchema.fromDynamicValue(resultDv).left.map(e => MigrationError(e.message))
      }
    }
  }

  /**
   * Returns a new `Migration[B, A]` that reverses this migration.
   *
   * The reversal delegates to [[DynamicMigration.reverse]] and swaps the source
   * and target schemas. Lossy operations (e.g. `DropField`, `Constant`
   * transforms) are reversed to `DefaultValue` — the reversed migration will
   * resolve those against the new target schema (`fromSchema`) when applied.
   */
  def reverse: Migration[B, A] = new Migration(toSchema, fromSchema, migration.reverse)

  /**
   * Composes this migration with `that`, producing a `Migration[A, C]` that
   * first applies `this` then `that`.
   *
   * The intermediate `B` type is erased at runtime; the composed pipeline
   * operates entirely on [[DynamicValue]]s.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    new Migration(
      fromSchema,
      that.toSchema,
      new DynamicMigration(resolveForComposition(this) ++ resolveForComposition(that))
    )

  /**
   * Alias for [[andThen]].
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] = andThen(that)

  // ─────────────────────────────────────────────────────────────────────────
  // DefaultValue resolution
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Replaces every [[ValueExpr.DefaultValue]] in `actions` (including inside
   * nested [[MigrationAction.ApplyMigration]] and [[MigrationAction.TransformCase]]
   * pipelines) with a concrete [[ValueExpr.Constant]] sourced from `toSchema`.
   *
   * Resolution strategy:
   *   - Navigate `toSchema` to the sub-reflect at the action's path.
   *   - Call `getDefaultValue` on the sub-reflect; if it returns `Some(v)`,
   *     convert to [[DynamicValue]] via `toDynamicValue`.
   *   - If no default is registered, return a [[MigrationError]].
   */
  private def resolveActions(
    actions: Chunk[MigrationAction]
  ): Either[MigrationError, Chunk[MigrationAction]] =
    actions.foldLeft[Either[MigrationError, Chunk[MigrationAction]]](Right(Chunk.empty)) {
      case (Right(acc), action) => resolveOne(action).map(acc :+ _)
      case (left, _)            => left
    }

  private def resolveOne(action: MigrationAction): Either[MigrationError, MigrationAction] = action match {

    case MigrationAction.ApplyMigration(path, dm) =>
      resolveActions(dm.actions).map(resolved =>
        MigrationAction.ApplyMigration(path, new DynamicMigration(resolved))
      )

    case MigrationAction.TransformCase(at, caseName, dm) =>
      resolveActions(dm.actions).map(resolved =>
        MigrationAction.TransformCase(at, caseName, new DynamicMigration(resolved))
      )

    case MigrationAction.AddField(path, ValueExpr.DefaultValue) =>
      resolveDefaultAt(path).map(dv => MigrationAction.AddField(path, ValueExpr.Constant(dv)))

    case MigrationAction.Mandate(path, ValueExpr.DefaultValue) =>
      resolveDefaultAt(path).map(dv => MigrationAction.Mandate(path, ValueExpr.Constant(dv)))

    case MigrationAction.TransformValue(path, ValueExpr.DefaultValue) =>
      resolveDefaultAt(path).map(dv => MigrationAction.TransformValue(path, ValueExpr.Constant(dv)))

    case MigrationAction.TransformElements(path, ValueExpr.DefaultValue) =>
      resolveDefaultAt(path).map(dv => MigrationAction.TransformElements(path, ValueExpr.Constant(dv)))

    case MigrationAction.TransformKeys(path, ValueExpr.DefaultValue) =>
      resolveDefaultAt(path).map(dv => MigrationAction.TransformKeys(path, ValueExpr.Constant(dv)))

    case MigrationAction.TransformValues(path, ValueExpr.DefaultValue) =>
      resolveDefaultAt(path).map(dv => MigrationAction.TransformValues(path, ValueExpr.Constant(dv)))

    case other =>
      Right(other)
  }

  /**
   * Resolves the default [[DynamicValue]] at `path` within `toSchema`.
   *
   * Navigates `toSchema`'s reflection tree via [[Schema.get(DynamicOptic)]] to
   * find the sub-reflect at `path`, then calls `getDefaultValue` on it. If the
   * schema at that path has no registered default, returns a
   * [[MigrationError]].
   */
  private def resolveDefaultAt(path: DynamicOptic): Either[MigrationError, DynamicValue] =
    extractDefaultFromWholeTarget(path) match {
      case right @ Right(_) =>
        right
      case Left(_) =>
        toSchema.get(path) match {
          case None =>
            Left(
              MigrationError(
                s"DefaultValue: no schema found at path ${path.toScalaString} in target schema"
              )
            )
          case Some(subReflect) =>
            getDefaultDynamic(subReflect) match {
              case Some(dv) => Right(dv)
              case None     =>
                Left(
                  MigrationError(
                    s"DefaultValue: target schema has no default value at path ${path.toScalaString}"
                  )
                )
            }
        }
    }

  /**
   * Extracts the default [[DynamicValue]] from an existentially typed
   * [[Reflect.Bound]].
   *
   * The cast to `Reflect.Bound[Any]` is safe because `getDefaultValue` and
   * `toDynamicValue` are used together on the same instance, preserving the
   * internal type consistency enforced by the `Reflect` ADT.
   */
  private def getDefaultDynamic(r: Reflect.Bound[_]): Option[DynamicValue] = {
    val anyR = r.asInstanceOf[Reflect.Bound[Any]]
    anyR.getDefaultValue.map(v => anyR.toDynamicValue(v))
  }

  private def extractDefaultFromWholeTarget(path: DynamicOptic): Either[MigrationError, DynamicValue] =
    getDefaultDynamic(toSchema.reflect) match {
      case Some(defaultDv) =>
        defaultDv.get(path).one.left.map(e => MigrationError(e.message, path))
      case None =>
        Left(
          MigrationError(
            s"DefaultValue: target schema has no default value at path ${path.toScalaString}"
          )
        )
    }

  private def resolveForComposition[C](migration: Migration[_, C]): Chunk[MigrationAction] =
    migration.resolveActions(migration.migration.actions).fold(_ => migration.migration.actions, identity)
}

object Migration {

  /**
   * Creates a [[Migration]] from an explicit [[DynamicMigration]] together with
   * its source and target schemas.
   */
  def apply[A, B](
    fromSchema: Schema[A],
    toSchema: Schema[B],
    migration: DynamicMigration
  ): Migration[A, B] = new Migration(fromSchema, toSchema, migration)

  /**
   * Creates an identity [[Migration]] for type `A` that applies no actions and
   * simply round-trips through [[DynamicValue]].
   *
   * Useful as a base for building migrations incrementally with
   * [[MigrationBuilder]].
   */
  def identity[A](schema: Schema[A]): Migration[A, A] =
    new Migration(schema, schema, DynamicMigration.identity)
}
