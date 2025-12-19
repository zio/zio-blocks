package zio.blocks.schema

trait SchemaVersionSpecific {
  // Scala 2 does not support derived schemas
  def derived[A]: Schema[A] = throw new UnsupportedOperationException("Derived schemas are not supported in Scala 2")
}