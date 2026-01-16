package zio.schema.migration

import zio.schema._

/**
 * Represents a path to a field in a schema, serializable and reconstructable
 */
sealed trait FieldPath {
  def serialize: String
  def ::(segment: String): FieldPath = FieldPath.Nested(this, segment)
}

object FieldPath {

  /** Root field path pointing to a top-level field */
  case class Root(name: String) extends FieldPath {
    def serialize: String = name
  }

  /** Nested field path for accessing fields within nested structures */
  case class Nested(parent: FieldPath, child: String) extends FieldPath {
    def serialize: String = s"${parent.serialize}.$child"
  }

  /** Parse a serialized field path string */
  def parse(path: String): Either[String, FieldPath] = {
    val segments = path.split('.')
    if (segments.isEmpty) {
      Left("Empty path")
    } else {
      val root   = Root(segments.head)
      val nested = segments.tail.foldLeft[FieldPath](root) { (acc, segment) =>
        FieldPath.Nested(acc, segment)
      }
      Right(nested)
    }
  }

  /** Create a root field path */
  def apply(name: String): FieldPath = Root(name)

  /** Schema for serializing FieldPath */
  implicit val schema: Schema[FieldPath] = Schema[String].transformOrFail(
    str => parse(str),
    path => Right(path.serialize)
  )
}
