package zio.blocks.typeid

/**
 * Represents a path to a term (value) for singleton types.
 *
 * Used to represent types like `x.type` where `x` is a singleton value. The
 * path captures the full qualifying path to the singleton.
 *
 * ==Example==
 * For `scala.None.type`:
 * {{{
 * TermPath(List(
 *   TermPath.Package("scala"),
 *   TermPath.Term("None")
 * ))
 * }}}
 *
 * @param segments
 *   The path segments from root to the term
 */
final case class TermPath(segments: List[TermPath.Segment]) {

  /**
   * Returns the path as a dot-separated string.
   */
  def show: String = segments.map(_.name).mkString(".")

  /**
   * Appends a package segment.
   */
  def pkg(name: String): TermPath = TermPath(segments :+ TermPath.Package(name))

  /**
   * Appends a term segment.
   */
  def term(name: String): TermPath = TermPath(segments :+ TermPath.Term(name))

  /**
   * Returns true if this path is empty.
   */
  def isEmpty: Boolean = segments.isEmpty

  /**
   * Returns the last segment name, if any.
   */
  def lastName: Option[String] = segments.lastOption.map(_.name)

  override def toString: String = s"TermPath($show)"
}

object TermPath {

  /**
   * A segment in the term path.
   */
  sealed trait Segment extends Product with Serializable {
    def name: String
  }

  /**
   * A package segment in the path.
   */
  final case class Package(name: String) extends Segment

  /**
   * A term (object/val/def) segment in the path.
   */
  final case class Term(name: String) extends Segment

  /**
   * An empty term path (represents `this` or local scope).
   */
  val Empty: TermPath = TermPath(Nil)

  /**
   * Creates a TermPath from a dot-separated string.
   */
  def fromString(path: String): TermPath =
    if (path.isEmpty) Empty
    else TermPath(path.split('.').toList.map(Term(_)))

  /**
   * Creates a TermPath from package and term names.
   */
  def apply(packages: List[String], term: String): TermPath =
    TermPath(packages.map(Package(_)) :+ Term(term))
}
