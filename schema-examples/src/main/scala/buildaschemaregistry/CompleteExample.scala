package buildaschemaregistry

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import zio.blocks.schema._
import zio.blocks.schema.avro.AvroFormat
import zio.blocks.schema.binding._
import zio.blocks.schema.json._

/**
 * How to Build a Schema Registry with DynamicSchema — The Complete Example
 *
 * Runs the whole life of a subject in one program: the producer registers
 * version 1, a gateway loads it without the class and checks events, a typed
 * consumer rebinds it, and the producer's next release registers version 2,
 * which the registry compares with version 1 before the consumer upgrades.
 *
 * Run with: cd schema-examples && sbt "runMain
 * buildaschemaregistry.CompleteExample"
 */
object CompleteExample {

  // The events: two versions of one class, in objects named v1 and v2

  object v1 {
    final case class BookPublished(isbn: String, title: String, price: BigDecimal)

    object BookPublished {
      implicit val schema: Schema[BookPublished] = Schema.derived
    }
  }

  object v2 {
    final case class BookPublished(
      isbn: String,
      title: String,
      price: BigDecimal,
      authors: List[String],
      edition: Option[Int]
    )

    object BookPublished {
      implicit val schema: Schema[BookPublished] = Schema.derived
    }
  }

  // The registry

  final class SchemaRegistry(root: Path) {
    private val codec = Schema[DynamicSchema].derive(AvroFormat)
    private val cache = scala.collection.mutable.Map.empty[(String, Int), DynamicSchema]

    def versions(subject: String): List[Int] = {
      val names = Option(root.resolve(subject).toFile.list()).fold(List.empty[String])(_.toList)
      names.collect { case name if name.matches("v[0-9]+\\.avro") => name.drop(1).dropRight(5).toInt }.sorted
    }

    def register(subject: String, schema: DynamicSchema): Int =
      versions(subject).find(version => load(subject, version) == schema) match {
        case Some(existing) => existing
        case None           =>
          val version = versions(subject).lastOption.fold(1)(_ + 1)
          val dir     = Files.createDirectories(root.resolve(subject))
          Files.write(dir.resolve(s"v$version.avro"), codec.encode(schema))
          Files.write(dir.resolve(s"v$version.json"), jsonView(schema).getBytes(UTF_8))
          cache((subject, version)) = schema
          version
      }

    def load(subject: String, version: Int): DynamicSchema =
      cache.getOrElseUpdate((subject, version), read(subject, version))

    def latest(subject: String): Option[(Int, DynamicSchema)] =
      versions(subject).lastOption.map(version => (version, load(subject, version)))

    private def read(subject: String, version: Int): DynamicSchema = {
      val bytes  = Files.readAllBytes(root.resolve(subject).resolve(s"v$version.avro"))
      val schema = codec.decode(bytes).fold(error => throw error, identity)
      if (schema.reflect.isDynamic)
        throw new IllegalStateException(s"$subject v$version was read back as an accept-anything schema")
      schema
    }

    private def jsonView(schema: DynamicSchema): String =
      Json.fromDynamicValue(DynamicSchema.toDynamicValue(schema)).print(WriterConfig.withIndentionStep(2))
  }

  def describe(schema: DynamicSchema): String =
    schema.reflect.asRecord match {
      case Some(record) =>
        record.fields
          .map(field => s"${field.name}: ${field.value.typeId}")
          .mkString(s"${schema.typeId.name}(", ", ", ")")
      case None => schema.typeId.toString
    }

  // The comparison

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
    val subject = "book-published"
    val root    = Files.createTempDirectory("schema-registry")

    // 1. The producer registers version 1, twice, as it does on every restart
    val producerRegistry = new SchemaRegistry(root)
    println(producerRegistry.register(subject, Schema[v1.BookPublished].toDynamicSchema))
    println(producerRegistry.register(subject, Schema[v1.BookPublished].toDynamicSchema))
    println(producerRegistry.versions(subject))
    println(root.resolve(subject).toFile.list().toList.sorted)

    // 2. A gateway without the class loads version 1 in a fresh registry
    val gatewayRegistry = new SchemaRegistry(root)
    val storedV1        = gatewayRegistry.load(subject, 1)
    println(describe(storedV1))
    println(storedV1 == Schema[v1.BookPublished].toDynamicSchema)

    // 3. The gateway checks one good event and one bad one
    val eventCodec = Schema[DynamicValue].derive(AvroFormat)
    val zionomicon = v1.BookPublished("978-1-00-000001-1", "Zionomicon", BigDecimal("49.99"))
    val sent       = eventCodec.encode(Schema[v1.BookPublished].toDynamicValue(zionomicon))
    val received   = eventCodec.decode(sent).fold(error => throw error, identity)
    println(storedV1.check(received))

    val textPrice  = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": "49.99"}""")
    val noTitle    = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "price": 49.99}""")
    val wholePrice = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": 50}""")
    println(storedV1.check(textPrice.toDynamicValue))
    println(storedV1.check(wholePrice.toDynamicValue))

    // 4. A typed consumer rebinds version 1 and decodes JSON events
    val v1Resolver = BindingResolver.empty.bind(Binding.of[v1.BookPublished]) ++ BindingResolver.defaults
    val typedV1    = storedV1.rebind[v1.BookPublished](v1Resolver)
    println(typedV1.jsonCodec.decode(wholePrice.print))
    println(typedV1.jsonCodec.decode(textPrice.print))

    // 5. The producer's next release registers version 2
    println(producerRegistry.register(subject, Schema[v2.BookPublished].toDynamicSchema))
    println(producerRegistry.versions(subject))

    // 6. The registry compares version 1 with version 2
    val changes = compare(producerRegistry.load(subject, 1), producerRegistry.load(subject, 2))
    changes.foreach(println)
    println(s"breaks old data: ${changes.exists(_.breaksOldData)}")

    // 7. A version 2 consumer rebinds with Option bindings and decodes new and old events
    val v2Resolver =
      BindingResolver.empty
        .bind(Binding.of[v2.BookPublished])
        .bind(Binding.of[Option[Int]])
        .bind(Binding.Record.someInt)
        .bind(Binding.Record.none) ++ BindingResolver.defaults
    val typedV2 = gatewayRegistry.load(subject, 2).rebind[v2.BookPublished](v2Resolver)

    val fpInScala =
      """{"isbn": "978-1-00-000002-8", "title": "Functional Programming in Scala", "price": 44.99,
        | "authors": ["Michael Pilquist", "Rúnar Bjarnason", "Paul Chiusano"], "edition": 2}""".stripMargin
    val zionomiconV1 =
      """{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": 49.99}"""

    println(typedV2.jsonCodec.decode(fpInScala))
    println(typedV2.jsonCodec.decode(zionomiconV1))
  }
}
