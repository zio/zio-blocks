package zio.blocks.schema.yaml

import zio.blocks.chunk.Chunk

sealed trait Yaml {
  def print: String       = YamlWriter.write(this, YamlOptions.default)
  def printPretty: String = YamlWriter.write(this, YamlOptions.pretty)
}

object Yaml {
  final case class Mapping(entries: Chunk[(Yaml, Yaml)])             extends Yaml
  final case class Sequence(elements: Chunk[Yaml])                   extends Yaml
  final case class Scalar(value: String, tag: Option[String] = None) extends Yaml
  case object NullValue                                              extends Yaml

  object Mapping {
    val empty: Mapping = Mapping(Chunk.empty)

    def apply(entries: (Yaml, Yaml)*): Mapping =
      new Mapping(Chunk.from(entries))

    def fromStringKeys(entries: (String, Yaml)*): Mapping =
      new Mapping(Chunk.from(entries.map { case (k, v) => (Scalar(k): Yaml, v) }))
  }

  object Sequence {
    val empty: Sequence = Sequence(Chunk.empty)

    def apply(elements: Yaml*): Sequence =
      new Sequence(Chunk.from(elements))
  }
}
