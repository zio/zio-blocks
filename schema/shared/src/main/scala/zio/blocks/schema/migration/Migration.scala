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
import zio.blocks.schema.{Schema, SchemaError}

/**
 * A `Migration[A, B]` turns a value of one version of a type into the next. It
 * converts the `A` to a [[zio.blocks.schema.DynamicValue]] with
 * [[sourceSchema]], applies a [[DynamicMigration]], which is a list of
 * [[MigrationAction]]s such as adding, renaming or dropping a field, and
 * converts the result to a `B` with [[targetSchema]]. Unlike
 * [[zio.blocks.schema.Into]], the steps are data: they can be inspected,
 * reversed and composed.
 *
 * Build one with [[Migration.newBuilder]], whose `build` checks at compile time
 * that every field of `B` is provided and every field of `A` is handled. On
 * Scala 2 the builder does not compile outside ZIO Blocks (see
 * `docs/dev/issues.md`); build a [[DynamicMigration]] and wrap it with
 * [[Migration.fromDynamic]] instead. On Scala 3 either schema can be a
 * structural one (JVM only), so an old version can be described without keeping
 * its case class.
 *
 * {{ // scala-3-only import zio.blocks.schema.migration._
 *
 * case class BookV1(title: String, pages: Int) object BookV1 { implicit val
 * schema: Schema[BookV1] = Schema.derived } case class BookV2(name: String,
 * pages: Int, edition: Int) object BookV2 { implicit val schema: Schema[BookV2] =
 * Schema.derived }
 *
 * val migration = Migration .newBuilder[BookV1, BookV2] .renameField(_.title,
 * _.name) .addField(_.edition, SchemaExpr.literal(1)) .build
 *
 * migration(BookV1("Zionomicon", 600)) // Right(BookV2("Zionomicon", 600, 1))
 * migration.reverse(BookV2("Zionomicon", 600, 2)) // Right(BookV1("Zionomicon",
 * 600)) }}
 *
 * @tparam A
 *   the source type
 * @tparam B
 *   the target type
 * @param sourceSchema
 *   converts the input to a `DynamicValue`
 * @param targetSchema
 *   converts the migrated `DynamicValue` to the result
 * @param dynamicMigration
 *   the actions, without the types
 * @see
 *   [[https://zioblocks.com/reference/schema/migration/ Migration]]
 */
final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  dynamicMigration: DynamicMigration
) {

  /**
   * Migrates `value`. Returns `Left` with the error of the first action that
   * fails (see [[DynamicMigration#apply]]), or with the error of converting the
   * migrated value to a `B`.
   */
  def apply(value: A): Either[SchemaError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue).flatMap { result =>
      targetSchema.fromDynamicValue(result)
    }
  }

  /**
   * Returns a migration that applies this migration's actions, then those of
   * `that`. The result uses this migration's source schema and the target
   * schema of `that`.
   */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    new Migration(sourceSchema, that.targetSchema, dynamicMigration ++ that.dynamicMigration)

  /** Same as `++`. */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /**
   * Returns the migration from `B` back to `A`: the actions reversed one by
   * one, in reverse order (see [[MigrationAction#reverse]]). It is computed
   * from the actions alone, so it can fail at run time: a reversed
   * `TransformField`, `ChangeFieldType`, `OptionalizeField` or collection
   * transform always fails, and a dropped field comes back with its
   * `defaultForReverse`, not its old value.
   */
  def reverse: Migration[B, A] =
    new Migration(targetSchema, sourceSchema, dynamicMigration.reverse)

  /**
   * Returns `true` when this migration has no actions. It can still fail, when
   * the source value does not convert to a `B`.
   */
  def isEmpty: Boolean = dynamicMigration.isEmpty

  /** Returns the number of actions. */
  def size: Int = dynamicMigration.size

  /** Returns the actions, in the order they run. */
  def actions: Chunk[MigrationAction] = dynamicMigration.actions
}

/**
 * Constructors of [[Migration]], the builder entry point [[newBuilder]], and
 * the selector syntax for builder selectors.
 */
object Migration extends MigrationSelectorSyntax with MigrationCompanionVersionSpecific {

  /** Returns the migration with no actions from `A` to itself. */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    new Migration(schema, schema, DynamicMigration.empty)

  private[migration] def fromAction[A, B](action: MigrationAction)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, DynamicMigration.single(action))

  private[migration] def fromActions[A, B](actions: MigrationAction*)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(Chunk.fromIterable(actions)))

  /**
   * Creates a migration from `A` to `B` that runs `dynamicMigration`. Nothing
   * checks at compile time that the actions produce a `B`; a mismatch shows up
   * as a `Left` when the migration runs.
   *
   * {{{
   * import zio.blocks.schema.migration._
   *
   * case class BookV1(title: String, pages: Int)
   * object BookV1 { implicit val schema: Schema[BookV1] = Schema.derived }
   * case class BookV2(name: String, pages: Int, edition: Int)
   * object BookV2 { implicit val schema: Schema[BookV2] = Schema.derived }
   *
   * val migration = Migration.fromDynamic[BookV1, BookV2](
   *   DynamicMigration(
   *     MigrationAction.RenameField(DynamicOptic.root.field("title"), "name"),
   *     MigrationAction.AddField(DynamicOptic.root.field("edition"), SchemaExpr.literal(1).toDynamic)
   *   )
   * )
   * migration(BookV1("Zionomicon", 600)) // Right(BookV2("Zionomicon", 600, 1))
   * }}}
   */
  def fromDynamic[A, B](dynamicMigration: DynamicMigration)(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    new Migration(sourceSchema, targetSchema, dynamicMigration)
}
