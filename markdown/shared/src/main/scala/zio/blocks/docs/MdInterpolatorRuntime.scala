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

object MdInterpolatorRuntime {

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
