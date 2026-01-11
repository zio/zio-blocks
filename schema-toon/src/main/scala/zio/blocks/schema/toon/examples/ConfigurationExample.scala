package zio.blocks.schema.toon.examples

import zio.blocks.schema.Schema
import zio.blocks.schema.toon.{ToonBinaryCodecDeriver, ArrayFormat}
import zio.blocks.schema.json.{NameMapper, DiscriminatorKind}

/**
 * Example demonstrating various deriver configuration options.
 *
 * Run with:
 * `sbt "schema-toon/runMain zio.blocks.schema.toon.examples.ConfigurationExample"`
 */
object ConfigurationExample extends App {

  case class UserProfile(
    firstName: String,
    lastName: String,
    emailAddress: String,
    phoneNumber: String,
    isActive: Boolean
  )

  object UserProfile {
    implicit val schema: Schema[UserProfile] = Schema.derived
  }

  sealed trait Animal
  case class Dog(name: String, breed: String) extends Animal
  case class Cat(name: String, color: String) extends Animal

  object Animal {
    implicit val schema: Schema[Animal] = Schema.derived
  }

  val user = UserProfile(
    firstName = "John",
    lastName = "Doe",
    emailAddress = "john.doe@example.com",
    phoneNumber = "555-1234",
    isActive = true
  )

  println("=== Deriver Configuration Examples ===")
  println()

  // 1. Default configuration
  println("--- Default Configuration ---")
  val defaultCodec = UserProfile.schema.derive(ToonBinaryCodecDeriver)
  println(defaultCodec.encodeToString(user))
  println()

  // 2. Snake case field names
  println("--- Snake Case Field Names ---")
  val snakeCaseCodec = UserProfile.schema.derive(
    ToonBinaryCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase)
  )
  println(snakeCaseCodec.encodeToString(user))
  println()

  // 3. Kebab case field names
  println("--- Kebab Case Field Names ---")
  val kebabCaseCodec = UserProfile.schema.derive(
    ToonBinaryCodecDeriver.withFieldNameMapper(NameMapper.KebabCase)
  )
  println(kebabCaseCodec.encodeToString(user))
  println()

  // 4. Array format configuration
  println("--- Array Formats ---")
  println()

  val numbers = List(1, 2, 3, 4, 5)

  println("Inline format:")
  val inlineCodec = Schema[List[Int]].derive(
    ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Inline)
  )
  println(inlineCodec.encodeToString(numbers))
  println()

  println("List format:")
  val listCodec = Schema[List[Int]].derive(
    ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
  )
  println(listCodec.encodeToString(numbers))
  println()

  // 5. Discriminator kind for variants
  println("--- Discriminator Kinds ---")
  println()

  val dog: Animal = Dog("Rex", "German Shepherd")

  println("Key discriminator (default):")
  val keyCodec = Animal.schema.derive(
    ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Key)
  )
  println(keyCodec.encodeToString(dog))
  println()

  println("Field discriminator:")
  val fieldCodec = Animal.schema.derive(
    ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("type"))
  )
  println(fieldCodec.encodeToString(dog))
  println()

  // 6. Combined configuration
  println("--- Combined Configuration ---")
  val combinedCodec = UserProfile.schema.derive(
    ToonBinaryCodecDeriver
      .withFieldNameMapper(NameMapper.SnakeCase)
      .withTransientNone(true)
      .withTransientEmptyCollection(true)
  )
  println(combinedCodec.encodeToString(user))
  println()

  // 7. Show all available configuration methods
  println("--- Available Configuration Methods ---")
  println("""
            |ToonBinaryCodecDeriver
            |  .withFieldNameMapper(NameMapper)       // Transform field names
            |  .withCaseNameMapper(NameMapper)        // Transform case names in variants
            |  .withDiscriminatorKind(DiscriminatorKind) // Variant discriminator style
            |  .withArrayFormat(ArrayFormat)          // Array encoding format
            |  .withTransientNone(Boolean)            // Omit None values
            |  .withTransientEmptyCollection(Boolean) // Omit empty collections
            |  .withTransientDefaultValue(Boolean)    // Omit default values
            |  .withRejectExtraFields(Boolean)        // Reject unknown fields on decode
            |  .withEnumValuesAsStrings(Boolean)      // Encode enums as strings
            |  .withRequireOptionFields(Boolean)      // Require optional fields
            |  .withRequireCollectionFields(Boolean)  // Require collection fields
            |  .withRequireDefaultValueFields(Boolean)// Require default value fields
  """.stripMargin)
}
