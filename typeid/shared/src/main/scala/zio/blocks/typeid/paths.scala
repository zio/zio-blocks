package zio.blocks.typeid

/**
 * Represents the lexical owner of a type definition.
 */
final case class Owner(segments: List[Owner.Segment]) {
  def /(segment: Owner.Segment): Owner = Owner(segments :+ segment)

  def parent: Option[Owner] =
    if (segments.isEmpty) None
    else Some(Owner(segments.init))

  def isRoot: Boolean = segments.isEmpty

  def asString: String = segments.map(_.show).mkString(".")

  def isPrefixOf(other: Owner): Boolean =
    other.segments.startsWith(segments)

  def isScala: Boolean = segments match {
    case List(Owner.Package("scala")) => true
    case _                            => false
  }
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

  def parse(s: String): Owner =
    if (s.isEmpty) Root
    else Owner(s.split('.').map(Package(_)).toList)
}

/**
 * Represents paths to terms, used for singleton and path-dependent types.
 */
final case class TermPath(segments: List[TermPath.Segment]) {
  def /(segment: TermPath.Segment): TermPath =
    TermPath(segments :+ segment)

  def isStable: Boolean = segments.forall(_.isStable)

  def asString: String = segments.map(_.name).mkString(".")
}

object TermPath {
  sealed trait Segment {
    def name: String
    def isStable: Boolean
  }

  final case class Package(name: String) extends Segment {
    def isStable: Boolean = true
  }

  final case class Module(name: String) extends Segment {
    def isStable: Boolean = true
  }

  final case class Val(name: String) extends Segment {
    def isStable: Boolean = true
  }

  final case class LazyVal(name: String) extends Segment {
    def isStable: Boolean = true
  }

  final case class Var(name: String) extends Segment {
    def isStable: Boolean = false
  }

  final case class Def(name: String) extends Segment {
    def isStable: Boolean = false
  }

  final case class This(ownerName: String) extends Segment {
    def name: String      = s"$ownerName.this"
    def isStable: Boolean = true
  }

  final case class Super(ownerName: String, mixinName: Option[String]) extends Segment {
    def name: String      = mixinName.fold(s"$ownerName.super")(m => s"$ownerName.super[$m]")
    def isStable: Boolean = true
  }

  val empty: TermPath = TermPath(Nil)
}
