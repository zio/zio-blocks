//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-openapi:0.0.56
//> using options -experimental
//> using file BookstoreSpec.scala
// Run from this folder:  scala-cli run Step1ParseSpecExample.scala BookstoreSpec.scala
// Or from openapi-examples:  sbt "runMain generatescalatypesfromopenapi.Step1ParseSpecExample"

package generatescalatypesfromopenapi

import zio.blocks.openapi._
import zio.blocks.schema.json._

import BookstoreSpec.bookstoreJson

/**
 * Generate Scala Types from an OpenAPI Spec — Parse the Spec
 *
 * Parses the bookstore spec with `Json.parse` and `openAPICodec`, then prints
 * its title and component names, and the error for a document without `info`.
 */
object Step1ParseSpecExample {
  def parseSpec(text: String): OpenAPI =
    Json.parse(text).flatMap(json => OpenAPICodec.openAPICodec.decode(json)) match {
      case Right(api)  => api
      case Left(error) => throw new IllegalArgumentException(s"Not an OpenAPI document: ${error.getMessage}")
    }

  val api: OpenAPI = parseSpec(bookstoreJson)

  def main(args: Array[String]): Unit = {
    println(s"${api.info.title} ${api.info.version}")
    println(api.components.toList.flatMap(_.schemas.toList).map(_._1).mkString(", "))

    println(Json.parse("""{"openapi": "3.1.0"}""").flatMap(json => OpenAPICodec.openAPICodec.decode(json)))
  }
}
