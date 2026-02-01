package zio.blocks.typeid

/**
 * Represents a path to a term value, used for singleton types.
 *
 * For example, for a singleton type `MyObject.Inner.value.type`:
 * {{{
 * TermPath(List(
 *   TermPath.Package("com"),
 *   TermPath.Package("example"),
 *   TermPath.Term("MyObject"),
 *   TermPath.Term("Inner"),
 *   TermPath.Term("value")
 * ))
 * }}}
 */
final case class TermPath(segments: List[TermPath.Segment]) {

  /**
   * Returns the path as a dot-separated string.
   */
  def asString: String = segments.map(_.name).mkString(".")

  /**
   * Appends a term segment to this path.
   */
  def /(name: String): TermPath = TermPath(segments :+ TermPath.Term(name))

  /**
   * Returns true if this is an empty path.
   */
  def isEmpty: Boolean = segments.isEmpty
}

object TermPath {

  /**
   * A segment in a term path.
   */
  sealed trait Segment {
    def name: String
  }

  /**
   * A package segment.
   */
  final case class Package(name: String) extends Segment

  /**
   * A term segment (object, value, etc.).
   */
  final case class Term(name: String) extends Segment

  /**
   * An empty term path.
   */
  val Empty: TermPath = TermPath(Nil)

  /**
   * Creates a TermPath from an Owner and a term name.
   */
  def fromOwner(owner: Owner, termName: String): TermPath = {
    val ownerSegments = owner.segments.map {
      case Owner.Package(name) => Package(name)
      case Owner.Term(name)    => Term(name)
      case Owner.Type(name)    => Term(name) // Types are accessed like terms in paths
    }
    TermPath(ownerSegments :+ Term(termName))
  }
}
