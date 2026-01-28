package zio.blocks.schema.migration

/**
 * Type-level operations for tracking field sets in migrations.
 *
 * Field sets are represented as tuples of string literal types, e.g.,
 * `("name", "age", "email")` represents a set of three fields.
 *
 * The type-level operations (Remove, Contains, IsEmpty) are implemented using
 * Scala 3 match types, which provide compile-time type computation.
 *
 * These operations are used by [[MigrationBuilder]] to track which fields have
 * been handled during migration construction, ensuring compile-time
 * completeness checking.
 */
object FieldSet {

  /**
   * Remove a field name from a tuple of field names.
   *
   * Example:
   * {{{
   * type Fields = ("name", "age", "email")
   * type Result = Remove[Fields, "age"]  // ("name", "email")
   * }}}
   */
  type Remove[S <: Tuple, Name <: String] <: Tuple = S match {
    case EmptyTuple   => EmptyTuple
    case Name *: tail => tail
    case head *: tail => head *: Remove[tail, Name]
  }

  /**
   * Check if a field name exists in a tuple of field names.
   *
   * Returns `true` (as a type) if the name is found, `false` otherwise.
   *
   * Example:
   * {{{
   * type Fields = ("name", "age")
   * type HasName = Contains[Fields, "name"]  // true
   * type HasEmail = Contains[Fields, "email"] // false
   * }}}
   */
  type Contains[S <: Tuple, Name <: String] <: Boolean = S match {
    case EmptyTuple => false
    case Name *: _  => true
    case _ *: tail  => Contains[tail, Name]
  }

  /**
   * Check if a tuple is empty.
   *
   * Example:
   * {{{
   * type Empty = IsEmpty[EmptyTuple]        // true
   * type NonEmpty = IsEmpty[("name" *: EmptyTuple)] // false
   * }}}
   */
  type IsEmpty[S <: Tuple] <: Boolean = S match {
    case EmptyTuple => true
    case _          => false
  }

  /**
   * Get the size of a tuple (number of fields). Note: This returns a type-level
   * representation, use Tuple.Size for runtime.
   */
  import scala.compiletime.ops.int.+
  type Size[S <: Tuple] <: Int = S match {
    case EmptyTuple => 0
    case _ *: tail  => Size[tail] + 1
  }

  /**
   * Concatenate two tuples.
   */
  type Concat[S1 <: Tuple, S2 <: Tuple] <: Tuple = S1 match {
    case EmptyTuple   => S2
    case head *: tail => head *: Concat[tail, S2]
  }
}
