package zio.blocks.schema

import scala.language.experimental.macros

trait MigrationBuilderVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>

  def addField[T](selector: B => T, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] = macro MigrationMacros.addField[A, B, T]

  def renameField[T](from: A => T, to: B => T): MigrationBuilder[A, B] = macro MigrationMacros.renameField[A, B, T]

  def removeField[T](selector: A => T): MigrationBuilder[A, B] = macro MigrationMacros.removeField[A, B, T]

  def renameCase[T](from: A => T, to: B => T): MigrationBuilder[A, B] = macro MigrationMacros.renameCase[A, B, T]
}
