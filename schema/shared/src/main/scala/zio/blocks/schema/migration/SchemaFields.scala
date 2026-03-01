package zio.blocks.schema.migration

import zio.blocks.schema.Schema

/**
 * Type class that extracts field names from a Schema[A] as a type-level
 * representation.
 *
 * This enables compile-time tracking of which fields have been handled during
 * migration construction. The [[MigrationBuilder]] uses this to validate that
 * all source fields are consumed and all target fields are provided.
 *
 * The actual field name extraction is implemented via macros in
 * platform-specific code (Scala 2 and Scala 3).
 *
 * @tparam A
 *   The type whose schema fields are being extracted
 */
trait SchemaFields[A] {

  /**
   * The field names as a type-level tuple (Scala 3) or HList (Scala 2). For
   * non-record types, this will be an empty tuple/HList.
   */
  type Fields

  /**
   * Get the field names as a runtime list of strings. This is useful for
   * debugging and error messages.
   */
  def fieldNames: List[String]
}

object SchemaFields {

  /**
   * Type alias to access the Fields type member.
   */
  type Aux[A, F] = SchemaFields[A] { type Fields = F }

  /**
   * Create a SchemaFields instance with the given field names. Used internally
   * by macros.
   */
  def apply[A, F](names: List[String]): SchemaFields.Aux[A, F] =
    new SchemaFields[A] {
      type Fields = F
      def fieldNames: List[String] = names
    }

  /**
   * Create a SchemaFields instance for a non-record type (empty fields). The
   * type parameter E should be EmptyTuple (Scala 3) or HNil (Scala 2).
   */
  def emptyWith[A, E]: SchemaFields.Aux[A, E] =
    new SchemaFields[A] {
      type Fields = E
      def fieldNames: List[String] = Nil
    }

  /**
   * Extract field names from a Schema at runtime. This is used as a fallback
   * when compile-time extraction isn't available.
   */
  def extractFieldNames[A](schema: Schema[A]): List[String] =
    schema.reflect.asRecord match {
      case Some(record) => record.fields.map(_.name).toList
      case None         => Nil
    }
}
