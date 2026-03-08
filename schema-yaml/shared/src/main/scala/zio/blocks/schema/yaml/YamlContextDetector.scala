package zio.blocks.schema.yaml

import zio.blocks.chunk.ChunkBuilder

private[schema] object YamlContextDetector {

  def detectContexts(parts: Seq[String]): Either[String, Seq[YamlInterpolationContext]] =
    if (parts.isEmpty || parts.tail.isEmpty) new Right(Nil)
    else detectContextsImpl(parts)

  private[this] def detectContextsImpl(parts: Seq[String]): Either[String, Seq[YamlInterpolationContext]] = {
    val contexts     = ChunkBuilder.make[YamlInterpolationContext]()
    val it           = parts.iterator
    var part: String = null
    while ({
      part = it.next()
      it.hasNext
    }) {
      val ctx = detectSingleContext(part)
      contexts.addOne(ctx)
    }
    new Right(contexts.result())
  }

  private[this] def detectSingleContext(part: String): YamlInterpolationContext = {
    var i        = part.length - 1
    var inSingle = false
    var inDouble = false
    var sawColon = false

    // Scan backwards to find the closest relevant context character
    // First, check if we're inside a quoted string by scanning forwards
    var j = 0
    while (j < part.length) {
      val c = part.charAt(j)
      if (c == '\'' && !inDouble) inSingle = !inSingle
      else if (c == '"' && !inSingle) {
        if (!(j > 0 && part.charAt(j - 1) == '\\')) inDouble = !inDouble
      }
      j += 1
    }

    if (inSingle || inDouble) return YamlInterpolationContext.InString

    // Not inside a string — scan backwards from end to find context
    i = part.length - 1
    while (i >= 0) {
      val c = part.charAt(i)
      if (c == ' ' || c == '\t') {
        i -= 1
      } else if (c == ':') {
        sawColon = true
        i = -1 // break
      } else if (c == '-' && (i == 0 || isWhitespace(part.charAt(i - 1)))) {
        // sequence item: "- " prefix, value context
        return YamlInterpolationContext.Value
      } else if (c == ',' || c == '[' || c == '{') {
        // flow context after comma or opening bracket
        // Determine if in flow mapping (key) or flow sequence (value)
        return detectFlowContext(part, i)
      } else {
        // Other character at end — likely key context (before colon)
        return YamlInterpolationContext.Value
      }
    }

    if (sawColon) YamlInterpolationContext.Value
    else YamlInterpolationContext.Key
  }

  private[this] def detectFlowContext(part: String, idx: Int): YamlInterpolationContext = {
    val ch = part.charAt(idx)
    if (ch == '[') return YamlInterpolationContext.Value
    if (ch == '{') return YamlInterpolationContext.Key

    // ch == ','  — need to find enclosing container
    var i     = idx - 1
    var depth = 0
    while (i >= 0) {
      val c = part.charAt(i)
      if (c == ']' || c == '}') depth += 1
      else if (c == '[') {
        if (depth == 0) return YamlInterpolationContext.Value
        depth -= 1
      } else if (c == '{') {
        if (depth == 0) return YamlInterpolationContext.Key
        depth -= 1
      }
      i -= 1
    }
    YamlInterpolationContext.Value
  }

  private[this] def isWhitespace(ch: Char): Boolean = ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r'
}
