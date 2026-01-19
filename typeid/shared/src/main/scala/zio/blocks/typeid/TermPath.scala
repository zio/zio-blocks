package zio.blocks.typeid

final case class TermPath(segments: List[TermPath.Segment])

object TermPath {
  sealed trait Segment { def name: String }

  final case class Package(name: String) extends Segment
  final case class Term(name: String)    extends Segment
}
