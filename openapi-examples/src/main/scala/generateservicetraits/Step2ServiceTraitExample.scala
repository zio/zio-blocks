//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-openapi:0.0.56
//> using dep dev.zio::zio-blocks-next-codegen:0.0.56
//> using options -experimental
//> using file ServiceMapping.scala
// Run from this folder:  scala-cli run Step2ServiceTraitExample.scala -- ../../openapi/bookstore-service.json
// Or from openapi-examples:  sbt "runMain generateservicetraits.Step2ServiceTraitExample"

package generateservicetraits

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import zio.blocks.openapi._
import zio.blocks.schema.json._
import zio.blocks.codegen.emit._

/**
 * Generate Scala Service Traits from OpenAPI Paths — Map Operations to Methods
 *
 * Maps each operation of the bookstore service spec to a method of a service
 * trait, with an error type per operation, and prints the emitted source.
 */
object Step2ServiceTraitExample {
  def main(args: Array[String]): Unit = {
    val specPath     = if (args.length > 0) args(0) else "src/main/openapi/bookstore-service.json"
    val specText     = new String(Files.readAllBytes(Paths.get(specPath)), StandardCharsets.UTF_8)
    val api: OpenAPI = Json.parse(specText).flatMap(json => OpenAPICodec.openAPICodec.decode(json)) match {
      case Right(parsed) => parsed
      case Left(error)   => throw new IllegalArgumentException(s"Not an OpenAPI document: ${error.getMessage}")
    }
    println(ScalaEmitter.emit(ServiceMapping.serviceTrait(api, "com.example.bookstore"), EmitterConfig.default))
  }
}
