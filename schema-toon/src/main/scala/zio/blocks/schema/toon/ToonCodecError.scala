package zio.blocks.schema.toon

import zio.blocks.schema.DynamicOptic

/**
 * Error type for TOON codec operations.
 *
 * Provides structured error information including position in the input, making
 * it easier to diagnose parsing issues.
 *
 * @param message
 *   The error message describing what went wrong
 * @param line
 *   The line number where the error occurred (1-indexed)
 * @param column
 *   The column number where the error occurred (1-indexed), if available
 * @param path
 *   The path to the field that failed, if available
 */
class ToonCodecError(
  val message: String,
  val line: Int,
  val column: Int = -1,
  val path: List[DynamicOptic.Node] = Nil
) extends RuntimeException(ToonCodecError.formatMessage(message, line, column, path)) {

  /**
   * Returns a copy of this error with an additional path segment prepended.
   */
  def withPath(node: DynamicOptic.Node): ToonCodecError =
    new ToonCodecError(message, line, column, node :: path)
}

object ToonCodecError {

  /**
   * Creates a ToonCodecError from a ToonReader's current position.
   */
  def at(reader: ToonReader, message: String): ToonCodecError =
    new ToonCodecError(message, reader.getLine)

  /**
   * Creates a ToonCodecError with just a message and line number.
   */
  def atLine(line: Int, message: String): ToonCodecError =
    new ToonCodecError(message, line)

  private def formatMessage(
    message: String,
    line: Int,
    column: Int,
    path: List[DynamicOptic.Node]
  ): String = {
    val posInfo  = if (column >= 0) s"line $line, column $column" else s"line $line"
    val pathInfo = if (path.nonEmpty) {
      val pathStr = path.reverse.map {
        case DynamicOptic.Node.Field(name)  => s".$name"
        case DynamicOptic.Node.AtIndex(idx) => s"[$idx]"
        case DynamicOptic.Node.Case(name)   => s"/$name"
        case other                          => s"/$other"
      }.mkString
      s" at path $pathStr"
    } else ""

    s"$message ($posInfo$pathInfo)"
  }
}
