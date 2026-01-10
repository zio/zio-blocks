package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * Version-specific methods for MigrationBuilder (Scala 2).
 * 
 * Provides string-based field selection for Scala 2 compatibility.
 */
private[migration] trait MigrationBuilderPlatform[A, B] { self: MigrationBuilder[A, B] =>
  
  /**
   * Rename a field using string names.
   * 
   * Example: `.renameField("firstName", "fullName")`
   */
  def renameField(oldFieldName: String, newFieldName: String): MigrationBuilder[A, B] = {
    new MigrationBuilder[A, B](
      self.actions :+ MigrationAction.RenameField(oldFieldName, newFieldName)
    )(self.fromSchema, self.toSchema)
  }
  
  /**
   * Drop a field using string name.
   * 
   * Example: `.dropField("obsoleteField")`
   */
  def dropField(fieldName: String): MigrationBuilder[A, B] = {
    new MigrationBuilder[A, B](
      self.actions :+ MigrationAction.DropField(fieldName)
    )(self.fromSchema, self.toSchema)
  }
  
  /**
   * Make a field optional using string name.
   * 
   * Example: `.optionalize("fieldName")`
   */
  def optionalize(fieldName: String): MigrationBuilder[A, B] = {
    new MigrationBuilder[A, B](
      self.actions :+ MigrationAction.Optionalize(fieldName)
    )(self.fromSchema, self.toSchema)
  }
}
