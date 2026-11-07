package writeandexportopenapi

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.docs._
import zio.blocks.openapi._
import zio.blocks.openapi.OpenAPICodec._
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, WriterConfig}

/**
 * How to Write an OpenAPI Document and Export It as JSON or YAML: Step 1 —
 * Generating Component Schemas From Your Types
 *
 * Prints the SchemaObject derived from Book, the $ref that toRefSchema returns,
 * and the component names from OpenAPIGen.schemas.
 *
 * Run with: cd openapi-examples && sbt "runMain
 * writeandexportopenapi.Step1ComponentSchemasExample"
 */
object Step1ComponentSchemasExample extends App {

  final case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  final case class ApiError(message: String)

  object ApiError {
    implicit val schema: Schema[ApiError] = Schema.derived
  }

  val sampleBooks: List[Book] = List(
    Book("Zionomicon", "John A. De Goes", 2024, None),
    Book("Programming in Scala", "Martin Odersky", 2008, Some("9780981531601")),
    Book("Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"))
  )

  println(Schema[Book].toOpenAPISchema.toJson.print(WriterConfig.withIndentionStep2))

  val (bookRef, _)  = Schema[Book].toRefSchema
  val (errorRef, _) = Schema[ApiError].toRefSchema

  println(bookRef)

  val componentSchemas: Map[String, SchemaObject] = OpenAPIGen.schemas(Schema[Book], Schema[ApiError])

  println(componentSchemas.keys)
}
