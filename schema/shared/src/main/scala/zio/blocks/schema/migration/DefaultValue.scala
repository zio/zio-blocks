package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema}

/**
 * Migration-specific helpers for default values.
 */
object DefaultValue {

  /**
   * Extract the default value from a schema, converting to DynamicValue.
   */
  def apply[A](schema: Schema[A]): Either[String, DynamicValue] =
    schema.getDefaultValue match {
      case Some(default) => Right(schema.toDynamicValue(default))
      case None          => Left(s"No default value for ${schema.reflect.typeId}")
    }

  /**
   * Create a Resolved from a schema's default value.
   */
  def fromSchema[A](schema: Schema[A]): Resolved =
    Resolved.DefaultValue(apply(schema))

  /**
   * Create a Resolved from a known value.
   */
  def literal[A](value: A, schema: Schema[A]): Resolved =
    Resolved.Literal(schema.toDynamicValue(value))

  /**
   * Marker that indicates "use schema default". Used in builder API before
   * resolution.
   */
  val UseSchemaDefault: Resolved = Resolved.DefaultValue(Left("Schema default not yet resolved"))
}
