package zio.blocks.smithy

/**
 * Represents a reference to a Smithy shape (either absolute or member).
 *
 * This sealed trait provides a common type for both ShapeId and ShapeId.Member,
 * enabling cross-Scala-version compatibility without using Scala 3 union types.
 */
sealed trait ShapeRef

/**
 * Represents an absolute reference to a Smithy shape.
 *
 * A ShapeId consists of a namespace and a name. It can optionally reference a
 * specific member of a shape via the Member subtype.
 *
 * @param namespace
 *   the namespace part (e.g., "com.example")
 * @param name
 *   the shape name (e.g., "MyShape")
 */
final case class ShapeId(namespace: String, name: String) extends ShapeRef {
  override def toString: String = s"$namespace#$name"
}

object ShapeId {

  /**
   * Represents a reference to a specific member within a shape.
   *
   * @param shape
   *   the shape containing this member
   * @param memberName
   *   the name of the member within the shape
   */
  final case class Member(shape: ShapeId, memberName: String) extends ShapeRef {
    override def toString: String = shape.toString + "$" + memberName
  }

  /**
   * Parses a string into either a ShapeId or a Member.
   *
   * Expected formats:
   *   - "namespace#name" → ShapeId
   *   - "namespace#name$$memberName" → Member
   *
   * @param s
   *   the string to parse
   * @return
   *   Right(ShapeId) or Right(Member) on success, Left(error) on failure
   */
  def parse(s: String): Either[String, ShapeRef] =
    if (s.isEmpty) {
      Left("ShapeId cannot be empty")
    } else if (!s.contains("#")) {
      Left(s"ShapeId must contain '#' separator, got: $s")
    } else {
      val parts = s.split('#')
      if (parts.length != 2) {
        Left(s"ShapeId must have exactly one '#' separator, got: $s")
      } else {
        val namespace = parts(0)
        val rest      = parts(1)

        if (namespace.isEmpty) {
          Left("ShapeId namespace cannot be empty")
        } else if (rest.isEmpty) {
          Left("ShapeId name cannot be empty")
        } else if (rest.contains("$")) {
          // Member reference
          val memberParts = rest.split('$')
          if (memberParts.length != 2) {
            Left("Member reference must have exactly one separator, got: " + s)
          } else {
            val name       = memberParts(0)
            val memberName = memberParts(1)
            if (name.isEmpty) {
              Left("ShapeId name cannot be empty")
            } else if (memberName.isEmpty) {
              Left("Member name cannot be empty")
            } else {
              Right(Member(ShapeId(namespace, name), memberName))
            }
          }
        } else {
          // Regular shape reference
          Right(ShapeId(namespace, rest))
        }
      }
    }
}
