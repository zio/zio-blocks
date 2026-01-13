package zio.blocks.typeid

/**
 * Represents the owner (enclosing scope) of a type or term definition.
 * An Owner consists of a path of segments from the root package to the immediate enclosing scope.
 */
final case class Owner(segments: List[Owner.Segment]) {

  /**
   * Returns the full qualified name by joining all segment names with dots.
   */
  def asString: String = segments.map(_.name).mkString(".")

  /**
   * Appends a segment to this owner, creating a child owner.
   */
  def /(segment: Owner.Segment): Owner = Owner(segments :+ segment)

  /**
   * Checks if this owner is empty (root owner).
   */
  def isEmpty: Boolean = segments.isEmpty
}

object Owner {

  /**
   * A segment in an owner's path hierarchy.
   */
  sealed trait Segment {
    def name: String
  }

  object Segment {
    /**
     * A package segment (e.g., "scala", "java", "zio").
     */
    final case class Package(name: String) extends Segment

    /**
     * A term/object segment (e.g., companion object).
     */
    final case class Term(name: String) extends Segment

    /**
     * A type segment (e.g., outer class or trait).
     */
    final case class Type(name: String) extends Segment
  }

  /**
   * The root owner with no segments.
   */
  val Root: Owner = Owner(Nil)

  // Common predefined owners for frequently used packages
  val scala: Owner = Root / Segment.Package("scala")
  val javaLang: Owner = Root / Segment.Package("java") / Segment.Package("lang")
  val javaUtil: Owner = Root / Segment.Package("java") / Segment.Package("util")
  val scalaCollection: Owner = Root / Segment.Package("scala") / Segment.Package("collection")
  val scalaCollectionImmutable: Owner = scalaCollection / Segment.Package("immutable")
}
