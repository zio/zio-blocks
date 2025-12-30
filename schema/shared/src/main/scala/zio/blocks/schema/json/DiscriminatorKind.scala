package zio.blocks.schema.json

/**
 * Represents the strategy for specifying the discriminator used in a JSON
 * schema for distinguishing between different subtypes of a sealed hierarchy.
 *
 * Implementations of this trait define how the discriminator is represented in
 * the serialized JSON, if any.
 *
 * The possible discriminator kinds include:
 *   - `Field` with adding a custom field named as specified in the `name`
 *     parameter that contains a value of the subtype discriminator, e.g.
 *     `{"type": "Person", ...}` for
 *     `case class Person(...) extends SealedTrait`
 *   - `Key`, where the discriminator is represented as a JSON object key with
 *     wrapping of the encoded value in a JSON object, e.g. `{"Person":{...}}`
 *     for `case class Person(...) extends SealedTrait`.
 *   - `None`, where no discriminator is used, meaning subtype differentiation
 *     is handled by sequential attempts to parse all possible subtypes,
 *     starting from the first subtype, until a successful parse is found, or an
 *     error is thrown if none of the subtypes can be parsed from the input.
 */
sealed trait DiscriminatorKind

object DiscriminatorKind {

  /**
   * Represents a discriminator strategy where a specific field in the JSON
   * object is used to identify the subtype of a sealed hierarchy, e.g.
   * `{"$type": "SubtypeIdentifier", ...}`
   *
   * Can be used only for serializing sealed hierarchy subtypes of non-abstract
   * classes only (not union types).
   *
   * Beware that parsing may be too slow for deeply nested or recursive data
   * structures when the discriminator field is placed at the end of the JSON
   * object.
   *
   * Don't use it for untrusted input that should be parsed to deeply nested or
   * recursive data structures to avoid potential security vulnerabilities of
   * DoS/DoW attacks.
   *
   * @param name
   *   The name of the discriminator field to be added to the JSON object. This
   *   field value will contain the subtype identifier as its value.
   */
  case class Field(name: String) extends DiscriminatorKind

  /**
   * Represents a discriminator strategy where the subtype identifier is encoded
   * as the key of a JSON object.
   *
   * It is a default strategy for serializing and deserializing of all kinds of
   * variants (sealed hierarchies, union types, etc.).
   *
   * In this strategy, the serialized representation of the subtype wraps the
   * encoded value in a JSON object keyed by the subtype's discriminator, e.g.,
   * `{"SubtypeIdentifier": {...}}`.
   *
   * This approach enables subtype differentiation based solely on the key of
   * the JSON object for the sealed hierarchy.
   */
  case object Key extends DiscriminatorKind

  /**
   * Represents the absence of a discriminator strategy for serializing and
   * deserializing sealed hierarchy subtypes or union types.
   *
   * When this strategy is used, subtype differentiation is performed without
   * adding any explicit discriminator field or key to the serialized JSON.
   * Instead, deserialization attempts to match the input JSON data sequentially
   * against all possible subtypes of the sealed hierarchy until a successful
   * match is found.
   *
   * This approach relies on the order and structure of subtype definitions and
   * may result in parsing errors if no matching subtype is found.
   *
   * Beware that this strategy hides error messages of underlying parsing errors
   * and parsing be too slow for large hierarchies of subtypes or deeply nested
   * or recursive data structures.
   *
   * Don't use it for untrusted input that should be parsed into deeply nested
   * or recursive data structures to avoid potential security vulnerabilities of
   * DoS/DoW attacks.
   */
  case object None extends DiscriminatorKind
}
