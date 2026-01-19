package zio.blocks.schema.internal

/**
 * Generates human-readable compile errors with position tracking and helpful suggestions.
 */
object ErrorReporter {

  /**
   * A compile error with position information and optional suggestion.
   */
  case class CompileError(
    message: String,
    position: Int,
    snippet: String,
    suggestion: Option[String] = None
  )

  /**
   * Format a ParseError into a CompileError with snippet and suggestions.
   */
  def formatError(input: String, error: ParseError): CompileError = {
    val snippet    = createSnippet(input, error.position)
    val suggestion = suggestFix(input, error.message)
    CompileError(error.message, error.position, snippet, suggestion)
  }

  /**
   * Create a code snippet showing the error position with a caret pointer.
   */
  private def createSnippet(input: String, pos: Int): String = {
    // +2 accounts for 'p"' prefix in the displayed snippet
    val pointer = " " * (pos + 2) + "^"
    s"""p"$input"
       |$pointer""".stripMargin
  }

  /**
   * Suggest fixes for common mistakes based on the error message.
   */
  private def suggestFix(input: String, msg: String): Option[String] = {
    val lowerMsg = msg.toLowerCase
    if (lowerMsg.contains("expected ]") || lowerMsg.contains("expected ']'")) {
      Some("Did you mean [a:b] for range or [a,b,c] for multiple indices?")
    } else if (lowerMsg.contains("expected }") || lowerMsg.contains("expected '}'")) {
      Some("Map keys use braces: {\"key\"} for strings, {42} for ints, {'c'} for chars")
    } else if (lowerMsg.contains("expected >") || lowerMsg.contains("expected '>'")) {
      Some("Variant cases use angle brackets: <CaseName>")
    } else if (lowerMsg.contains("negative") && input.contains("[")) {
      Some("Array indices must be non-negative. Use {-N} for negative map keys.")
    } else if (lowerMsg.contains("start") && lowerMsg.contains("end")) {
      Some("Range start must be less than end. Ranges are exclusive: [0:5] → Seq(0,1,2,3,4)")
    } else if (lowerMsg.contains("overflow") || lowerMsg.contains("maxvalue")) {
      Some(s"Value exceeds Int.MaxValue (${Int.MaxValue})")
    } else if (lowerMsg.contains("range too large")) {
      Some("Consider using a smaller range or accessing elements individually")
    } else if (lowerMsg.contains("whitespace")) {
      Some("Whitespace is only allowed inside [], {}, and <>. Not around dots or between segments.")
    } else if (lowerMsg.contains("unterminated string")) {
      Some("String literals must be closed with a matching quote: \"value\"")
    } else if (lowerMsg.contains("invalid escape")) {
      Some("Valid escape sequences: \\n, \\t, \\r, \\\\, \\\", \\'")
    } else {
      None
    }
  }

  /**
   * Format a CompileError into a single error message string.
   */
  def toErrorMessage(error: CompileError): String = {
    val base = s"${error.message}\n${error.snippet}"
    error.suggestion.fold(base)(s => s"$base\n\nHint: $s")
  }
}
