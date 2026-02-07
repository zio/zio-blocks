package zio.blocks.schema

package object migration {
  type MigrationError = SchemaError
  val MigrationError: SchemaError.type = SchemaError
}
