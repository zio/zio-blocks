package buildaschemaregistry

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import zio.blocks.schema._

/**
 * How to Build a Schema Registry with DynamicSchema — Register Version 1
 *
 * Registers version 1 twice, as a producer does each time it restarts, lists
 * the stored files, and prints the start of the JSON view and the sizes of both
 * files.
 *
 * Run with: cd schema-examples && sbt "runMain
 * buildaschemaregistry.Step2RegisterExample"
 */
object Step2RegisterExample {
  def main(args: Array[String]): Unit = {
    val root: Path               = Files.createTempDirectory("schema-registry")
    val registry: SchemaRegistry = new SchemaRegistry(root)

    println(registry.register("book-published", Schema[v1.BookPublished].toDynamicSchema))
    println(registry.register("book-published", Schema[v1.BookPublished].toDynamicSchema))
    println(registry.versions("book-published"))
    println(root.resolve("book-published").toFile.list().toList.sorted)

    val v1Dir: Path  = root.resolve("book-published")
    val view: String = new String(Files.readAllBytes(v1Dir.resolve("v1.json")), UTF_8)

    println(view.linesIterator.take(12).mkString("\n"))
    println(
      s"v1.json: ${Files.size(v1Dir.resolve("v1.json"))} bytes, v1.avro: ${Files.size(v1Dir.resolve("v1.avro"))} bytes"
    )
  }
}
