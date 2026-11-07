package customizejsonencoding

import zio.blocks.schema._

/**
 * Customize JSON: Leave a Field Out of the JSON
 *
 * Leaves one field out in both directions with Modifier.transient and another
 * only when encoding with Modifier.encodeTransient.
 *
 * Run with: cd schema-examples && sbt "runMain
 * customizejsonencoding.TransientFieldsExample"
 */
object TransientFieldsExample extends App {

  case class Book(
    title: String,
    @Modifier.transient() searchScore: Double = 0.0,
    @Modifier.encodeTransient() legacyId: Option[Long]
  )
  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  // The encoder writes neither field
  println(Book("Zionomicon", 0.93, Some(42L)).toJsonString)

  // When decoding, a transient field ignores the input and takes its default,
  // while an encode-transient field is still read
  println("""{"title":"Zionomicon","searchScore":0.5,"legacyId":42}""".fromJson[Book])

  // A transient field without a default value (and that is not an Option or a
  // collection) fails at compile time, so that case is not shown here.
}
