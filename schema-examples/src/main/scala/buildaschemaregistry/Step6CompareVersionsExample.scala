package buildaschemaregistry

import java.nio.file.{Files, Path}

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.json._

/**
 * How to Build a Schema Registry with DynamicSchema — Register Version 2 and
 * Compare It With Version 1
 *
 * Registers version 2, compares it with version 1, rebinds it with the `Option`
 * bindings, and decodes a new event and old ones.
 *
 * Run with: cd schema-examples && sbt "runMain
 * buildaschemaregistry.Step6CompareVersionsExample"
 */
object Step6CompareVersionsExample {
  final case class Change(description: String, breaksOldData: Boolean)

  def compare(older: DynamicSchema, newer: DynamicSchema): List[Change] = {
    def fields(schema: DynamicSchema): Map[String, Reflect.Unbound[_]] =
      schema.reflect.asRecord.fold(Map.empty[String, Reflect.Unbound[_]])(_.fields.map(f => f.name -> f.value).toMap)

    val before = fields(older)
    val after  = fields(newer)

    val removed = (before.keySet -- after.keySet).toList.sorted.map { name =>
      Change(s"removed $name", breaksOldData = false)
    }
    val added = (after.keySet -- before.keySet).toList.sorted.map { name =>
      val field  = after(name)
      val filled = field.isOption || field.isCollection
      Change(s"added $name: ${field.typeId}", breaksOldData = !filled)
    }
    val changed = (before.keySet intersect after.keySet).toList.sorted.collect {
      case name if before(name).typeId != after(name).typeId =>
        Change(s"changed $name: ${before(name).typeId} -> ${after(name).typeId}", breaksOldData = true)
    }
    removed ++ added ++ changed
  }

  def main(args: Array[String]): Unit = {
    val root: Path               = Files.createTempDirectory("schema-registry")
    val registry: SchemaRegistry = new SchemaRegistry(root)
    registry.register("book-published", Schema[v1.BookPublished].toDynamicSchema)

    val consumerRegistry: SchemaRegistry = new SchemaRegistry(root)

    println(registry.register("book-published", Schema[v2.BookPublished].toDynamicSchema))
    println(registry.versions("book-published"))

    val changes = compare(registry.load("book-published", 1), registry.load("book-published", 2))

    changes.foreach(println)
    println(changes.exists(_.breaksOldData))

    val v2Resolver: BindingResolver =
      BindingResolver.empty
        .bind(Binding.of[v2.BookPublished])
        .bind(Binding.of[Option[Int]])
        .bind(Binding.Record.someInt)
        .bind(Binding.Record.none) ++ BindingResolver.defaults

    val typedV2: Schema[v2.BookPublished] =
      consumerRegistry.load("book-published", 2).rebind[v2.BookPublished](v2Resolver)

    val noTitle    = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "price": 49.99}""")
    val wholePrice = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": 50}""")

    val fpInScala =
      """{"isbn": "978-1-00-000002-8", "title": "Functional Programming in Scala", "price": 44.99,
        | "authors": ["Michael Pilquist", "Rúnar Bjarnason", "Paul Chiusano"], "edition": 2}""".stripMargin

    println(typedV2.jsonCodec.decode(fpInScala))
    println(typedV2.jsonCodec.decode(noTitle.print))
    println(typedV2.jsonCodec.decode(wholePrice.print))
  }
}
