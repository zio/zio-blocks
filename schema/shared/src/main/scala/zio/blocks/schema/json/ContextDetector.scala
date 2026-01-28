package zio.blocks.schema.json

import scala.collection.mutable

/**
 * Detects the interpolation context for each argument in a JSON string
 * interpolator.
 *
 * Analyzes the literal parts of a StringContext to determine whether each
 * interpolation occurs in key position, value position, or inside a string
 * literal.
 */
private[schema] object ContextDetector {

  /**
   * Analyzes StringContext parts and returns the context for each
   * interpolation.
   *
   * @param parts
   *   The literal string parts from StringContext (parts.length == args.length
   *   + 1)
   * @return
   *   A list of contexts, one for each interpolation (length == parts.length -
   *   1)
   */
  def detectContexts(parts: Seq[String]): Either[String, List[InterpolationContext]] =
    if (parts.isEmpty) {
      Right(Nil)
    } else if (parts.length == 1) {
      Right(Nil)
    } else {
      detectContextsImpl(parts)
    }

  private sealed trait ParseState
  private object ParseState {
    // In object, expecting a key (after { or ,)
    case object ExpectingKey extends ParseState
    // In object, after key, expecting colon
    case object ExpectingColon extends ParseState
    // Expecting a value (after : in object, or after [ or , in array)
    case object ExpectingValue extends ParseState
    // After a value, expecting comma or closing bracket
    case object AfterValue extends ParseState
    // Inside a quoted string literal
    case class InString(returnState: ParseState) extends ParseState
    // Top level, outside any container
    case object TopLevel extends ParseState
  }

  private sealed trait Container
  private object Container {
    case object Object extends Container
    case object Array  extends Container
  }

  private def detectContextsImpl(parts: Seq[String]): Either[String, List[InterpolationContext]] = {
    import ParseState._
    import Container._

    var state: ParseState = TopLevel
    val containerStack    = mutable.Stack[Container]()
    val contexts          = mutable.ListBuffer[InterpolationContext]()
    var partIndex         = 0

    // Process each part except the last one (which has no following interpolation)
    while (partIndex < parts.length - 1) {
      val part = parts(partIndex)
      var i    = 0

      // Process characters in the current part
      while (i < part.length) {
        val ch = part.charAt(i)

        state match {
          case InString(returnState) =>
            if (ch == '"') {
              state = returnState
              i += 1
            } else if (ch == '\\' && i + 1 < part.length) {
              // Skip escaped character
              i += 2
            } else {
              i += 1
            }

          case TopLevel =>
            if (ch == '{') {
              containerStack.push(Object)
              state = ExpectingKey
              i += 1
            } else if (ch == '[') {
              containerStack.push(Array)
              state = ExpectingValue
              i += 1
            } else if (ch == '"') {
              state = InString(TopLevel)
              i += 1
            } else if (isWhitespace(ch)) {
              i += 1
            } else {
              // Start of a value at top level (number, boolean, null, etc.)
              // Skip until we hit something that ends the value
              state = AfterValue
              i += 1
            }

          case ExpectingKey =>
            if (ch == '"') {
              state = InString(ExpectingColon)
              i += 1
            } else if (ch == '}') {
              // Empty object
              containerStack.pop()
              state = if (containerStack.isEmpty) TopLevel else AfterValue
              i += 1
            } else if (isWhitespace(ch)) {
              i += 1
            } else {
              // Unquoted key or interpolation - we'll determine at interpolation point
              i += 1
            }

          case ExpectingColon =>
            if (ch == ':') {
              state = ExpectingValue
              i += 1
            } else if (isWhitespace(ch)) {
              i += 1
            } else {
              // Unexpected character
              i += 1
            }

          case ExpectingValue =>
            if (ch == '"') {
              state = InString(AfterValue)
              i += 1
            } else if (ch == '{') {
              containerStack.push(Object)
              state = ExpectingKey
              i += 1
            } else if (ch == '[') {
              containerStack.push(Array)
              state = ExpectingValue
              i += 1
            } else if (ch == ']') {
              // Empty array
              containerStack.pop()
              state = if (containerStack.isEmpty) TopLevel else AfterValue
              i += 1
            } else if (isWhitespace(ch)) {
              i += 1
            } else {
              // Start of a literal value (number, true, false, null)
              state = AfterValue
              i += 1
            }

          case AfterValue =>
            if (ch == ',') {
              if (containerStack.nonEmpty) {
                containerStack.top match {
                  case Object => state = ExpectingKey
                  case Array  => state = ExpectingValue
                }
              }
              i += 1
            } else if (ch == '}') {
              containerStack.pop()
              state = if (containerStack.isEmpty) TopLevel else AfterValue
              i += 1
            } else if (ch == ']') {
              containerStack.pop()
              state = if (containerStack.isEmpty) TopLevel else AfterValue
              i += 1
            } else if (isWhitespace(ch)) {
              i += 1
            } else {
              // Continue consuming value characters
              i += 1
            }
        }
      }

      // At the end of this part, determine the context for the next interpolation
      val context = state match {
        case InString(_)  => InterpolationContext.InString
        case ExpectingKey =>
          // After the interpolation, we'll need a colon
          state = ExpectingColon
          InterpolationContext.Key
        case ExpectingValue | TopLevel =>
          state = AfterValue
          InterpolationContext.Value
        case ExpectingColon =>
          // This would be unusual - interpolation where we expect a colon
          // Treat as a value context (error will be caught by JSON parsing)
          state = AfterValue
          InterpolationContext.Value
        case AfterValue =>
          // Interpolation after a value - unusual but treat as value
          InterpolationContext.Value
      }
      contexts.addOne(context)
      partIndex += 1
    }
    new Right(contexts.toList)
  }

  private def isWhitespace(ch: Char): Boolean = ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r'
}
