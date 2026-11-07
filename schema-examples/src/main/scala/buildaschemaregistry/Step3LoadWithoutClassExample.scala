package buildaschemaregistry

import java.nio.file.{Files, Path}

import zio.blocks.schema._

/**
 * How to Build a Schema Registry with DynamicSchema — Load the Schema Without
 * the Class
 *
 * A producer registers version 1; a second registry on the same directory loads
 * it, prints its fields, and compares it with the schemas of both event
 * classes.
 *
 * Run with: cd schema-examples && sbt "runMain
 * buildaschemaregistry.Step3LoadWithoutClassExample"
 */
object Step3LoadWithoutClassExample {
  import SchemaRegistry.describe

  def main(args: Array[String]): Unit = {
    val root: Path               = Files.createTempDirectory("schema-registry")
    val registry: SchemaRegistry = new SchemaRegistry(root)
    registry.register("book-published", Schema[v1.BookPublished].toDynamicSchema)

    val consumerRegistry: SchemaRegistry = new SchemaRegistry(root)
    val storedV1: DynamicSchema          = consumerRegistry.load("book-published", 1)

    println(describe(storedV1))

    println(storedV1 == Schema[v1.BookPublished].toDynamicSchema)
    println(storedV1 == Schema[v2.BookPublished].toDynamicSchema)
  }
}
