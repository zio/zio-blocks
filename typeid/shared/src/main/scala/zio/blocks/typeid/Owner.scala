package zio.blocks.typeid

final case class Owner(segments: List[Owner.Segment]) {
  def /(segment: Owner.Segment): Owner = Owner(segments :+ segment)

  def parent: Option[Owner] =
    if (segments.isEmpty) None
    else Some(Owner(segments.init))

  def isRoot: Boolean = segments.isEmpty

  def asString: String = segments.map(_.show).mkString(".")

  def isPrefixOf(other: Owner): Boolean =
    other.segments.startsWith(segments)

  override def toString: String = asString
}

object Owner {
  sealed trait Segment {
    def name: String
    def show: String = name
  }

  final case class Package(name: String) extends Segment
  final case class Term(name: String)    extends Segment
  final case class Type(name: String)    extends Segment
  final case class Local(index: Int)     extends Segment {
    def name: String = s"<local$index>"
  }

  val Root: Owner = Owner(Nil)

  def pkg(name: String): Owner    = Owner(List(Package(name)))
  def pkgs(names: String*): Owner = Owner(names.map(Package(_)).toList)

  // Helper for tests/macros
  def parse(s: String): Owner =
    if (s.isEmpty) Root
    else Owner(s.split('.').map(Package(_)).toList)
}
