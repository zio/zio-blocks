//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-openapi:0.0.56
//> using options -experimental
//> using file BookstoreSpec.scala
// Run from this folder:  scala-cli run Step2ComponentSchemasExample.scala BookstoreSpec.scala
// Or from openapi-examples:  sbt "runMain generatescalatypesfromopenapi.Step2ComponentSchemasExample"

package generatescalatypesfromopenapi

import zio.blocks.openapi._
import zio.blocks.schema.json._

import BookstoreSpec.bookstoreJson

/**
 * Generate Scala Types from an OpenAPI Spec — Read the Component Schemas
 *
 * Reads every component schema as a typed `JsonSchema.Object` and prints its
 * type, properties and required properties, and the discriminator of `Payment`.
 */
object Step2ComponentSchemasExample {
  // Setup from "Parse the Spec": the parsed spec.
  def parseSpec(text: String): OpenAPI =
    Json.parse(text).flatMap(json => OpenAPICodec.openAPICodec.decode(json)) match {
      case Right(api)  => api
      case Left(error) => throw new IllegalArgumentException(s"Not an OpenAPI document: ${error.getMessage}")
    }

  val api: OpenAPI = parseSpec(bookstoreJson)

  // Read the Component Schemas
  final case class Component(name: String, schema: JsonSchema.Object, discriminator: Option[Discriminator])

  def componentsOf(api: OpenAPI): List[Component] =
    api.components.toList.flatMap(_.schemas.toList).map {
      case (name, ReferenceOr.Value(schemaObject)) =>
        schemaObject.toJsonSchema match {
          case Right(schema: JsonSchema.Object) => Component(name, schema, schemaObject.discriminator)
          case _                                => throw new IllegalArgumentException(s"$name: unsupported schema")
        }
      case (name, ReferenceOr.Ref(_)) =>
        throw new IllegalArgumentException(s"$name: a component that is only a $$ref is not supported")
    }

  val components: List[Component] = componentsOf(api)

  def main(args: Array[String]): Unit = {
    components.foreach { component =>
      val schema = component.schema
      println(
        s"${component.name}: type=${schema.`type`.map(_.toJson.print).getOrElse("-")}, " +
          s"properties=${schema.properties.map(_.toList.map(_._1).mkString("[", ", ", "]")).getOrElse("-")}, " +
          s"required=${schema.required.map(_.toList.sorted.mkString("[", ", ", "]")).getOrElse("-")}"
      )
    }
    println(components.find(_.name == "Payment").flatMap(_.discriminator))
  }
}
