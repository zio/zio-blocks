// scalafmt: { maxColumn = 500 }
package zio.blocks.schema.migration
import zio.blocks.schema.SchemaExpr
import scala.language.experimental.macros
trait MigrationMacros[A, B] {
  def addField[T](name: String, default: SchemaExpr[A, T]): MigrationBuilder[A, B] = macro MigrationBuilderMacros.addFieldImpl[A, B, T]
  def dropField[T](name: String): MigrationBuilder[A, B] = macro MigrationBuilderMacros.dropFieldImpl[A, B, T]
  def dropField[T](name: String, defaultForReverse: Option[SchemaExpr[B, T]]): MigrationBuilder[A, B] = macro MigrationBuilderMacros.dropFieldWithDefaultImpl[A, B, T]
  def renameField[T1, T2](from: String, to: String): MigrationBuilder[A, B] = macro MigrationBuilderMacros.renameFieldImpl[A, B, T1, T2]
  def transformField[T](path: String, transform: SchemaExpr[T, T]): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformFieldImpl[A, B, T]
  def mandateField[T](path: String, default: SchemaExpr[A, T]): MigrationBuilder[A, B] = macro MigrationBuilderMacros.mandateFieldImpl[A, B, T]
  def optionalizeField[T](path: String): MigrationBuilder[A, B] = macro MigrationBuilderMacros.optionalizeFieldImpl[A, B, T]
  def changeFieldType[T1, T2](path: String, converter: SchemaExpr[T1, T2]): MigrationBuilder[A, B] = macro MigrationBuilderMacros.changeFieldTypeImpl[A, B, T1, T2]
  def transformElements[T](path: String, transform: SchemaExpr[T, T]): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformElementsImpl[A, B, T]
  def transformKeys[K, V](path: String, transform: SchemaExpr[K, K]): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformKeysImpl[A, B, K, V]
  def transformValues[K, V](path: String, transform: SchemaExpr[V, V]): MigrationBuilder[A, B] = macro MigrationBuilderMacros.transformValuesImpl[A, B, K, V]
  def build: Migration[A, B] = macro MigrationBuilderMacros.buildImpl[A, B]
  def buildPartial: DynamicMigration = macro MigrationBuilderMacros.buildPartialImpl[A, B]
}
