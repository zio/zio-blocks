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
