package zio.blocks.schema.toon

import zio.blocks.schema.Schema
import zio.test._

/**
 * Tests for transient field handling in TOON codec.
 *
 * Verifies that fields are correctly omitted based on configuration:
 *   - transientNone: omit None values
 *   - transientEmptyCollection: omit empty collections
 *   - transientDefaultValue: omit fields with default values
 */
object TransientFieldSpec extends ZIOSpecDefault {

  case class PersonWithOptional(
    name: String,
    age: Int,
    email: Option[String],
    phone: Option[String]
  )

  object PersonWithOptional {
    implicit val schema: Schema[PersonWithOptional] = Schema.derived
  }

  case class PersonWithCollections(
    name: String,
    tags: List[String],
    metadata: Map[String, String]
  )

  object PersonWithCollections {
    implicit val schema: Schema[PersonWithCollections] = Schema.derived
  }

  def spec = suite("TransientFieldSpec")(
    suite("transientNone handling")(
      test("should omit None values when transientNone is true") {
        val deriver = ToonBinaryCodecDeriver.withTransientNone(true)
        val codec   = PersonWithOptional.schema.derive(deriver)

        val person = PersonWithOptional("Alice", 30, Some("alice@example.com"), None)
        val toon   = codec.encodeToString(person)

        assertTrue(
          toon.contains("name: Alice"),
          toon.contains("age: 30"),
          toon.contains("email"), // Option is encoded as variant
          !toon.contains("phone") // None should be omitted
        )
      },
      test("should include None values when transientNone is false") {
        val deriver = ToonBinaryCodecDeriver.withTransientNone(false)
        val codec   = PersonWithOptional.schema.derive(deriver)

        val person = PersonWithOptional("Alice", 30, Some("alice@example.com"), None)
        val toon   = codec.encodeToString(person)

        assertTrue(
          toon.contains("name: Alice"),
          toon.contains("age: 30"),
          toon.contains("email"),
          toon.contains("phone"), // None should be included
          toon.contains("None")   // Encoded as None variant
        )
      }
    ),
    suite("transientEmptyCollection handling")(
      test("should omit empty collections when transientEmptyCollection is true") {
        val deriver = ToonBinaryCodecDeriver.withTransientEmptyCollection(true)
        val codec   = PersonWithCollections.schema.derive(deriver)

        val person = PersonWithCollections("Bob", List.empty, Map("key" -> "value"))
        val toon   = codec.encodeToString(person)

        assertTrue(
          toon.contains("name: Bob"),
          !toon.contains("tags"), // Should be omitted (empty list)
          toon.contains("metadata")
        )
      },
      test("should include empty collections when transientEmptyCollection is false") {
        val deriver = ToonBinaryCodecDeriver.withTransientEmptyCollection(false)
        val codec   = PersonWithCollections.schema.derive(deriver)

        val person = PersonWithCollections("Bob", List.empty, Map("key" -> "value"))
        val toon   = codec.encodeToString(person)

        assertTrue(
          toon.contains("name: Bob"),
          toon.contains("tags"), // Should be included
          toon.contains("metadata")
        )
      }
    ),
    suite("combined transient handling")(
      test("should handle multiple transient flags together") {
        val deriver = ToonBinaryCodecDeriver
          .withTransientNone(true)
          .withTransientEmptyCollection(true)
        val codec = PersonWithOptional.schema.derive(deriver)

        val person = PersonWithOptional("Charlie", 25, None, None)
        val toon   = codec.encodeToString(person)

        assertTrue(
          toon.contains("name: Charlie"),
          toon.contains("age: 25"),
          !toon.contains("email"), // None, should be omitted
          !toon.contains("phone")  // None, should be omitted
        )
      }
    )
  )
}
