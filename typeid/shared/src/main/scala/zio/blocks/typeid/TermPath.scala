package zio.blocks.typeid

/**
 * Represents a path to a term (value or object) in Scala.
 * Used for singleton types like `object.type` or literal types.
 */
final case class TermPath(segments: List[TermPath.Segment])

object TermPath {
  /**
   * A segment in a term path.
   */
  sealed trait Segment {
    def name: String
  }

  object Segment {
    /**
     * A package segment.
     */
    final case class Package(name: String) extends Segment

    /**
     * A term segment (object, val, def result, etc.).
     */
    final case class Term(name: String) extends Segment
  }
}
