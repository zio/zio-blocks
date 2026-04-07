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
import zio.blocks.schema.{DynamicOptic, Reflect, Schema}

/**
 * An immutable builder that accumulates [[MigrationAction]]s and assembles them
 * into a [[Migration]] or [[DynamicMigration]].
 *
 * `MigrationBuilder[A, B]` is the entry point for constructing typed
 * migrations. In normal usage you obtain one via [[MigrationBuilder.apply]] and
 * add actions through the macro DSL methods provided by the platform-specific
 * companion object extensions (Steps 5–8). The low-level [[withAction]] method
 * is also public so that users can append pre-built [[MigrationAction]]s
 * directly when the macro DSL is not expressive enough.
 *
 * `MigrationBuilder` itself carries no closures and only stores pure-data
 * [[MigrationAction]] values, so a snapshot of `actions` is fully serializable.
 *
 * ==Typical usage==
 * {{{
 * val migration: Migration[UserV1, UserV2] =
 *   MigrationBuilder[UserV1, UserV2](userV1Schema, userV2Schema)
 *     .renameField(_.name, "fullName")           // macro DSL (Step 5/7)
 *     .addField(_.age, ValueExpr.Constant(...))  // macro DSL (Step 5/7)
 *     .build
 * }}}
 *
 * @tparam A
 *   The source type being migrated from.
 * @tparam B
 *   The target type being migrated to.
 * @param fromSchema
 *   The [[Schema]] for the source type `A`.
 * @param toSchema
 *   The [[Schema]] for the target type `B`.
 * @param actions
 *   The accumulated [[MigrationAction]]s in the order they will be applied.
 */
final class MigrationBuilder[A, B](
  val fromSchema: Schema[A],
  val toSchema: Schema[B],
  val actions: Chunk[MigrationAction]
) {

  /**
   * Appends a single [[MigrationAction]] to this builder, returning a new
   * builder with the action added at the end.
   *
   * This is the low-level building block used by all macro DSL methods. Prefer
   * the typed DSL methods (e.g. `renameField`, `dropField`) where available.
   */
  def withAction(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(fromSchema, toSchema, actions :+ action)

  /**
   * Appends multiple [[MigrationAction]]s in order.
   *
   * Equivalent to folding `withAction` over `newActions`.
   */
  def withActions(newActions: Chunk[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(fromSchema, toSchema, actions ++ newActions)

  // ─────────────────────────────────────────────────────────────────────────
  // Low-level DSL — path-explicit variants
  //
  // These accept a pre-built DynamicOptic directly. The macro DSL methods in
  // the platform-specific companion extensions call these after converting a
  // selector lambda to a DynamicOptic.
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Appends an [[MigrationAction.AddField]] action using an explicit
   * [[DynamicOptic]] path.
   */
  def addFieldAt(path: DynamicOptic, defaultValue: ValueExpr): MigrationBuilder[A, B] =
    withAction(MigrationAction.AddField(path, defaultValue))

  /**
   * Appends a [[MigrationAction.DropField]] action using an explicit
   * [[DynamicOptic]] path.
   */
  def dropFieldAt(path: DynamicOptic): MigrationBuilder[A, B] =
    withAction(MigrationAction.DropField(path))

  /**
   * Appends a [[MigrationAction.RenameField]] action using an explicit
   * [[DynamicOptic]] path.
   */
  def renameFieldAt(path: DynamicOptic, newName: String): MigrationBuilder[A, B] =
    withAction(MigrationAction.RenameField(path, newName))

  /**
   * Appends a [[MigrationAction.TransformValue]] action using an explicit
   * [[DynamicOptic]] path.
   */
  def transformValueAt(path: DynamicOptic, expr: ValueExpr): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformValue(path, expr))

  /**
   * Appends a [[MigrationAction.Mandate]] action using an explicit
   * [[DynamicOptic]] path.
   */
  def mandateAt(path: DynamicOptic, defaultExpr: ValueExpr): MigrationBuilder[A, B] =
    withAction(MigrationAction.Mandate(path, defaultExpr))

  /**
   * Appends a [[MigrationAction.Optionalize]] action using an explicit
   * [[DynamicOptic]] path.
   */
  def optionalizeAt(path: DynamicOptic): MigrationBuilder[A, B] =
    withAction(MigrationAction.Optionalize(path))

  /**
   * Appends a [[MigrationAction.ChangeType]] action using an explicit
   * [[DynamicOptic]] path.
   */
  def changeTypeAt(path: DynamicOptic, expr: ValueExpr.PrimitiveConvert): MigrationBuilder[A, B] =
    withAction(MigrationAction.ChangeType(path, expr))

  /**
   * Appends a [[MigrationAction.Join]] action using explicit [[DynamicOptic]]
   * paths.
   */
  def joinAt(
    left: DynamicOptic,
    right: DynamicOptic,
    target: DynamicOptic,
    combiner: ValueExpr
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.Join(left, right, target, combiner))

  /**
   * Appends a [[MigrationAction.Split]] action using explicit [[DynamicOptic]]
   * paths.
   */
  def splitAt(
    from: DynamicOptic,
    toLeft: DynamicOptic,
    toRight: DynamicOptic,
    splitter: ValueExpr
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.Split(from, toLeft, toRight, splitter))

  /**
   * Appends a [[MigrationAction.TransformElements]] action using an explicit
   * [[DynamicOptic]] path.
   */
  def transformElementsAt(path: DynamicOptic, expr: ValueExpr): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformElements(path, expr))

  /**
   * Appends a [[MigrationAction.TransformKeys]] action using an explicit
   * [[DynamicOptic]] path.
   */
  def transformKeysAt(path: DynamicOptic, expr: ValueExpr): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformKeys(path, expr))

  /**
   * Appends a [[MigrationAction.TransformValues]] action using an explicit
   * [[DynamicOptic]] path.
   */
  def transformValuesAt(path: DynamicOptic, expr: ValueExpr): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformValues(path, expr))

  /**
   * Appends a [[MigrationAction.RenameCase]] action.
   */
  def renameCase(fromName: String, toName: String): MigrationBuilder[A, B] =
    withAction(MigrationAction.RenameCase(fromName, toName))

  /**
   * Appends a [[MigrationAction.TransformCase]] action.
   */
  def transformCase(caseName: String, expr: ValueExpr): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformCase(caseName, expr))

  // ─────────────────────────────────────────────────────────────────────────
  // Terminal operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Builds the accumulated actions into a typed [[Migration]][A, B].
   *
   * The returned `Migration` holds `fromSchema`, `toSchema`, and a
   * [[DynamicMigration]] wrapping a snapshot of the current `actions`. Further
   * calls to `withAction` on this builder do not affect the returned
   * `Migration`.
   */
  def build: Migration[A, B] =
    validateBuild().fold(
      errors =>
        throw new IllegalArgumentException(
          s"Invalid migration:\n${errors.mkString("\n")}"
        ),
      _ => buildPartial
    )

  /**
   * Builds a [[Migration]] without validating the accumulated actions against
   * the source and target schemas.
   *
   * Useful while constructing partial or forward-declared migrations. Runtime
   * application can still fail if any action paths are incompatible with the
   * provided schemas.
   */
  def buildPartial: Migration[A, B] =
    new Migration(fromSchema, toSchema, new DynamicMigration(actions))

  /**
   * Builds only the untyped [[DynamicMigration]], discarding schema
   * information.
   *
   * Useful when you need a serializable, schema-agnostic representation of the
   * migration (e.g. for storage or transmission).
   */
  def buildDynamic: DynamicMigration = new DynamicMigration(actions)

  private def validateBuild(): Either[Chunk[String], Unit] = {
    val errors = actions.zipWithIndex.foldLeft(Chunk.empty[String]) { case (acc, (action, idx)) =>
      acc ++ validateAction(idx, action)
    }
    if (errors.isEmpty) Right(())
    else Left(errors)
  }

  private def validateAction(index: Int, action: MigrationAction): Chunk[String] = {
    val prefix = s"Action #$index (${action.getClass.getSimpleName})"

    def missingSource(path: DynamicOptic): Chunk[String] =
      if (fromSchema.get(path).isDefined) Chunk.empty
      else Chunk.single(s"$prefix: source schema has no node at ${path.toScalaString}")

    def missingTarget(path: DynamicOptic): Chunk[String] =
      if (toSchema.get(path).isDefined) Chunk.empty
      else Chunk.single(s"$prefix: target schema has no node at ${path.toScalaString}")

    def expectSource(
      path: DynamicOptic,
      label: String
    )(predicate: Reflect.Bound[_] => Boolean, expected: String): Chunk[String] =
      fromSchema.get(path) match {
        case Some(reflect) if predicate(reflect) => Chunk.empty
        case Some(_)                             =>
          Chunk.single(s"$prefix: source $label at ${path.toScalaString} must be $expected")
        case None =>
          Chunk.single(s"$prefix: source schema has no node at ${path.toScalaString}")
      }

    action match {
      case MigrationAction.AddField(path, _) =>
        missingTarget(path)

      case MigrationAction.DropField(path) =>
        missingSource(path)

      case MigrationAction.RenameField(path, newName) =>
        val base        = missingSource(path)
        val renamedPath = renameLastField(path, newName)
        base ++ renamedPath.fold(
          Chunk.single(
            s"$prefix: rename path ${path.toScalaString} must end in a field"
          )
        )(missingTarget)

      case MigrationAction.TransformValue(path, _) =>
        missingSource(path)

      case MigrationAction.Mandate(path, _) =>
        expectSource(path, "path")(_.isOption, "an Option") ++ missingTarget(path)

      case MigrationAction.Optionalize(path) =>
        missingSource(path) ++ missingTarget(path)

      case MigrationAction.ChangeType(path, _) =>
        expectSource(path, "path")(_.isPrimitive, "a primitive") ++ missingTarget(path)

      case MigrationAction.Join(left, right, target, _) =>
        missingSource(left) ++ missingSource(right) ++ missingTarget(target)

      case MigrationAction.Split(from, toLeft, toRight, _) =>
        missingSource(from) ++ missingTarget(toLeft) ++ missingTarget(toRight)

      case MigrationAction.TransformElements(path, _) =>
        expectSource(path, "path")(_.isSequence, "a sequence")

      case MigrationAction.TransformKeys(path, _) =>
        expectSource(path, "path")(_.isMap, "a map")

      case MigrationAction.TransformValues(path, _) =>
        expectSource(path, "path")(_.isMap, "a map")

      case MigrationAction.RenameCase(_, _) =>
        Chunk.empty

      case MigrationAction.TransformCase(_, _) =>
        Chunk.empty
    }
  }

  private def renameLastField(path: DynamicOptic, newName: String): Option[DynamicOptic] =
    if (path.nodes.isEmpty) None
    else
      path.nodes.last match {
        case _: DynamicOptic.Node.Field =>
          Some(new DynamicOptic(path.nodes.dropRight(1) :+ new DynamicOptic.Node.Field(newName)))
        case _ => None
      }
}

object MigrationBuilder {

  /**
   * Creates an empty [[MigrationBuilder]] for the given source and target
   * schemas.
   *
   * Start here, chain DSL methods, and call [[MigrationBuilder.build]] to
   * obtain the finished [[Migration]].
   *
   * {{{
   * val m = MigrationBuilder[V1, V2](v1Schema, v2Schema)
   *   .renameField(_.name, "fullName")
   *   .build
   * }}}
   */
  def apply[A, B](fromSchema: Schema[A], toSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(fromSchema, toSchema, Chunk.empty)
}
