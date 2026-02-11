package zio.blocks.schema.json

import zio.blocks.chunk.ChunkBuilder
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
  def detectContexts(parts: Seq[String]): Either[String, Seq[InterpolationContext]] =
    if (parts.isEmpty || parts.tail.isEmpty) new Right(Nil)
    else detectContextsImpl(parts)

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

    case object Array extends Container
  }

  private[this] def detectContextsImpl(parts: Seq[String]): Either[String, Seq[InterpolationContext]] = {
    import ParseState._
    import Container._

    var state: ParseState = TopLevel
    val containerStack    = mutable.Stack[Container]()
    val contexts          = ChunkBuilder.make[InterpolationContext]()
    val it                = parts.iterator
    var part: String      = null
    while ({
      it.hasNext
      part = it.next()
      it.hasNext // Process each part except the last one (which has no following interpolation)
    }) {
      var idx = 0
      // Process characters in the current part
      while (idx < part.length) {
        val ch = part.charAt(idx)
        idx += 1
        state match {
          case is: InString =>
            if (ch == '"') state = is.returnState
            else if (ch == '\\' && idx < part.length) idx += 1 // Skip escaped character
          case _: TopLevel.type =>
            if (ch == '{') {
              containerStack.push(Object)
              state = ExpectingKey
            } else if (ch == '[') {
              containerStack.push(Array)
              state = ExpectingValue
            } else if (ch == '"') {
              state = new InString(TopLevel)
            } else if (!isWhitespace(ch)) {
              // Start of a value at top level (number, boolean, null, etc.)
              // Skip until we hit something that ends the value
              state = AfterValue
            }
          case _: ExpectingKey.type =>
            if (ch == '"') state = new InString(ExpectingColon)
            else if (ch == '}') {
              // Empty object
              containerStack.pop()
              state = if (containerStack.isEmpty) TopLevel else AfterValue
            }
          case _: ExpectingColon.type =>
            if (ch == ':') state = ExpectingValue
          case _: ExpectingValue.type =>
            if (ch == '"') state = new InString(AfterValue)
            else if (ch == '{') {
              containerStack.push(Object)
              state = ExpectingKey
            } else if (ch == '[') {
              containerStack.push(Array)
              state = ExpectingValue
            } else if (ch == ']') {
              // Empty array
              containerStack.pop()
              state = if (containerStack.isEmpty) TopLevel else AfterValue
            } else if (!isWhitespace(ch)) state = AfterValue // Start of a literal value (number, true, false, null)
          case _ =>
            if (ch == ',') {
              if (containerStack.nonEmpty) {
                containerStack.top match {
                  case Object => state = ExpectingKey
                  case Array  => state = ExpectingValue
                }
              }
            } else if (ch == '}' || ch == ']') {
              containerStack.pop()
              state = if (containerStack.isEmpty) TopLevel else AfterValue
            }
        }
      }
      // At the end of this part, determine the context for the next interpolation
      val context = state match {
        case _: InString          => InterpolationContext.InString
        case _: ExpectingKey.type =>
          // After the interpolation, we'll need a colon
          state = ExpectingColon
          InterpolationContext.Key
        case _: ExpectingColon.type =>
          // This would be unusual - interpolation where we expect a colon
          // Treat as a value context (error will be caught by JSON parsing)
          state = AfterValue
          InterpolationContext.Value
        case _: AfterValue.type =>
          // Interpolation after a value - unusual but treat as value
          InterpolationContext.Value
        case _ =>
          state = AfterValue
          InterpolationContext.Value
      }
      contexts.addOne(context)
    }
    new Right(contexts.result())
  }

  private def isWhitespace(ch: Char): Boolean = ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r'
}
