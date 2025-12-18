package zio.blocks.schema

trait SchemaVersionSpecific {
  def derived[A]: Schema[A] = throw new NotImplementedError("Schema macros for Scala 2 are not implemented in this migration demo")
}
