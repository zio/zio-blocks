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

package zio.blocks.markdown

import zio.blocks.chunk.{Chunk, ChunkBuilder}

/**
 * A strict GitHub Flavored Markdown parser with position-aware error reporting.
 */
object Parser {

  /**
   * Parses a Markdown string into a Document.
   */
  def parse(input: String): Either[ParseError, Document] =
    try Right(parseUnsafe(input))
    catch {
      case e: ParseError => Left(e)
    }

  /**
   * Parses a Markdown string into a Document, throwing ParseError on failure.
   */
  def parseUnsafe(input: String): Document = {
    val state  = new ParserState(input)
    val blocks = parseBlocks(state)
    Document(blocks)
  }

  private class ParserState(val input: String) {
    var pos: Int    = 0
    var line: Int   = 1
    var column: Int = 1

    def remaining: String         = input.substring(pos)
    def isAtEnd: Boolean          = pos >= input.length
    def peek: Char                = if (isAtEnd) '\u0000' else input.charAt(pos)
    def peekAt(offset: Int): Char = if (pos + offset >= input.length) '\u0000' else input.charAt(pos + offset)

    def advance(): Char = {
      val c = peek
      pos += 1
      if (c == '\n') {
        line += 1
        column = 1
      } else {
        column += 1
      }
      c
    }

    def advanceN(n: Int): String = {
      val start = pos
      var i     = 0
      while (i < n && !isAtEnd) {
        advance()
        i += 1
      }
      input.substring(start, pos)
    }

    def skipWhitespace(): Unit =
      while (!isAtEnd && (peek == ' ' || peek == '\t')) advance()

    def skipSpaces(max: Int): Int = {
      var count = 0
      while (count < max && !isAtEnd && peek == ' ') {
        advance()
        count += 1
      }
      count
    }

    def consumeUntil(terminator: Char): String = {
      val sb = new StringBuilder
      while (!isAtEnd && peek != terminator) {
        sb.append(advance())
      }
      sb.toString
    }

    def consumeLine(): String = {
      val line = consumeUntil('\n')
      if (!isAtEnd && peek == '\n') advance()
      line
    }

    def lookingAt(s: String): Boolean =
      if (pos + s.length > input.length) false
      else input.regionMatches(pos, s, 0, s.length)

    def matches(regex: String): Option[String] = {
      val pattern = regex.r
      pattern.findPrefixOf(remaining)
    }

    def error(message: String): ParseError = {
      val near = remaining.take(30)
      ParseError(message, line, column, if (near.isEmpty) None else Some(near))
    }
  }

  private def parseBlocks(state: ParserState): Chunk[Block] = {
    val builder = ChunkBuilder.make[Block]()

    while (!state.isAtEnd) {
      skipBlankLines(state)
      if (!state.isAtEnd) {
        parseBlock(state) match {
          case Some(block) => builder.addOne(block)
          case None        => () // Skip unparseable content
        }
      }
    }

    builder.result()
  }

  private def skipBlankLines(state: ParserState): Unit =
    while (!state.isAtEnd) {
      val savedPos    = state.pos
      val savedLine   = state.line
      val savedColumn = state.column

      state.skipWhitespace()
      if (!state.isAtEnd && state.peek == '\n') {
        state.advance()
      } else {
        // Restore position if not a blank line
        state.pos = savedPos
        state.line = savedLine
        state.column = savedColumn
        return
      }
    }

  private def parseBlock(state: ParserState): Option[Block] = {
    state.skipSpaces(3)

    state.peek match {
      case '#'             => Some(parseHeading(state))
      case '>'             => Some(parseBlockQuote(state))
      case '-' | '*' | '+' =>
        if (isThematicBreak(state)) Some(parseThematicBreak(state))
        else if (isListItem(state)) Some(parseBulletList(state))
        else Some(parseParagraph(state))
      case '_' =>
        if (isThematicBreak(state)) Some(parseThematicBreak(state))
        else Some(parseParagraph(state))
      case '`' =>
        if (isFencedCodeBlock(state)) Some(parseFencedCodeBlock(state))
        else Some(parseParagraph(state))
      case '~' =>
        if (isFencedCodeBlock(state)) Some(parseFencedCodeBlock(state))
        else Some(parseParagraph(state))
      case '<' =>
        if (isHtmlBlock(state)) Some(parseHtmlBlock(state))
        else Some(parseParagraph(state))
      case '|' =>
        if (isTable(state)) parseTable(state)
        else Some(parseParagraph(state))
      case c if c.isDigit =>
        if (isOrderedListItem(state)) Some(parseOrderedList(state))
        else Some(parseParagraph(state))
      case '\n' | '\r' => None // Blank line
      case '\u0000'    => None // End of input
      case _           => Some(parseParagraph(state))
    }
  }

  private def parseHeading(state: ParserState): Block = {
    var level = 0
    while (!state.isAtEnd && state.peek == '#' && level < 6) {
      state.advance()
      level += 1
    }

    if (level == 0 || (!state.isAtEnd && state.peek != ' ' && state.peek != '\n')) {
      // Not a valid heading, treat as paragraph
      return parseParagraphStartingWith("#" * level, state)
    }

    if (!state.isAtEnd && state.peek == ' ') state.advance()

    val contentLine = state.consumeLine().trim
    // Remove trailing # characters
    val content = contentLine.replaceAll("\\s+#+\\s*$", "").trim

    val inlines = parseInlines(content)
    Block.Heading(HeadingLevel.unsafeFromInt(level), inlines)
  }

  private def parseParagraph(state: ParserState): Block =
    parseParagraphStartingWith("", state)

  private def parseParagraphStartingWith(prefix: String, state: ParserState): Block = {
    val lines = new StringBuilder
    if (prefix.nonEmpty) lines.append(prefix)

    var continueParsing = true
    while (continueParsing && !state.isAtEnd) {
      val savedPos    = state.pos
      val savedLine   = state.line
      val savedColumn = state.column

      val line = state.consumeLine()

      // Check if line is blank or starts a new block
      if (line.trim.isEmpty) {
        continueParsing = false
      } else if (startsNewBlock(line)) {
        // Restore position and stop
        state.pos = savedPos
        state.line = savedLine
        state.column = savedColumn
        continueParsing = false
      } else {
        if (lines.nonEmpty) lines.append('\n')
        lines.append(line)
      }
    }

    val inlines = parseInlines(lines.toString)
    Block.Paragraph(inlines)
  }

  private def startsNewBlock(line: String): Boolean = {
    val trimmed = line.trim
    if (trimmed.isEmpty) return true

    // Heading
    if (trimmed.startsWith("#")) return true

    // Block quote
    if (trimmed.startsWith(">")) return true

    // Thematic break
    if (isThematicBreakLine(trimmed)) return true

    // Fenced code block
    if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) return true

    // List items
    if (trimmed.matches("^[-*+]\\s.*")) return true
    if (trimmed.matches("^\\d+[.)]\\s.*")) return true

    // HTML block
    if (trimmed.startsWith("<") && isHtmlBlockStart(trimmed)) return true

    false
  }

  private def isThematicBreakLine(line: String): Boolean = {
    val stripped = line.filterNot(_ == ' ')
    if (stripped.length < 3) return false

    val char = stripped.head
    if (char != '-' && char != '*' && char != '_') return false

    stripped.forall(_ == char)
  }

  private def isHtmlBlockStart(line: String): Boolean = {
    val lower = line.toLowerCase
    lower.startsWith("<pre") || lower.startsWith("<script") ||
    lower.startsWith("<style") || lower.startsWith("<div") ||
    lower.startsWith("<table") || lower.startsWith("<p") ||
    lower.startsWith("<!--") || lower.startsWith("<!doctype")
  }

  private def isThematicBreak(state: ParserState): Boolean = {
    val savedPos    = state.pos
    val savedLine   = state.line
    val savedColumn = state.column

    val line   = state.consumeLine()
    val result = isThematicBreakLine(line)

    state.pos = savedPos
    state.line = savedLine
    state.column = savedColumn
    result
  }

  private def parseThematicBreak(state: ParserState): Block = {
    state.consumeLine()
    Block.ThematicBreak
  }

  private def isListItem(state: ParserState): Boolean = {
    val c = state.peek
    if (c != '-' && c != '*' && c != '+') return false

    val next = state.peekAt(1)
    next == ' ' || next == '\t' || next == '\n'
  }

  private def isOrderedListItem(state: ParserState): Boolean = {
    var i = 0
    while (state.peekAt(i).isDigit && i < 9) i += 1
    if (i == 0) return false

    val marker = state.peekAt(i)
    if (marker != '.' && marker != ')') return false

    val after = state.peekAt(i + 1)
    after == ' ' || after == '\t' || after == '\n'
  }

  private def parseBulletList(state: ParserState): Block = {
    val items = ChunkBuilder.make[Block.ListItem]()
    var tight = true

    while (!state.isAtEnd && isListItem(state)) {
      val (item, hadBlankLine) = parseBulletListItem(state)
      items.addOne(item)
      if (hadBlankLine && !state.isAtEnd && isListItem(state)) tight = false
    }

    Block.BulletList(items.result(), tight)
  }

  private def parseBulletListItem(state: ParserState): (Block.ListItem, Boolean) = {
    // Consume bullet marker
    state.advance() // - or * or +
    state.skipSpaces(1)

    val (checked, _) = parseTaskListMarker(state)

    val contentBuilder = new StringBuilder
    var hadBlankLine   = false

    // First line
    contentBuilder.append(state.consumeLine())

    // Continue with indented lines
    while (!state.isAtEnd) {
      val savedPos    = state.pos
      val savedLine   = state.line
      val savedColumn = state.column

      // Check for blank line
      var shouldContinue = false
      if (state.peek == '\n' || (state.remaining.take(20).trim.isEmpty && state.peek != '\u0000')) {
        val line = state.consumeLine()
        if (line.trim.isEmpty) {
          hadBlankLine = true
          // Check if next line is indented continuation
          if (!state.isAtEnd) {
            val nextSpaces = countLeadingSpaces(state.remaining)
            if (nextSpaces >= 2 && !isListItem(state) && !isOrderedListItem(state)) {
              contentBuilder.append('\n')
              contentBuilder.append('\n')
              shouldContinue = true
            } else {
              // End of list item
              state.pos = savedPos
              state.line = savedLine
              state.column = savedColumn
              return (makeListItem(contentBuilder.toString, checked), hadBlankLine)
            }
          }
        }
      }

      if (!shouldContinue) {
        // Check for indented content
        val spaces = countLeadingSpaces(state.remaining)
        if (spaces >= 2) {
          state.advanceN(2) // Skip indentation
          contentBuilder.append('\n')
          contentBuilder.append(state.consumeLine())
        } else if (
          isListItem(state) || isOrderedListItem(state) || startsNewBlock(state.remaining.takeWhile(_ != '\n'))
        ) {
          // New list item or block
          return (makeListItem(contentBuilder.toString, checked), hadBlankLine)
        } else if (state.remaining.trim.nonEmpty) {
          // Continuation paragraph (lazy continuation)
          contentBuilder.append('\n')
          contentBuilder.append(state.consumeLine())
        } else {
          return (makeListItem(contentBuilder.toString, checked), hadBlankLine)
        }
      }
    }

    (makeListItem(contentBuilder.toString, checked), hadBlankLine)
  }

  private def countLeadingSpaces(s: String): Int = {
    var count = 0
    val len   = s.length
    while (count < len && s.charAt(count) == ' ') count += 1
    count
  }

  private def parseTaskListMarker(state: ParserState): (Option[Boolean], Boolean) =
    if (state.lookingAt("[ ] ")) {
      state.advanceN(4)
      (Some(false), true)
    } else if (state.lookingAt("[x] ") || state.lookingAt("[X] ")) {
      state.advanceN(4)
      (Some(true), true)
    } else {
      (None, false)
    }

  private def makeListItem(content: String, checked: Option[Boolean]): Block.ListItem = {
    val blocks = parseBlocksFromString(content.trim)
    Block.ListItem(blocks, checked)
  }

  private def parseBlocksFromString(content: String): Chunk[Block] = {
    if (content.isEmpty) return Chunk.empty
    val state = new ParserState(content)
    parseBlocks(state)
  }

  private def parseOrderedList(state: ParserState): Block = {
    val items = ChunkBuilder.make[Block.ListItem]()
    var tight = true
    var start = 1

    var first = true
    while (!state.isAtEnd && isOrderedListItem(state)) {
      val (item, itemStart, hadBlankLine) = parseOrderedListItem(state)
      if (first) {
        start = itemStart
        first = false
      }
      items.addOne(item)
      if (hadBlankLine && !state.isAtEnd && isOrderedListItem(state)) tight = false
    }

    Block.OrderedList(items.result(), start, tight)
  }

  private def parseOrderedListItem(state: ParserState): (Block.ListItem, Int, Boolean) = {
    // Parse number
    val numBuilder = new StringBuilder
    while (!state.isAtEnd && state.peek.isDigit) {
      numBuilder.append(state.advance())
    }
    val start = numBuilder.toString.toInt

    // Consume marker
    state.advance() // . or )
    state.skipSpaces(1)

    val (checked, _) = parseTaskListMarker(state)

    val contentBuilder = new StringBuilder
    var hadBlankLine   = false

    // First line
    contentBuilder.append(state.consumeLine())

    // Continue with indented lines
    while (!state.isAtEnd) {
      val savedPos    = state.pos
      val savedLine   = state.line
      val savedColumn = state.column

      // Check for blank line
      var shouldContinue = false
      if (state.peek == '\n' || (state.remaining.take(20).trim.isEmpty && state.peek != '\u0000')) {
        val line = state.consumeLine()
        if (line.trim.isEmpty) {
          hadBlankLine = true
          // Check if next line is indented continuation
          if (!state.isAtEnd) {
            val nextSpaces = countLeadingSpaces(state.remaining)
            if (nextSpaces >= 3 && !isListItem(state) && !isOrderedListItem(state)) {
              contentBuilder.append('\n')
              contentBuilder.append('\n')
              shouldContinue = true
            } else {
              state.pos = savedPos
              state.line = savedLine
              state.column = savedColumn
              return (makeListItem(contentBuilder.toString, checked), start, hadBlankLine)
            }
          }
        }
      }

      if (!shouldContinue) {
        // Check for indented content
        val spaces = countLeadingSpaces(state.remaining)
        if (spaces >= 3) {
          state.advanceN(3) // Skip indentation
          contentBuilder.append('\n')
          contentBuilder.append(state.consumeLine())
        } else if (
          isListItem(state) || isOrderedListItem(state) || startsNewBlock(state.remaining.takeWhile(_ != '\n'))
        ) {
          return (makeListItem(contentBuilder.toString, checked), start, hadBlankLine)
        } else if (state.remaining.trim.nonEmpty) {
          contentBuilder.append('\n')
          contentBuilder.append(state.consumeLine())
        } else {
          return (makeListItem(contentBuilder.toString, checked), start, hadBlankLine)
        }
      }
    }

    (makeListItem(contentBuilder.toString, checked), start, hadBlankLine)
  }

  private def parseBlockQuote(state: ParserState): Block = {
    val lines = new StringBuilder

    while (!state.isAtEnd) {
      state.skipSpaces(3)

      if (state.peek == '>') {
        state.advance()
        if (!state.isAtEnd && state.peek == ' ') state.advance()
        lines.append(state.consumeLine())
        lines.append('\n')
      } else if (
        lines.nonEmpty && state.remaining.trim.nonEmpty && !startsNewBlock(state.remaining.takeWhile(_ != '\n'))
      ) {
        // Lazy continuation
        lines.append(state.consumeLine())
        lines.append('\n')
      } else {
        // Done with block quote
        // Don't consume the line, leave it for the next block
        // Restore any leading spaces we consumed
        return Block.BlockQuote(parseBlocksFromString(lines.toString.trim))
      }
    }

    Block.BlockQuote(parseBlocksFromString(lines.toString.trim))
  }

  private def isFencedCodeBlock(state: ParserState): Boolean = {
    val c = state.peek
    if (c != '`' && c != '~') return false

    var count = 0
    while (state.peekAt(count) == c && count < 10) count += 1
    count >= 3
  }

  private def parseFencedCodeBlock(state: ParserState): Block = {
    val fenceChar = state.advance()
    var fenceLen  = 1

    while (!state.isAtEnd && state.peek == fenceChar) {
      state.advance()
      fenceLen += 1
    }

    // Parse info string (language)
    val infoString = state.consumeLine().trim
    val info       = if (infoString.isEmpty) None else Some(infoString.split("\\s").head)

    val codeBuilder = new StringBuilder

    // Parse code content until closing fence
    while (!state.isAtEnd) {
      val savedPos    = state.pos
      val savedLine   = state.line
      val savedColumn = state.column

      state.skipSpaces(3)

      // Check for closing fence
      var closingFenceLen = 0
      while (!state.isAtEnd && state.peek == fenceChar) {
        state.advance()
        closingFenceLen += 1
      }

      if (closingFenceLen >= fenceLen && (state.isAtEnd || state.peek == '\n')) {
        // Found closing fence
        if (!state.isAtEnd) state.advance() // consume newline
        return Block.CodeBlock(codeBuilder.toString.stripSuffix("\n"), info)
      } else {
        // Not a closing fence, restore and add line to code
        state.pos = savedPos
        state.line = savedLine
        state.column = savedColumn
        codeBuilder.append(state.consumeLine())
        codeBuilder.append('\n')
      }
    }

    // EOF without closing fence
    Block.CodeBlock(codeBuilder.toString.stripSuffix("\n"), info)
  }

  private def isHtmlBlock(state: ParserState): Boolean = {
    if (state.peek != '<') return false
    isHtmlBlockStart(state.remaining.takeWhile(_ != '\n'))
  }

  private def parseHtmlBlock(state: ParserState): Block = {
    val htmlBuilder = new StringBuilder

    // Determine HTML block type
    val firstLine = state.remaining.takeWhile(_ != '\n').toLowerCase

    // Type 1-5: specific tags
    val isType1    = firstLine.startsWith("<pre") || firstLine.startsWith("<script") || firstLine.startsWith("<style")
    val endPattern = if (isType1) {
      if (firstLine.startsWith("<pre")) "</pre>"
      else if (firstLine.startsWith("<script")) "</script>"
      else "</style>"
    } else ""

    if (isType1) {
      // Read until closing tag
      while (!state.isAtEnd) {
        val line = state.consumeLine()
        htmlBuilder.append(line)
        htmlBuilder.append('\n')
        if (line.toLowerCase.contains(endPattern)) {
          return Block.HtmlBlock(htmlBuilder.toString.stripSuffix("\n"))
        }
      }
    } else {
      // Type 6/7: read until blank line
      while (!state.isAtEnd) {
        val line = state.consumeLine()
        if (line.trim.isEmpty) {
          return Block.HtmlBlock(htmlBuilder.toString.stripSuffix("\n"))
        }
        htmlBuilder.append(line)
        htmlBuilder.append('\n')
      }
    }

    Block.HtmlBlock(htmlBuilder.toString.stripSuffix("\n"))
  }

  private def isTable(state: ParserState): Boolean = {
    val savedPos    = state.pos
    val savedLine   = state.line
    val savedColumn = state.column

    // First line should have pipes
    val firstLine = state.consumeLine()
    if (!firstLine.contains('|')) {
      state.pos = savedPos
      state.line = savedLine
      state.column = savedColumn
      return false
    }

    // Second line should be delimiter row
    val secondLine  = state.consumeLine()
    val isDelimiter = secondLine.trim.matches("^\\|?[:\\-\\|\\s]+\\|?$") &&
      secondLine.contains('-')

    state.pos = savedPos
    state.line = savedLine
    state.column = savedColumn
    isDelimiter
  }

  private def parseTable(state: ParserState): Option[Block] = {
    // Parse header row
    val headerLine  = state.consumeLine()
    val headerCells = parseTableRow(headerLine)

    // Parse delimiter row
    val delimiterLine = state.consumeLine()
    val alignments    = parseAlignments(delimiterLine)

    if (alignments.isEmpty) {
      return None
    }

    // Parse body rows
    val rows = ChunkBuilder.make[Chunk[Block.TableCell]]()

    while (!state.isAtEnd) {
      val savedPos    = state.pos
      val savedLine   = state.line
      val savedColumn = state.column

      val line = state.consumeLine()
      if (line.trim.isEmpty || !line.contains('|')) {
        state.pos = savedPos
        state.line = savedLine
        state.column = savedColumn
        // Done with table
        return Some(Block.Table(headerCells, alignments, rows.result()))
      } else {
        rows.addOne(parseTableRow(line))
      }
    }

    Some(Block.Table(headerCells, alignments, rows.result()))
  }

  private def parseTableRow(line: String): Chunk[Block.TableCell] = {
    val trimmed             = line.trim
    val withoutLeadingPipe  = if (trimmed.startsWith("|")) trimmed.drop(1) else trimmed
    val withoutTrailingPipe =
      if (withoutLeadingPipe.endsWith("|")) withoutLeadingPipe.dropRight(1) else withoutLeadingPipe

    val cells = splitOnUnescapedPipe(withoutTrailingPipe)
    Chunk.from(cells.map { cell =>
      val content = cell.trim.replace("\\|", "|")
      Block.TableCell(parseInlines(content))
    })
  }

  // Split on | that isn't preceded by backslash (ES5-compatible, no lookbehind)
  private def splitOnUnescapedPipe(s: String): Array[String] = {
    val result  = scala.collection.mutable.ArrayBuffer[String]()
    val current = new StringBuilder
    var i       = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '|' && (i == 0 || s.charAt(i - 1) != '\\')) {
        result += current.toString
        current.clear()
      } else {
        current.append(c)
      }
      i += 1
    }
    result += current.toString
    result.toArray
  }

  private def parseAlignments(line: String): Chunk[Alignment] = {
    val trimmed             = line.trim
    val withoutLeadingPipe  = if (trimmed.startsWith("|")) trimmed.drop(1) else trimmed
    val withoutTrailingPipe =
      if (withoutLeadingPipe.endsWith("|")) withoutLeadingPipe.dropRight(1) else withoutLeadingPipe

    val cells = withoutTrailingPipe.split("\\|", -1)

    val alignmentsOpt = cells.map { cell =>
      val c = cell.trim
      if (!c.matches("^:?-+:?$")) None
      else {
        val left  = c.startsWith(":")
        val right = c.endsWith(":")

        val alignment =
          if (left && right) Alignment.Center
          else if (left) Alignment.Left
          else if (right) Alignment.Right
          else Alignment.None

        Some(alignment)
      }
    }

    if (alignmentsOpt.exists(_.isEmpty)) Chunk.empty[Alignment]
    else Chunk.from(alignmentsOpt.flatten.toSeq)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Inline Parsing
  // ─────────────────────────────────────────────────────────────────────────

  private def parseInlines(input: String): Chunk[Inline] = {
    if (input.isEmpty) return Chunk.empty

    val builder = ChunkBuilder.make[Inline]()
    var pos     = 0
    val len     = input.length
    val text    = new StringBuilder

    def flushText(): Unit =
      if (text.nonEmpty) {
        builder.addOne(Inline.Text(text.toString))
        text.clear()
      }

    def charAt(i: Int): Char = if (i >= len) '\u0000' else input.charAt(i)

    while (pos < len) {
      val c = input.charAt(pos)

      c match {
        case '\\' if pos + 1 < len =>
          // Escape sequence
          val next = input.charAt(pos + 1)
          if ("\\`*_{}[]()#+-.!|~<>".contains(next)) {
            text.append(next)
            pos += 2
          } else {
            text.append(c)
            pos += 1
          }

        case '`' =>
          flushText()
          val (code, endPos) = parseCodeSpan(input, pos)
          builder.addOne(code)
          pos = endPos

        case '*' | '_' =>
          flushText()
          val (inline, endPos) = parseEmphasis(input, pos, c)
          builder.addOne(inline)
          pos = endPos

        case '~' if charAt(pos + 1) == '~' =>
          flushText()
          val (inline, endPos) = parseStrikethrough(input, pos)
          builder.addOne(inline)
          pos = endPos

        case '[' =>
          flushText()
          val (inline, endPos) = parseLinkOrImage(input, pos, isImage = false)
          builder.addOne(inline)
          pos = endPos

        case '!' if charAt(pos + 1) == '[' =>
          flushText()
          val (inline, endPos) = parseLinkOrImage(input, pos + 1, isImage = true)
          builder.addOne(inline)
          pos = endPos

        case '<' =>
          flushText()
          val (inline, endPos) = parseAutolink(input, pos)
          builder.addOne(inline)
          pos = endPos

        case '\n' =>
          // Check for hard break (two trailing spaces or backslash)
          if (text.nonEmpty && (text.endsWith("  ") || text.endsWith("\\"))) {
            if (text.endsWith("\\")) text.deleteCharAt(text.length - 1)
            else text.delete(text.length - 2, text.length)
            flushText()
            builder.addOne(Inline.HardBreak)
          } else {
            flushText()
            builder.addOne(Inline.SoftBreak)
          }
          pos += 1

        case _ =>
          text.append(c)
          pos += 1
      }
    }

    flushText()
    builder.result()
  }

  private def parseCodeSpan(input: String, start: Int): (Inline, Int) = {
    var pos       = start
    val len       = input.length
    var openTicks = 0

    // Count opening backticks
    while (pos < len && input.charAt(pos) == '`') {
      openTicks += 1
      pos += 1
    }

    val contentStart = pos
    var contentEnd   = pos

    // Find closing backticks
    while (pos < len) {
      if (input.charAt(pos) == '`') {
        val tickStart = pos
        var ticks     = 0
        while (pos < len && input.charAt(pos) == '`') {
          ticks += 1
          pos += 1
        }
        if (ticks == openTicks) {
          // Found matching close
          val content = input.substring(contentStart, tickStart)
          // Normalize: strip leading/trailing space if both present
          val normalized =
            if (content.nonEmpty && content.startsWith(" ") && content.endsWith(" ") && content.length > 2) {
              content.drop(1).dropRight(1)
            } else content
          return (Inline.Code(normalized.replace('\n', ' ')), pos)
        }
        contentEnd = pos
      } else {
        pos += 1
        contentEnd = pos
      }
    }

    // No closing backticks found, treat as text
    (Inline.Text("`" * openTicks + input.substring(contentStart, contentEnd)), contentEnd)
  }

  private def parseEmphasis(input: String, start: Int, marker: Char): (Inline, Int) = {
    var pos   = start
    val len   = input.length
    var count = 0

    // Count opening markers
    while (pos < len && input.charAt(pos) == marker) {
      count += 1
      pos += 1
    }

    if (count > 2) count = 2 // Limit to 2 for strong

    val isStrong     = count == 2
    val contentStart = pos

    // Find closing markers
    var contentEnd = pos
    var foundClose = false

    while (pos < len && !foundClose) {
      val c = input.charAt(pos)

      if (c == '\\' && pos + 1 < len) {
        pos += 2 // Skip escape
      } else if (c == marker) {
        var closeCount = 0
        val closeStart = pos
        while (pos < len && input.charAt(pos) == marker) {
          closeCount += 1
          pos += 1
        }
        if (closeCount >= count) {
          contentEnd = closeStart
          foundClose = true
        }
      } else {
        pos += 1
      }
    }

    if (!foundClose) {
      // No closing marker, treat as text
      return (Inline.Text((marker.toString * count) + input.substring(contentStart, pos)), pos)
    }

    val content  = input.substring(contentStart, contentEnd)
    val children = parseInlines(content)

    val inline = if (isStrong) Inline.Strong(children) else Inline.Emphasis(children)
    (inline, pos)
  }

  private def parseStrikethrough(input: String, start: Int): (Inline, Int) = {
    var pos          = start + 2 // Skip ~~
    val len          = input.length
    val contentStart = pos

    // Find closing ~~
    while (pos < len - 1) {
      if (input.charAt(pos) == '~' && input.charAt(pos + 1) == '~') {
        val content  = input.substring(contentStart, pos)
        val children = parseInlines(content)
        return (Inline.Strikethrough(children), pos + 2)
      }
      pos += 1
    }

    // No closing ~~, treat as text
    (Inline.Text("~~" + input.substring(contentStart, len)), len)
  }

  private def parseLinkOrImage(input: String, start: Int, isImage: Boolean): (Inline, Int) = {
    var pos = start + 1 // Skip [
    val len = input.length

    // Parse link text
    val textStart = pos
    var depth     = 1

    while (pos < len && depth > 0) {
      val c = input.charAt(pos)
      if (c == '\\' && pos + 1 < len) {
        pos += 2
      } else if (c == '[') {
        depth += 1
        pos += 1
      } else if (c == ']') {
        depth -= 1
        pos += 1
      } else {
        pos += 1
      }
    }

    if (depth != 0 || pos >= len) {
      // No closing bracket
      val prefix = if (isImage) "![" else "["
      return (Inline.Text(prefix + input.substring(textStart, pos)), pos)
    }

    val linkText = input.substring(textStart, pos - 1)

    // Check for (url) or (url "title")
    if (pos < len && input.charAt(pos) == '(') {
      pos += 1

      // Skip whitespace
      while (pos < len && input.charAt(pos).isWhitespace) pos += 1

      // Parse URL
      val urlStart   = pos
      var urlEnd     = pos
      var parenDepth = 1

      // Handle URL in angle brackets
      if (pos < len && input.charAt(pos) == '<') {
        pos += 1
        val urlContentStart = pos
        while (pos < len && input.charAt(pos) != '>') pos += 1
        urlEnd = pos
        val url = input.substring(urlContentStart, urlEnd)
        if (pos < len) pos += 1 // Skip >

        // Skip whitespace and look for title
        while (pos < len && input.charAt(pos).isWhitespace) pos += 1

        val (title, endPos) = parseLinkTitle(input, pos)
        while (endPos < len && input.charAt(endPos).isWhitespace) {}

        if (endPos < len && input.charAt(endPos) == ')') {
          val inline =
            if (isImage) Inline.Image(linkText, url, title)
            else Inline.Link(parseInlines(linkText), url, title)
          return (inline, endPos + 1)
        }
      } else {
        // Regular URL
        while (pos < len && parenDepth > 0) {
          val c = input.charAt(pos)
          if (c == '(') parenDepth += 1
          else if (c == ')') parenDepth -= 1
          else if (c.isWhitespace && parenDepth == 1) {
            urlEnd = pos
            // Look for title
            while (pos < len && input.charAt(pos).isWhitespace) pos += 1
            val (title, endPos) = parseLinkTitle(input, pos)

            // Skip to closing paren
            var p = endPos
            while (p < len && input.charAt(p).isWhitespace) p += 1

            if (p < len && input.charAt(p) == ')') {
              val url    = input.substring(urlStart, urlEnd)
              val inline =
                if (isImage) Inline.Image(linkText, url, title)
                else Inline.Link(parseInlines(linkText), url, title)
              return (inline, p + 1)
            }
          }
          pos += 1
        }

        urlEnd = pos - 1
        val url    = input.substring(urlStart, urlEnd)
        val inline =
          if (isImage) Inline.Image(linkText, url, None)
          else Inline.Link(parseInlines(linkText), url, None)
        return (inline, pos)
      }
    }

    // No (url), treat as text
    val prefix = if (isImage) "![" else "["
    (Inline.Text(prefix + linkText + "]"), pos)
  }

  private def parseLinkTitle(input: String, start: Int): (Option[String], Int) = {
    if (start >= input.length) return (None, start)

    val quote = input.charAt(start)
    if (quote != '"' && quote != '\'' && quote != '(') return (None, start)

    val closeQuote = if (quote == '(') ')' else quote
    var pos        = start + 1

    while (pos < input.length && input.charAt(pos) != closeQuote) {
      if (input.charAt(pos) == '\\' && pos + 1 < input.length) pos += 2
      else pos += 1
    }

    if (pos >= input.length) return (None, start)

    val title = input.substring(start + 1, pos)
    (Some(title), pos + 1)
  }

  private def parseAutolink(input: String, start: Int): (Inline, Int) = {
    var pos = start + 1 // Skip <
    val len = input.length

    // Find closing >
    val contentStart = pos
    while (pos < len && input.charAt(pos) != '>') {
      pos += 1
    }

    if (pos >= len) {
      return (Inline.Text("<"), start + 1)
    }

    val content = input.substring(contentStart, pos)
    pos += 1 // Skip >

    // Check if it's an email or URL
    if (content.contains("://")) {
      (Inline.Autolink(content, isEmail = false), pos)
    } else if (content.contains("@") && !content.startsWith("@")) {
      (Inline.Autolink(content, isEmail = true), pos)
    } else {
      // Treat as HTML inline
      (Inline.HtmlInline("<" + content + ">"), pos)
    }
  }
}
