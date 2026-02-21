package zio.blocks.schema.migration

/**
 * MigrationState provides a type-level representation of schema
 * transformations. These are implemented as "Phantom Types," meaning they exist
 * only during compilation to enforce static validation and carry zero runtime
 * overhead. * The states are structured in a linked-list fashion, where each
 * operation maintains a reference to its predecessor (Prev), enabling the
 * compiler to verify the entire migration chain.
 */
object MigrationState {

  /** Base trait for all migration states. */
  sealed trait State

  /**
   * Represents the initial state before any migration operations are applied.
   */
  sealed trait Empty extends State

  /**
   * Tracks the addition of a new field.
   * @tparam Name
   *   The string literal type of the field name.
   * @tparam T
   *   The data type of the new field.
   * @tparam Prev
   *   The preceding state in the migration chain.
   */
  sealed trait AddField[Name <: String, T, Prev <: State] extends State

  /**
   * Tracks the renaming of an existing field.
   * @tparam From
   *   Old field name.
   * @tparam To
   *   New field name.
   * @tparam Prev
   *   Preceding state.
   */
  sealed trait RenameField[From <: String, To <: String, Prev <: State] extends State

  /**
   * Tracks the removal of a field from the schema.
   */
  sealed trait DropField[Name <: String, Prev <: State] extends State

  /**
   * Tracks a type transformation for a specific field.
   * @tparam From
   *   The original type.
   * @tparam To
   *   The target type.
   */
  sealed trait ChangeType[Name <: String, From, To, Prev <: State] extends State

  /**
   * Transformation trackers for field nullability and constraints.
   */
  sealed trait MandateField[Name <: String, T, Prev <: State]     extends State
  sealed trait OptionalizeField[Name <: String, T, Prev <: State] extends State

  /**
   * Trackers for collection-level transformations.
   */
  sealed trait TransformElements[Name <: String, T, Prev <: State] extends State
  sealed trait TransformKeys[Name <: String, K, Prev <: State]     extends State
  sealed trait TransformValues[Name <: String, V, Prev <: State]   extends State

  /**
   * Represents nested migrations or sum-type (Enum) case transformations. This
   * implements a recursive state structure where a sub-migration's entire chain
   * (InnerS) is embedded within the outer migration flow. * @tparam Name The
   * case or nested entity name.
   * @tparam InnerS
   *   The complete state chain of the nested migration.
   * @tparam Prev
   *   The preceding state of the parent migration.
   */
  sealed trait TransformCase[Name <: String, InnerS <: State, Prev <: State] extends State
}
