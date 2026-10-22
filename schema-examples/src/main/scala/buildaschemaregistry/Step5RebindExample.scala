package buildaschemaregistry

import java.nio.file.{Files, Path}

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.json._

/**
 * How to Build a Schema Registry with DynamicSchema — Rebind to a Scala Type
 *
 * Rebinds version 1 to `v1.BookPublished`, decodes JSON events with the rebound
 * schema, and shows the `RebindException` for a type the resolver doesn't know.
 *
 * Run with: cd schema-examples && sbt "runMain
 * buildaschemaregistry.Step5RebindExample"
 */
object Step5RebindExample {
  def main(args: Array[String]): Unit = {
    val root: Path               = Files.createTempDirectory("schema-registry")
    val registry: SchemaRegistry = new SchemaRegistry(root)
    registry.register("book-published", Schema[v1.BookPublished].toDynamicSchema)

    val consumerRegistry: SchemaRegistry = new SchemaRegistry(root)
    val storedV1: DynamicSchema          = consumerRegistry.load("book-published", 1)

    val textPrice  = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": "49.99"}""")
    val wholePrice = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": 50}""")

    val v1Resolver: BindingResolver =
      BindingResolver.empty.bind(Binding.of[v1.BookPublished]) ++ BindingResolver.defaults

    val typedV1: Schema[v1.BookPublished] = storedV1.rebind[v1.BookPublished](v1Resolver)

    println(typedV1.jsonCodec.decode(wholePrice.print))
    println(typedV1.jsonCodec.decode(textPrice.print))

    val renamedResolver: BindingResolver =
      BindingResolver.empty.bind(Binding.of[v2.BookPublished]) ++ BindingResolver.defaults

    try storedV1.rebind[v2.BookPublished](renamedResolver)
    catch { case e: RebindException => println(e.getMessage) }
  }
}
