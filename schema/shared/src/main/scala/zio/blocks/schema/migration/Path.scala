package zio.blocks.schema.migration

import scala.collection.immutable.Vector

final case class Path(segments: Vector[Path.Segment]) {
  def /(field: String): Path = Path(segments :+ Path.Segment.Field(field))
  def /(index: Int): Path    = Path(segments :+ Path.Segment.Index(index))
}

object Path {
  val root: Path = Path(Vector.empty)

  sealed trait Segment extends Product with Serializable
  object Segment {
    final case class Field(name: String) extends Segment
    final case class Index(i: Int)       extends Segment
  }
}
