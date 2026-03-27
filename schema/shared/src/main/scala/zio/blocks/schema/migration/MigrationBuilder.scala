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

import zio.blocks.schema.{DynamicOptic, Schema}
import zio.blocks.schema.migration.MigrationAction._

final class MigrationBuilder[A, B] private[migration] (
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction],
  val handledFields: Set[String]
) {

  def addField(target: DynamicOptic, default: MigrationExpr): MigrationBuilder[A, B] =
    withAction(AddField(target, default), topLevelField(target).toSet)

  def dropField(
    source: DynamicOptic,
    defaultForReverse: MigrationExpr = MigrationExpr.DefaultValue
  ): MigrationBuilder[A, B] =
    withAction(DropField(source, defaultForReverse), Set.empty)

  def renameField(from: DynamicOptic, to: DynamicOptic): MigrationBuilder[A, B] =
    withAction(Rename(from, topLevelField(to).getOrElse(to.toString)), topLevelField(to).toSet)

  def transformField(from: DynamicOptic, to: DynamicOptic, transform: MigrationExpr): MigrationBuilder[A, B] =
    withAction(TransformValue(from, transform), topLevelField(to).toSet)

  def mandateField(source: DynamicOptic, target: DynamicOptic, default: MigrationExpr): MigrationBuilder[A, B] =
    withAction(Mandate(source, default), topLevelField(target).toSet)

  def optionalizeField(source: DynamicOptic, target: DynamicOptic): MigrationBuilder[A, B] =
    withAction(Optionalize(source), topLevelField(target).toSet)

  def changeFieldType(source: DynamicOptic, target: DynamicOptic, converter: MigrationExpr): MigrationBuilder[A, B] =
    withAction(ChangeType(source, converter), topLevelField(target).toSet)

  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    withAction(RenameCase(DynamicOptic.root, from, to), Set.empty)

  def inField[FA, FB](
    fromField: DynamicOptic,
    toField: DynamicOptic,
    subMigration: Migration[FA, FB]
  ): MigrationBuilder[A, B] = {
    val _ = fromField
    withAction(NestedMigration(toField, subMigration.dynamicMigration), topLevelField(toField).toSet)
  }

  def transformElements(source: DynamicOptic, transform: MigrationExpr): MigrationBuilder[A, B] =
    withAction(TransformElements(source, transform), Set.empty)

  def transformKeys(source: DynamicOptic, transform: MigrationExpr): MigrationBuilder[A, B] =
    withAction(TransformKeys(source, transform), Set.empty)

  def transformValues(source: DynamicOptic, transform: MigrationExpr): MigrationBuilder[A, B] =
    withAction(TransformValues(source, transform), Set.empty)

  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
  // build is provided by MigrationBuilderVersionSpecific via extension

  private[this] def withAction(action: MigrationAction, fields: Set[String]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action, handledFields ++ fields)

  private[this] def topLevelField(path: DynamicOptic): Option[String] =
    path.nodes.headOption.collect { case DynamicOptic.Node.Field(name) => name }
}

object MigrationBuilder {
  def apply[A, B](implicit sa: Schema[A], sb: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(sa, sb, Vector.empty, Set.empty)
}
