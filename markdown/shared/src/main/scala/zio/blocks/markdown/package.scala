/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks

import zio.blocks.chunk.Chunk

package object markdown {

  /**
   * String interpolator extension for creating Markdown documents.
   *
   * Usage:
   * {{{
   * val name = "World"
   * val doc = md"# Hello $name"
   * }}}
   */
  implicit class MarkdownInterpolator(private val sc: StringContext) extends AnyVal {

    /**
     * Creates a Document from a Markdown string with interpolated values.
     *
     * @throws ParseError
     *   if the Markdown is invalid
     */
    def md(args: Any*): Document = {
      val parts  = sc.parts.iterator
      val values = args.iterator
      val sb     = new StringBuilder

      sb.append(parts.next())
      while (parts.hasNext) {
        val value = values.next()
        sb.append(valueToString(value))
        sb.append(parts.next())
      }

      Parser.parseUnsafe(sb.toString)
    }

    private def valueToString(value: Any): String = value match {
      case s: String      => s
      case i: Int         => i.toString
      case l: Long        => l.toString
      case d: Double      => d.toString
      case f: Float       => f.toString
      case b: Boolean     => b.toString
      case c: Char        => c.toString
      case bi: BigInt     => bi.toString
      case bd: BigDecimal => bd.toString
      case inline: Inline => inlineToString(inline)
      case doc: Document  => doc.render.stripSuffix("\n")
      case block: Block   => Renderer.render(Document(Chunk(block))).stripSuffix("\n")
      case other          => other.toString
    }

    private def inlineToString(inline: Inline): String = inline match {
      case Inline.Text(value) => value
      case Inline.Code(value) =>
        val ticks = if (value.contains("`")) "``" else "`"
        s"$ticks$value$ticks"
      case Inline.Emphasis(children)         => s"*${children.map(inlineToString).mkString}*"
      case Inline.Strong(children)           => s"**${children.map(inlineToString).mkString}**"
      case Inline.Strikethrough(children)    => s"~~${children.map(inlineToString).mkString}~~"
      case Inline.Link(children, url, title) =>
        val titlePart = title.map(t => s""" "$t"""").getOrElse("")
        s"[${children.map(inlineToString).mkString}]($url$titlePart)"
      case Inline.Image(alt, url, title) =>
        val titlePart = title.map(t => s""" "$t"""").getOrElse("")
        s"![$alt]($url$titlePart)"
      case Inline.HtmlInline(value)      => value
      case Inline.SoftBreak              => "\n"
      case Inline.HardBreak              => "  \n"
      case Inline.Autolink(url, isEmail) =>
        if (isEmail) s"<$url>" else s"<$url>"
    }
  }

  /**
   * Parses a Markdown string into a Document.
   */
  def parse(input: String): Either[ParseError, Document] = Parser.parse(input)

  /**
   * Parses a Markdown string into a Document, throwing on failure.
   */
  def parseUnsafe(input: String): Document = Parser.parseUnsafe(input)

  /**
   * Renders a Document to a Markdown string.
   */
  def render(doc: Document): String = Renderer.render(doc)

  /**
   * Renders a Document to a Markdown string with custom configuration.
   */
  def render(doc: Document, config: RenderConfig): String = Renderer.render(doc, config)
}
