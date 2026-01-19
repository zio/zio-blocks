package zio.blocks.typeid

final case class Owner(segments: List[Owner.Segment]) {
  def asString: String = segments.map(_.name).mkString(".")
}

object Owner {
  sealed trait Segment { def name: String }

  final case class Package(name: String) extends Segment
  final case class Term(name: String)    extends Segment
  final case class Type(name: String)    extends Segment

  val Root: Owner = Owner(Nil)
}
