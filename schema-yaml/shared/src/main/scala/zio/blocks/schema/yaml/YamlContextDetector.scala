/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    }) contexts.addOne(detectSingleContext(part))
    new Right(contexts.result())
  }

  private[this] def detectSingleContext(part: String): YamlInterpolationContext = {
    var idx      = part.length - 1
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
    idx = part.length - 1
    while (idx >= 0) {
      val c = part.charAt(idx)
      if (c == ' ' || c == '\t') {
        idx -= 1
      } else if (c == ':') {
        sawColon = true
        idx = -1 // break
      } else if (c == '-' && (idx == 0 || isWhitespace(part.charAt(idx - 1)))) {
        // sequence item: "- " prefix, value context
        return YamlInterpolationContext.Value
      } else if (c == ',' || c == '[' || c == '{') {
        // flow context after comma or opening bracket
        // Determine if in flow mapping (key) or flow sequence (value)
        return detectFlowContext(part, idx)
      } else {
        // Other character at end — treat as value context
        return YamlInterpolationContext.Value
      }
    }

    if (sawColon) YamlInterpolationContext.Value
    else YamlInterpolationContext.Key
  }

  private[this] def detectFlowContext(part: String, index: Int): YamlInterpolationContext = {
    val ch = part.charAt(index)
    if (ch == '[') return YamlInterpolationContext.Value
    if (ch == '{') return YamlInterpolationContext.Key
    // ch == ','  — need to find enclosing container
    var idx   = index - 1
    var depth = 0
    while (idx >= 0) {
      val c = part.charAt(idx)
      if (c == ']' || c == '}') depth += 1
      else if (c == '[') {
        if (depth == 0) return YamlInterpolationContext.Value
        depth -= 1
      } else if (c == '{') {
        if (depth == 0) return YamlInterpolationContext.Key
        depth -= 1
      }
      idx -= 1
    }
    YamlInterpolationContext.Value
  }

  private[this] def isWhitespace(ch: Char): Boolean = ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r'
}
