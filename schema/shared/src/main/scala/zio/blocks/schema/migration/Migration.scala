package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * Type-safe builder API for Schema Migrations.
 * Internally compounds `DynamicMigration` algebra nodes natively using optics.
 */
class Migration[A, B](val untyped: DynamicMigration) {

  def >>> [C](that: Migration[B, C]): Migration[A, C] =
    new Migration(this.untyped >>> that.untyped)

  def addField(
    optic: DynamicOptic,
    defaultValue: DynamicValue
  ): Migration[A, B] = {
    new Migration(this.untyped >>> DynamicMigration.AddField(optic, defaultValue))
  }

  def deleteField(optic: DynamicOptic): Migration[A, B] = {
    new Migration(this.untyped >>> DynamicMigration.DeleteField(optic))
  }
}

object Migration {
  def identity[A]: Migration[A, A] = new Migration(DynamicMigration.Identity)
}
