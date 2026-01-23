package zio.blocks.typeid

final case class TermPath(segments: List[TermPath.Segment]) {
  def /(segment: TermPath.Segment): TermPath =
    TermPath(segments :+ segment)

  def isStable: Boolean = segments.forall(_.isStable)

  def asString: String = segments.map(_.name).mkString(".")

  override def toString: String = asString
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
