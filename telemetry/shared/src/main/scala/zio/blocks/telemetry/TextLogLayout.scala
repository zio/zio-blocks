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

package zio.blocks.telemetry

/**
 * Shared text-layout location rendering for `TextLogFormatter` (both entry
 * points) and `StdoutLogRecordProcessor`.
 *
 * Approved layout is message-first: `timestamp SEVERITY body [compact-fn]
 * path:line {attrs}`. The captured `code.file.path` is preserved exactly as
 * emitted at compile time (never absolutized against the runtime cwd, never
 * shortened — Windows `\` separators stay literal), and the method never
 * appears inside the `path:line` token so terminal navigation keeps working.
 * When no path was captured the compact `[Class.method:line]` fallback stays
 * informational; a bare line with no function or path renders as `[line:42]`
 * rather than the ambiguous `[:42]`; with no location at all nothing renders
 * (no empty `[]`).
 */
private[telemetry] object TextLogLayout {

  /**
   * Append the location tokens prefixed with a single separation space; writes
   * nothing when no location field is present.
   */
  def appendLocation(sb: StringBuilder, filePath: String, functionName: String, lineNo: Long): Unit = {
    val func    = compactFunctionName(functionName)
    val hasPath = hasContent(filePath)
    val hasFunc = hasContent(func)
    val hasLine = lineNo > 0
    if (hasPath || hasFunc || hasLine) {
      sb.append(' ')
      if (hasPath) {
        if (hasFunc) {
          sb.append('[')
          appendLocationEscaped(sb, func)
          sb.append(']')
          sb.append(' ')
        }
        appendLocationEscaped(sb, filePath)
        if (hasLine) {
          sb.append(':')
          sb.append(lineNo)
        }
      } else if (hasFunc) {
        sb.append('[')
        appendLocationEscaped(sb, func)
        if (hasLine) {
          sb.append(':')
          sb.append(lineNo)
        }
        sb.append(']')
      } else {
        sb.append("[line:")
        sb.append(lineNo)
        sb.append(']')
      }
    }
  }

  /**
   * Compact a fully-qualified `code.function.name` (`com.example.Svc.doWork`)
   * to its last two segments (`Svc.doWork`). A dotless name renders as-is;
   * null/empty stays empty.
   */
  private def compactFunctionName(functionName: String): String =
    if (functionName == null || functionName.isEmpty) ""
    else {
      val lastDot = functionName.lastIndexOf('.')
      if (lastDot < 0) functionName
      else {
        val prevDot = functionName.lastIndexOf('.', lastDot - 1)
        functionName.substring(prevDot + 1)
      }
    }

  private def hasContent(s: String): Boolean = s != null && s.nonEmpty

  /**
   * Escape CR/LF/tab/control characters in captured file/function text so the
   * new placement cannot forge extra physical lines. Everything else —
   * including `\`, so Windows `C:\...` separators survive literally — passes
   * through untouched; the whole path is never quoted.
   */
  private def appendLocationEscaped(sb: StringBuilder, s: String): Unit =
    if (s == null) sb.append(s)
    else {
      var i = 0
      while (i < s.length) {
        val c = s.charAt(i)
        c match {
          case '\n' => sb.append("\\n")
          case '\r' => sb.append("\\r")
          case '\t' => sb.append("\\t")
          case _    =>
            if (c < 0x20) {
              sb.append("\\u")
              Hex.appendChar4(sb, c.toInt)
            } else sb.append(c)
        }
        i += 1
      }
    }
}
