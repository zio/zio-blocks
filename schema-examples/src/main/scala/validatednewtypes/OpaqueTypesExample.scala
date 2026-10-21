package validatednewtypes

import zio.blocks.schema._

/**
 * Validated Newtypes: Opaque Types (Scala 3 only)
 *
 * Defines Isbn as a Scala 3 opaque type with a `given Schema` built with
 * Schema#transform, then decodes a good and a bad payload. This file compiles
 * on Scala 3 only.
 *
 * Run with: cd schema-examples && sbt "runMain
 * validatednewtypes.OpaqueTypesExample"
 */
object OpaqueTypesExample extends App {

  object catalog {
    opaque type Isbn = String

    object Isbn {
      def parse(input: String): Either[SchemaError, Isbn] = {
        val digits = input.filterNot(c => c == '-' || c == ' ')
        if (digits.length == 13 && digits.forall(_.isDigit)) Right(digits)
        else Left(SchemaError.validationFailed(s"ISBN must have 13 digits, got '$input'"))
      }

      given Schema[Isbn] = Schema[String].transform(s => parse(s).fold(e => throw e, identity), identity)

      extension (isbn: Isbn) def value: String = isbn
    }
  }

  final case class Book(isbn: catalog.Isbn, title: String) derives Schema

  val good = Schema[Book].jsonCodec.decode("""{"isbn":"978-1-61729-065-7","title":"Functional Programming in Scala"}""")
  println(good.map(b => (b.isbn.value, b.title)))

  val decoded = Schema[Book].jsonCodec.decode("""{"isbn":"978-ZIO","title":"Zionomicon"}""")
  decoded.left.foreach(e => println(e.message))
}
