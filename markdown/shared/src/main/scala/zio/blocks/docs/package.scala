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
 * ZIO Blocks Markdown - A pure Scala GFM library.
 *
 * This package provides a complete implementation of GitHub Flavored Markdown
 * with a type-safe ADT, strict parser, renderer, and compile-time validated
 * string interpolator.
 *
 * ==Quick Start==
 * {{{
 *   import zio.blocks.docs._
 *
 *   // Parse markdown
 *   val doc = Parser.parse("# Hello *world*")
 *
 *   // Render to string
 *   val md = Renderer.render(doc.toOption.get)
 *
 *   // Use the interpolator
 *   val name = "World"
 *   val greeting = md"# Hello $$name"
 * }}}
 *
 * ==String Interpolator==
 * The `md` interpolator validates markdown at compile time and supports
 * interpolation of any type with a [[ToMarkdown]] instance.
 *
 * {{{
 *   val title = "My Doc"
 *   val count = 42
 *   val doc = md"""
 *     # $$title
 *
 *     There are $$count items.
 *   """
 * }}}
 *
 * ==Core Types==
 *   - [[Doc]] - A complete markdown document
 *   - [[Block]] - Block-level elements (headings, paragraphs, lists, etc.)
 *   - [[Inline]] - Inline elements (text, emphasis, links, etc.)
 *   - [[Parser]] - Strict GFM parser
 *   - [[Renderer]] - GFM renderer
 *
 * ==GFM Support==
 * Supports all GitHub Flavored Markdown features including:
 *   - ATX headings, code blocks, thematic breaks
 *   - Block quotes, lists (ordered, unordered, task lists)
 *   - Tables with alignment
 *   - Inline formatting (emphasis, strong, strikethrough, code)
 *   - Links, images, autolinks
 *   - HTML blocks and inline HTML
 */
package object docs extends MdInterpolator
