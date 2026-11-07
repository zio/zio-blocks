//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-openapi:0.0.56
//> using dep dev.zio::zio-blocks-next-codegen:0.0.56
//> using options -experimental
//> using file ServiceMapping.scala
// Run from this folder:  scala-cli run Step1ReadPathsExample.scala -- ../../openapi/bookstore-service.json
// Or from openapi-examples:  sbt "runMain generateservicetraits.Step1ReadPathsExample"

package generateservicetraits

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import zio.blocks.openapi._
import zio.blocks.schema.json._

/**
 * Generate Scala Service Traits from OpenAPI Paths — Read the Operations
 *
 * Parses the bookstore service spec and prints each operation with its method,
 * path, operation id, parameters, request body and success type.
 */
object Step1ReadPathsExample {
  def main(args: Array[String]): Unit = {
    val specPath = if (args.length > 0) args(0) else "src/main/openapi/bookstore-service.json"
    val specText = new String(Files.readAllBytes(Paths.get(specPath)), StandardCharsets.UTF_8)
    val api: OpenAPI = Json.parse(specText).flatMap(json => OpenAPICodec.openAPICodec.decode(json)) match {
      case Right(parsed) => parsed
      case Left(error)   => throw new IllegalArgumentException(s"Not an OpenAPI document: ${error.getMessage}")
    }
    ServiceMapping.endpoints(api).foreach { e =>
      val params = ServiceMapping.pathParams(e).map(p => s"${p.name}: ${p.typeRef.name}")
      val body   = ServiceMapping.bodyParam(e).map(p => s"${p.name}: ${p.typeRef.name}")
      println(s"${e.method.toUpperCase} ${e.path} -> ${e.operationId}(${(params ++ body).mkString(", ")}): ${ServiceMapping.successType(e).name}")
    }
  }
}
