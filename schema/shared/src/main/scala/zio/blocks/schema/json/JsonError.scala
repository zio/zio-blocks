package zio.blocks.schema.json

import zio.Chunk

sealed trait JsonError {
  def message: String
  def path: Chunk[JsonPathSegment]
  def position: Option[JsonPosition]

  def atPath(segment: JsonPathSegment): JsonError
  def atIndex(index: Int): JsonError = atPath(JsonPathSegment.Index(index))
  def atKey(key: String): JsonError = atPath(JsonPathSegment.Key(key))

  def withPosition(pos: JsonPosition): JsonError

  def pathString: String = path.map {
    case JsonPathSegment.Key(k)   => s".$k"
    case JsonPathSegment.Index(i) => s"[$i]"
  }.mkString

  override def toString: String = {
    val posStr = position.map(p => s" at line ${p.line}, column ${p.column}").getOrElse("")
    s"$message$posStr (path: $pathString)"
  }
}

object JsonError {
  final case class ParseError(
    message: String,
    path: Chunk[JsonPathSegment] = Chunk.empty,
    position: Option[JsonPosition] = None
  ) extends JsonError {
    def atPath(segment: JsonPathSegment): JsonError =
      copy(path = segment +: path)

    def withPosition(pos: JsonPosition): JsonError =
      copy(position = Some(pos))
  }

  final case class DecodeError(
    message: String,
    path: Chunk[JsonPathSegment] = Chunk.empty,
    position: Option[JsonPosition] = None
  ) extends JsonError {
    def atPath(segment: JsonPathSegment): JsonError =
      copy(path = segment +: path)

    def withPosition(pos: JsonPosition): JsonError =
      copy(position = Some(pos))
  }

  final case class TypeMismatch(
    expected: String,
    actual: String,
    path: Chunk[JsonPathSegment] = Chunk.empty,
    position: Option[JsonPosition] = None
  ) extends JsonError {
    def message: String = s"Expected $expected but found $actual"

    def atPath(segment: JsonPathSegment): JsonError =
      copy(path = segment +: path)

    def withPosition(pos: JsonPosition): JsonError =
      copy(position = Some(pos))
  }

  final case class MissingField(
    fieldName: String,
    path: Chunk[JsonPathSegment] = Chunk.empty,
    position: Option[JsonPosition] = None
  ) extends JsonError {
    def message: String = s"Missing required field: $fieldName"

    def atPath(segment: JsonPathSegment): JsonError =
      copy(path = segment +: path)

    def withPosition(pos: JsonPosition): JsonError =
      copy(position = Some(pos))
  }

  def parse(message: String): JsonError = ParseError(message)
  def decode(message: String): JsonError = DecodeError(message)
  def typeMismatch(expected: String, actual: String): JsonError = TypeMismatch(expected, actual)
  def missingField(name: String): JsonError = MissingField(name)
}

sealed trait JsonPathSegment

object JsonPathSegment {
  final case class Key(name: String) extends JsonPathSegment
  final case class Index(index: Int) extends JsonPathSegment
}

final case class JsonPosition(
  line: Int,
  column: Int,
  offset: Long
)
