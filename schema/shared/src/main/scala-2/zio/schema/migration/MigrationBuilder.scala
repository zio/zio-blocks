package zio.schema.migration

import scala.language.experimental.macros
import zio.blocks.schema.{Schema, SchemaExpr}

class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {
  
  def withAction(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)

  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  def addField(target: B => Any, default: SchemaExpr[A, _]): MigrationBuilder[A, B] =
    macro MigrationMacros.addFieldImpl[A, B]

  def dropField(source: A => Any, defaultForReverse: SchemaExpr[B, _]): MigrationBuilder[A, B] =
    macro MigrationMacros.dropFieldImpl[A, B]

  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] =
    macro MigrationMacros.renameFieldImpl[A, B]

  def transformValue[S, T](path: A => S, expr: SchemaExpr[S, T]): MigrationBuilder[A, B] =
    macro MigrationMacros.transformValueImpl[A, B, S, T]

  def mandate[S](path: A => Option[S]): MigrationBuilder[A, B] =
    macro MigrationMacros.mandateImpl[A, B, S]

  def optionalize[S](path: A => S): MigrationBuilder[A, B] =
    macro MigrationMacros.optionalizeImpl[A, B, S]

  def transformElements[S, T](path: A => Seq[S], migration: Migration[S, T]): MigrationBuilder[A, B] =
    macro MigrationMacros.transformElementsImpl[A, B, S, T]

  def transformKeys[K, V, K2](path: A => Map[K, V], migration: Migration[K, K2]): MigrationBuilder[A, B] =
    macro MigrationMacros.transformKeysImpl[A, B, K, V, K2]

  def transformValues[K, V, V2](path: A => Map[K, V], migration: Migration[V, V2]): MigrationBuilder[A, B] =
    macro MigrationMacros.transformValuesImpl[A, B, K, V, V2]

  def transformCase[S, T](path: A => S, migration: Migration[S, T]): MigrationBuilder[A, B] =
    macro MigrationMacros.transformCaseImpl[A, B, S, T]

  def renameCase(path: A => Any, from: String, to: String): MigrationBuilder[A, B] =
    macro MigrationMacros.renameCaseImpl[A, B]

  def changeType[S](path: A => S, converter: SchemaExpr[S, _]): MigrationBuilder[A, B] =
    macro MigrationMacros.changeTypeImpl[A, B, S]

  def join(at: B => Any, combiner: SchemaExpr[_, _], sources: (A => Any)*): MigrationBuilder[A, B] =
    macro MigrationMacros.joinImpl[A, B]

  def split(at: A => Any, splitter: SchemaExpr[_, _], targets: (B => Any)*): MigrationBuilder[A, B] =
    macro MigrationMacros.splitImpl[A, B]
}
