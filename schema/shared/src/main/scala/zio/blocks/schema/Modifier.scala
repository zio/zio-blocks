package zio.blocks.schema

import scala.annotation.meta.field
import scala.annotation.StaticAnnotation

/**
 * A sealed trait that represents a modifier used to annotate terms or reflect
 * values. Modifiers are used to provide metadata or additional configuration
 * associated with terms or reflect values.
 */
sealed trait Modifier extends StaticAnnotation

object Modifier {

  /**
   * `Term` represents a sealed trait for modifiers that annotate terms: record
   * fields or variant cases.
   *
   * The following are the known subtypes of `Term`:
   *   - `transient`: Used to indicate that a field should not be persisted or
   *     serialized.
   *   - `rename`: Used to specify a new name for a term, typically useful in
   *     serialization scenarios.
   *   - `alias`: Provides an alternative name (alias) for a term.
   *   - `config`: Represents a key-value pair for attaching additional
   *     configuration metadata to terms.
   */
  sealed trait Term extends Modifier

  /**
   * A modifier that marks a term (such as a field) as transient.
   */
  @field case class transient() extends Term

  /**
   * A modifier used to specify a new name for a term.
   *
   * @param name
   *   The new name to apply to the term.
   */
  @field case class rename(name: String) extends Term

  /**
   * A modifier representing an alias for a term.
   *
   * @param name
   *   The alias name for the term.
   */
  @field case class alias(name: String) extends Term

  /**
   * Represents a sealed trait for modifiers that annotate reflect values.
   */
  sealed trait Reflect extends Modifier

  /**
   * A configuration key-value pair, which can be attached to any type of
   * reflect values. The convention for keys is `<format>.<property>`. For
   * example, `protobuf.field-id` is a key that specifies the field id for a
   * protobuf format.
   */
  @field case class config(key: String, value: String) extends Term with Reflect
}

package annotation {
  import scala.annotation.meta.field

  /**
   * Overrides the default case name used in schema-based serialization.
   */
  final case class caseName(name: String) extends Modifier.Term

  /**
   * Declares additional case names accepted during decoding.
   */
  final case class caseNameAliases(aliases: String*) extends Modifier.Term

  /**
   * Overrides the default field name used in schema-based serialization.
   */
  @field final case class fieldName(name: String) extends Modifier.Term

  /**
   * Declares additional field names accepted during decoding.
   */
  @field final case class fieldNameAliases(aliases: String*) extends Modifier.Term

  /**
   * Marks a field as transient so it is omitted during serialization.
   */
  @field final case class transientField() extends Modifier.Term

  /**
   * Marks a case as transient so it is omitted during serialization.
   */
  final case class transientCase() extends Modifier.Term

  /**
   * Sets the discriminator field name for schema-based sum type encoding.
   */
  final case class discriminatorName(tag: String) extends Modifier.Reflect

  /**
   * Disables discriminators for schema-based sum type encoding.
   */
  final class noDiscriminator extends Modifier.Reflect

  /**
   * Rejects extra fields during decoding.
   */
  final class rejectExtraFields extends Modifier.Reflect
}

package bson {
  import scala.annotation.meta.field

  /**
   * Overrides the BSON field name for a case class field.
   */
  @field final case class bsonField(name: String) extends Modifier.Term

  /**
   * Overrides the BSON case name for sum type encoding.
   */
  final case class bsonHint(name: String) extends Modifier.Term

  /**
   * Sets the BSON discriminator field name for sum type encoding.
   */
  final case class bsonDiscriminator(name: String) extends Modifier.Reflect

  /**
   * Rejects extra fields during BSON decoding.
   */
  final class bsonNoExtraFields extends Modifier.Reflect

  /**
   * Excludes a field from BSON serialization and deserialization.
   */
  @field final class bsonExclude extends Modifier.Term
}
