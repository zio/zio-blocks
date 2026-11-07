//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-openapi:0.0.56
//> using dep dev.zio::zio-blocks-next-codegen:0.0.56
//> using options -experimental
//> using file ServiceMapping.scala
// Run from this folder:  scala-cli run CompleteExample.scala -- ../../openapi/bookstore-service.json
// Or from openapi-examples:  sbt "runMain generateservicetraits.CompleteExample"

package generateservicetraits

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import zio.blocks.openapi._
import zio.blocks.schema.json._
import zio.blocks.codegen.emit._

/**
 * Generate Scala Service Traits from OpenAPI Paths — Putting It Together
 *
 * Reads an OpenAPI document from a file, maps its paths to a service trait with
 * `ServiceMapping` and writes the Scala 3 source of the trait and its error
 * types to `BookstoreService.scala`.
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {
    val specPath  = if (args.length > 0) args(0) else "src/main/openapi/bookstore-service.json"
    val outputDir = if (args.length > 1) args(1) else "target/generated-bookstore-service"
    val specText  = new String(Files.readAllBytes(Paths.get(specPath)), StandardCharsets.UTF_8)

    val api: OpenAPI = Json.parse(specText).flatMap(json => OpenAPICodec.openAPICodec.decode(json)) match {
      case Right(parsed) => parsed
      case Left(error)   => throw new IllegalArgumentException(s"Not an OpenAPI document: ${error.getMessage}")
    }

    val outputFile = Paths.get(outputDir, "BookstoreService.scala")
    Files.createDirectories(Paths.get(outputDir))
    Files.write(
      outputFile,
      ScalaEmitter
        .emit(ServiceMapping.serviceTrait(api, "com.example.bookstore"), EmitterConfig.default)
        .getBytes(StandardCharsets.UTF_8)
    )
    println(s"Generated ${api.info.title.filter(_.isLetterOrDigit)}Service into $outputFile")
  }
}
