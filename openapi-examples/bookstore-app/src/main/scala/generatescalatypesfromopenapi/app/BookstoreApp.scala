// Run from openapi-examples:  sbt bookstoreApp/run
// The generated types come from `openapi-examples/src/main/openapi/bookstore.json`; sbt generates them before it compiles.

package generatescalatypesfromopenapi.app

import java.nio.charset.StandardCharsets

import com.example.bookstore._
import zio.blocks.openapi._
import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Generate Scala Types from an OpenAPI Spec — Run the Generator on Every Build
 *
 * Reads the examples of `Book` and `Order` from the spec on the classpath,
 * decodes them into the generated types, and encodes the order back to JSON.
 */
object BookstoreApp {
  def main(args: Array[String]): Unit = {
    val specText = new String(getClass.getResourceAsStream("/bookstore.json").readAllBytes(), StandardCharsets.UTF_8)
    val api      =
      Json.parse(specText).flatMap(json => OpenAPICodec.openAPICodec.decode(json)).fold(error => throw error, identity)

    def example(name: String): Json =
      api.components.flatMap(_.schemas.get(name)) match {
        case Some(ReferenceOr.Value(schemaObject)) if schemaObject.example.isDefined => schemaObject.example.get
        case _                                                                       => throw new IllegalArgumentException(s"No example for $name")
      }

    val book  = Schema[Book].jsonCodec.decode(example("Book"))
    val order = Schema[Order].jsonCodec.decode(example("Order"))
    println(book)
    println(order)
    println(order.map(_.toJsonString))
  }
}
