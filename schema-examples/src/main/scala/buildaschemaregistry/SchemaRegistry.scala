package buildaschemaregistry

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import zio.blocks.schema._
import zio.blocks.schema.avro.AvroFormat
import zio.blocks.schema.json._

/**
 * How to Build a Schema Registry with DynamicSchema — The Registry
 *
 * A directory-backed registry that stores each version of a subject's schema as
 * Avro bytes, with an indented JSON view next to it for people to read. Shared
 * by the step examples.
 */
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

object SchemaRegistry {

  /** Reads a stored schema's fields from `DynamicSchema#reflect`. */
  def describe(schema: DynamicSchema): String =
    schema.reflect.asRecord match {
      case Some(record) =>
        record.fields
          .map(field => s"${field.name}: ${field.value.typeId}")
          .mkString(s"${schema.typeId.name}(", ", ", ")")
      case None => schema.typeId.toString
    }
}
