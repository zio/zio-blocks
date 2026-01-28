package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * A selector for a field that captures the field name as a type parameter.
 *
 * Created by the `select` macro from a selector expression like `_.fieldName`.
 * The Name type parameter captures the field name at the type level, allowing
 * builder methods to track which fields have been handled through type
 * constraints.
 *
 * This is a key component of the non-inline builder design:
 *   - The `select()` macro is the ONLY inline part of the migration API
 *   - It extracts the field name from `_.fieldName` and captures it as a
 *     singleton type
 *   - Builder methods are regular (non-inline) methods that use the Name type
 *     parameter
 *   - This allows builders to be stored in vals without losing type tracking
 *
 * Example usage:
 * {{{
 * // select() is inline - extracts field name into type parameter
 * val ageSelector = select[Person](_.age)
 * // Type: FieldSelector[Person, Int, "age"]
 *
 * // Builder methods use the type parameter, not macros
 * val builder = Migration.newBuilder[PersonV0, PersonV1]
 *   .addField(select(_.age), 0)         // "age" removed from TgtRemaining
 *   .renameField(select(_.name), select(_.fullName))  // Both fields tracked
 *   .build                              // Compiles only if all fields handled
 * }}}
 *
 * @tparam S
 *   The schema type (source or target of migration)
 * @tparam F
 *   The field type
 * @tparam Name
 *   The field name as a singleton string type (e.g., "age", "name")
 */
final class FieldSelector[S, F, Name <: String](
  val name: Name,
  val optic: DynamicOptic
) {

  /**
   * The field name as a runtime String value. Same as `name` but without the
   * singleton type.
   */
  def fieldName: String = name

  override def toString: String = s"FieldSelector($name)"

  override def equals(other: Any): Boolean = other match {
    case that: FieldSelector[_, _, _] => this.name == that.name && this.optic == that.optic
    case _                            => false
  }

  override def hashCode(): Int = {
    val state = Seq(name, optic)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

object FieldSelector {

  /**
   * Create a FieldSelector with a specific name and optic.
   *
   * This is used by the `select()` macro to construct FieldSelectors. Users
   * should use the `select()` macro instead of calling this directly.
   *
   * @param name
   *   The field name (must match the Name type parameter)
   * @param optic
   *   The DynamicOptic for accessing this field
   * @return
   *   A FieldSelector with the name captured as a type parameter
   */
  def apply[S, F, Name <: String](name: Name, optic: DynamicOptic): FieldSelector[S, F, Name] =
    new FieldSelector(name, optic)

  /**
   * Create a FieldSelector for a simple field access (no nested path).
   *
   * Convenience constructor that creates the DynamicOptic internally.
   *
   * @param name
   *   The field name
   * @return
   *   A FieldSelector with a single-field optic
   */
  def field[S, F, Name <: String](name: Name): FieldSelector[S, F, Name] =
    new FieldSelector(name, DynamicOptic.root.field(name))
}
