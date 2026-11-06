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

package zio.blocks

/**
 * ZIO Blocks Markdown: parse, build and render GitHub Flavored Markdown.
 *
 * The package has a document model ([[Doc]], [[Block]], [[Inline]]), a
 * line-based [[Parser]], renderers for Markdown ([[Renderer]]), HTML
 * ([[HtmlRenderer]]) and ANSI terminals ([[TerminalRenderer]]), and the `md`
 * string interpolator. Import the package to get all of them, including `md`.
 *
 * ==Quick Start==
 * {{{
 * val doc = Parser.parse("# Release 1.4\n\nFixed *two* bugs.").toOption.get
 *
 * val markdown = Renderer.render(doc)
 * // # Release 1.4
 * // Fixed *two* bugs.
 * //
 * val html = doc.toHtmlFragment
 * // <h1>Release 1.4</h1><p>Fixed <em>two</em> bugs.</p>
 * }}}
 *
 * ==String Interpolator==
 * The `md` interpolator checks the literal Markdown when the code compiles and
 * splices in any value that has a [[ToMarkdown]] instance.
 *
 * {{{
 * val title = "Release 1.4"
 * val count = 2
 *
 * val doc = md"## \$title, fixed \$count bugs"
 *
 * val markdown = doc.toString
 * // ## Release 1.4, fixed 2 bugs
 * }}}
 *
 * ==Core Types==
 *   - [[Doc]]: a document, a sequence of blocks
 *   - [[Block]]: block-level elements (headings, paragraphs, lists, tables,
 *     ...)
 *   - [[Inline]]: inline elements (text, emphasis, links, ...)
 *   - [[Parser]]: reads Markdown text into a `Doc`
 *   - [[Renderer]], [[HtmlRenderer]], [[TerminalRenderer]]: write a `Doc`
 *   - [[ToMarkdown]]: converts a value for use in `md`
 *
 * ==GFM Support==
 * The model and the renderers cover ATX headings, fenced code blocks, thematic
 * breaks, block quotes, bullet, ordered and task lists, tables with column
 * alignment, emphasis, strong, strikethrough, code spans, links, images,
 * autolinks, and HTML blocks and inline HTML. The parser reads a subset of GFM;
 * see [[Parser]] for what it does not read.
 *
 * @see
 *   [[https://zioblocks.com/reference/docs/ Docs]]
 */
package object docs extends MdInterpolator
