package zio.blocks.schema.toon

/**
 * Represents the strategy for specifying the discriminator used in a TOON
 * schema for distinguishing between different subtypes of a sealed hierarchy.
 *
 * The possible discriminator kinds include:
 *   - `Field` with adding a custom field that contains the subtype
 *     discriminator value
 *   - `Key`, where the discriminator is represented as a key with wrapping of
 *     the encoded value (default)
 *   - `None`, where no discriminator is used and subtypes are tried
 *     sequentially
 */
sealed trait DiscriminatorKind

object DiscriminatorKind {

  /**
   * Represents a discriminator strategy where a specific field in the TOON
   * object is used to identify the subtype of a sealed hierarchy.
   *
   * Example:
   * {{{
   * $type: Person
   * name: Alice
   * }}}
   *
   * @param name
   *   The name of the discriminator field to be added to the TOON object.
   */
  case class Field(name: String) extends DiscriminatorKind

  /**
   * Represents a discriminator strategy where the subtype identifier is encoded
   * as the key of a TOON object.
   *
   * This is the default strategy for serializing and deserializing variants.
   *
   * Example:
   * {{{
   * Person:
   *   name: Alice
   * }}}
   */
  case object Key extends DiscriminatorKind

  /**
   * Represents the absence of a discriminator strategy for serializing and
   * deserializing sealed hierarchy subtypes.
   *
   * When this strategy is used, deserialization attempts to match the input
   * TOON data sequentially against all possible subtypes until a successful
   * match is found.
   *
   * Warning: This strategy may be slow for large hierarchies and hides parsing
   * error messages.
   */
  case object None extends DiscriminatorKind
}
