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

package zio.blocks.docs

/**
 * Run-time support for the `md` interpolator. The macro expands every `md"..."`
 * into a call to [[MdInterpolatorRuntime.parseAndBuild]]; code does not
 * normally call it directly.
 */
object MdInterpolatorRuntime {

  /**
   * Splice already converted values into the literal parts of a string context
   * and parse the result.
   *
   * Each element of `args` is written out with [[Renderer.renderInline]] and
   * placed after the part with the same position. A string context has one more
   * part than it has values; when `args` has fewer elements than gaps, the
   * remaining gaps are left empty.
   *
   * @param sc
   *   The string context whose parts are the literal text
   * @param args
   *   The values to splice in, already converted to inline elements
   * @return
   *   The parsed document
   * @throws java.lang.IllegalArgumentException
   *   if the combined text does not parse, with the message
   *   `Invalid markdown: ` followed by the [[ParseError]] message
   */
  def parseAndBuild(sc: StringContext, args: Seq[Inline]): Doc = {
    val combined = buildMarkdownString(sc.parts, args)
    Parser.parse(combined) match {
      case Right(doc) => doc
      case Left(err)  => throw new IllegalArgumentException(s"Invalid markdown: ${err.message}")
    }
  }

  private def buildMarkdownString(parts: Seq[String], args: Seq[Inline]): String = {
    val sb        = new StringBuilder
    val partsIter = parts.iterator
    val argsIter  = args.iterator
    while (partsIter.hasNext) {
      sb.append(partsIter.next())
      if (argsIter.hasNext) {
        sb.append(Renderer.renderInline(argsIter.next()))
      }
    }
    sb.toString
  }
}
