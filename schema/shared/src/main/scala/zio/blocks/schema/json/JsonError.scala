package zio.blocks.schema.json

final case class JsonError(message: String, path: Vector[String] = Vector.empty) {
  def prependPath(segment: String): JsonError = copy(path = segment +: path)
  def prependField(field: String): JsonError  = prependPath(s".$field")
  def prependIndex(index: Int): JsonError     = prependPath(s"[$index]")

  override def toString: String =
    if (path.isEmpty) message
    else s"$message at ${path.mkString}"
}

object JsonError {
  def typeMismatch(expected: String, actual: String): JsonError =
    JsonError(s"Type mismatch: expected $expected, got $actual")

  def missingField(field: String): JsonError =
    JsonError(s"Missing field: $field")

  def indexOutOfBounds(index: Int, length: Int): JsonError =
    JsonError(s"Index $index out of bounds (length: $length)")

  def keyNotFound(key: String): JsonError =
    JsonError(s"Key not found: $key")

  def fieldExists(field: String): JsonError =
    JsonError(s"Field already exists: $field")

  def invalidRange(start: Int, end: Int, length: Int): JsonError =
    JsonError(s"Invalid range [$start, $end) for length $length")

  def stringIndexOutOfBounds(index: Int, length: Int): JsonError =
    JsonError(s"String index $index out of bounds (length: $length)")

  def unsupportedOperation(message: String): JsonError =
    JsonError(s"Unsupported operation: $message")

  def incompatibleDynamicPatch(message: String): JsonError =
    JsonError(s"Incompatible DynamicPatch: $message")
}
