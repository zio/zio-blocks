package zio.blocks.docs

import zio.blocks.chunk.Chunk

/**
 * Parser for GitHub Flavored Markdown.
 *
 * Provides strict parsing of GFM documents, returning either a parsed [[Doc]]
 * or a [[ParseError]] with position information.
 *
 * ==Supported Features==
 *   - ATX headings (# to ######)
 *   - Fenced code blocks (``` or ~~~)
 *   - Thematic breaks (---, ***, ___)
 *   - Block quotes (> prefix)
 *   - Bullet and ordered lists
 *   - Task lists (- [ ] and - [x])
 *   - Tables with alignment
 *   - Inline formatting (emphasis, strong, strikethrough, code)
 *   - Links and images
 *   - Autolinks
 *   - HTML blocks and inline HTML
 *
 * ==Not Supported==
 *   - YAML frontmatter (causes parse error)
 *   - Setext headings (use ATX style)
 *   - Indented code blocks (use fenced)
 *   - Link reference definitions
 */
object Parser {

  /**
   * Parses a markdown string into a Doc.
   *
   * @param input
   *   The markdown string to parse
   * @return
   *   Either a [[ParseError]] with position info, or the parsed [[Doc]]
   *
   * @example
   *   {{{ Parser.parse("# Hello") match { case Right(doc) => println(s"Parsed
   *   $${doc.blocks.size} blocks") case Left(err) => println(s"Error at line
   *   $${err.line}: $${err.message}") } }}}
   */
  def parse(input: String): Either[ParseError, Doc] = {
    val state = new ParserState(input)
    state.parseDocument()
  }

  private class ParserState(input: String) {
    private val lines: Array[String] = input.split("\n", -1)
    private var lineIndex: Int       = 0

    def parseDocument(): Either[ParseError, Doc] =
      for {
        _      <- checkFrontmatter()
        blocks <- parseBlocks()
      } yield Doc(blocks)

    private def checkFrontmatter(): Either[ParseError, Unit] =
      if (lines.nonEmpty && lines(0) == "---") {
        var i        = 1
        var foundEnd = false
        while (i < lines.length && !foundEnd) {
          if (lines(i) == "---") foundEnd = true
          i += 1
        }
        if (foundEnd && i > 2) {
          Left(ParseError("Frontmatter is not supported", 1, 1, lines(0)))
        } else {
          Right(())
        }
      } else {
        Right(())
      }

    private def parseBlocks(): Either[ParseError, Chunk[Block]] = {
      val blocks = Chunk.newBuilder[Block]

      while (lineIndex < lines.length) {
        skipBlankLines()
        if (lineIndex < lines.length) {
          parseBlock() match {
            case Left(err)    => return Left(err)
            case Right(block) => blocks += block
          }
        }
      }

      Right(blocks.result())
    }

    private def skipBlankLines(): Unit =
      while (lineIndex < lines.length && lines(lineIndex).trim.isEmpty) {
        lineIndex += 1
      }

    private def currentLine: String =
      if (lineIndex < lines.length) lines(lineIndex) else ""

    private def parseBlock(): Either[ParseError, Block] = {
      val line = currentLine

      if (line.startsWith("#")) {
        parseHeading()
      } else if (line.startsWith("```") || line.startsWith("~~~")) {
        parseCodeBlock()
      } else if (isThematicBreak(line)) {
        lineIndex += 1
        Right(ThematicBreak)
      } else if (line.startsWith(">")) {
        parseBlockQuote()
      } else if (isBulletListItem(line)) {
        parseBulletList()
      } else if (isOrderedListItem(line)) {
        parseOrderedList()
      } else if (isTableStart()) {
        parseTable()
      } else if (line.startsWith("<") && isHtmlBlockStart(line)) {
        parseHtmlBlock()
      } else {
        parseParagraph()
      }
    }

    private def parseHeading(): Either[ParseError, Heading] = {
      val line   = currentLine
      val hashes = line.takeWhile(_ == '#')
      val level  = hashes.length

      if (level > 6) {
        return Left(ParseError(s"Invalid heading level: $level (max is 6)", lineIndex + 1, 1, line))
      }

      HeadingLevel.fromInt(level) match {
        case None =>
          Left(ParseError(s"Invalid heading level: $level", lineIndex + 1, 1, line))
        case Some(headingLevel) =>
          val rest = line.drop(level)
          if (rest.nonEmpty && rest.head != ' ') {
            lineIndex += 1
            val inlines = parseInlines(line)
            Right(Heading(HeadingLevel.H1, Chunk(Paragraph(inlines).content: _*)))
          } else {
            var content = rest.dropWhile(_ == ' ')
            content = content.reverse.dropWhile(_ == '#').dropWhile(_ == ' ').reverse
            lineIndex += 1
            val inlines = parseInlines(content)
            Right(Heading(headingLevel, inlines))
          }
      }
    }

    private def parseCodeBlock(): Either[ParseError, CodeBlock] = {
      val startLine  = currentLine
      val fenceChar  = startLine.head
      val fenceCount = startLine.takeWhile(_ == fenceChar).length
      val info       = startLine.drop(fenceCount).trim
      val infoOpt    = if (info.isEmpty) None else Some(info)

      lineIndex += 1
      val codeLines = Chunk.newBuilder[String]

      while (lineIndex < lines.length) {
        val line = currentLine
        if (line.startsWith(fenceChar.toString * fenceCount) && line.trim == fenceChar.toString * fenceCount) {
          lineIndex += 1
          return Right(CodeBlock(infoOpt, codeLines.result().toList.mkString("\n")))
        }
        codeLines += line
        lineIndex += 1
      }

      Left(ParseError("Unclosed code fence", lineIndex, 1, startLine))
    }

    private def isThematicBreak(line: String): Boolean = {
      val trimmed = line.trim
      if (trimmed.length < 3) return false

      val chars = trimmed.filterNot(_ == ' ')
      if (chars.length < 3) return false

      val first = chars.head
      (first == '-' || first == '*' || first == '_') && chars.forall(_ == first)
    }

    private def parseBlockQuote(): Either[ParseError, BlockQuote] = {
      val quoteLines = Chunk.newBuilder[String]

      while (lineIndex < lines.length && currentLine.startsWith(">")) {
        val line    = currentLine
        val content = if (line.length > 1 && line(1) == ' ') line.drop(2) else line.drop(1)
        quoteLines += content
        lineIndex += 1
      }

      val innerInput = quoteLines.result().toList.mkString("\n")
      val innerState = new ParserState(innerInput)
      innerState.parseBlocks().map(blocks => BlockQuote(blocks))
    }

    private def isBulletListItem(line: String): Boolean = {
      val trimmed = line.dropWhile(_ == ' ')
      if (trimmed.isEmpty) return false
      val marker = trimmed.head
      (marker == '-' || marker == '*' || marker == '+') &&
      trimmed.length > 1 && trimmed(1) == ' '
    }

    private def isOrderedListItem(line: String): Boolean = {
      val trimmed = line.dropWhile(_ == ' ')
      val digits  = trimmed.takeWhile(_.isDigit)
      digits.nonEmpty && trimmed.length > digits.length &&
      trimmed(digits.length) == '.' &&
      trimmed.length > digits.length + 1 &&
      trimmed(digits.length + 1) == ' '
    }

    private def parseBulletList(): Either[ParseError, BulletList] = {
      val items = Chunk.newBuilder[ListItem]

      while (lineIndex < lines.length && isBulletListItem(currentLine)) {
        val line    = currentLine
        val trimmed = line.dropWhile(_ == ' ')
        val content = trimmed.drop(2)

        val (checked, itemContent) = parseTaskListMarker(content)

        lineIndex += 1

        val continuationLines = Chunk.newBuilder[String]
        continuationLines += itemContent

        while (lineIndex < lines.length && isListContinuation(currentLine)) {
          continuationLines += currentLine.dropWhile(_ == ' ')
          lineIndex += 1
        }

        val itemText    = continuationLines.result().toList.mkString("\n")
        val innerState  = new ParserState(itemText)
        val innerBlocks = innerState.parseBlocks().getOrElse(Chunk.empty)

        val finalBlocks =
          if (innerBlocks.isEmpty) Chunk(Paragraph(parseInlines(itemText)))
          else innerBlocks

        items += ListItem(finalBlocks, checked)
      }

      Right(BulletList(items.result(), tight = true))
    }

    private def parseTaskListMarker(content: String): (Option[Boolean], String) =
      if (content.startsWith("[ ] ")) {
        (Some(false), content.drop(4))
      } else if (content.startsWith("[x] ") || content.startsWith("[X] ")) {
        (Some(true), content.drop(4))
      } else {
        (None, content)
      }

    private def isListContinuation(line: String): Boolean = {
      val trimmed = line.dropWhile(_ == ' ')
      trimmed.nonEmpty &&
      !isBulletListItem(line) &&
      !isOrderedListItem(line) &&
      line.startsWith("  ")
    }

    private def parseOrderedList(): Either[ParseError, OrderedList] = {
      val items = Chunk.newBuilder[ListItem]
      var start = 1

      var first = true
      while (lineIndex < lines.length && isOrderedListItem(currentLine)) {
        val line    = currentLine
        val trimmed = line.dropWhile(_ == ' ')
        val digits  = trimmed.takeWhile(_.isDigit)
        val num     = digits.toInt

        if (first) {
          start = num
          first = false
        }

        val content = trimmed.drop(digits.length + 2)
        lineIndex += 1

        val continuationLines = Chunk.newBuilder[String]
        continuationLines += content

        while (lineIndex < lines.length && isListContinuation(currentLine)) {
          continuationLines += currentLine.dropWhile(_ == ' ')
          lineIndex += 1
        }

        val itemText    = continuationLines.result().toList.mkString("\n")
        val innerState  = new ParserState(itemText)
        val innerBlocks = innerState.parseBlocks().getOrElse(Chunk.empty)

        val finalBlocks =
          if (innerBlocks.isEmpty) Chunk(Paragraph(parseInlines(itemText)))
          else innerBlocks

        items += ListItem(finalBlocks, None)
      }

      Right(OrderedList(start, items.result(), tight = true))
    }

    private def isTableStart(): Boolean =
      if (lineIndex + 1 >= lines.length) false
      else {
        val line1 = currentLine
        val line2 = lines(lineIndex + 1)
        line1.contains("|") && isTableDelimiterRow(line2)
      }

    private def isTableDelimiterRow(line: String): Boolean = {
      val trimmed = line.trim
      if (!trimmed.contains("|")) return false

      val cells = splitTableRow(trimmed)
      cells.forall { cell =>
        val c = cell.trim
        c.matches(":?-{3,}:?")
      }
    }

    private def splitTableRow(line: String): Array[String] = {
      var l = line.trim
      if (l.startsWith("|")) l = l.drop(1)
      if (l.endsWith("|")) l = l.dropRight(1)
      l.split("\\|").map(_.trim)
    }

    private def parseTable(): Either[ParseError, Table] = {
      val headerLine    = currentLine
      val delimiterLine = lines(lineIndex + 1)

      val headerCells = splitTableRow(headerLine).map(cell => parseInlines(cell))
      val header      = TableRow(Chunk.fromIterable(headerCells.toSeq))

      val alignments = splitTableRow(delimiterLine).map { cell =>
        val c = cell.trim
        if (c.startsWith(":") && c.endsWith(":")) Alignment.Center
        else if (c.startsWith(":")) Alignment.Left
        else if (c.endsWith(":")) Alignment.Right
        else Alignment.None
      }

      lineIndex += 2

      val rows = Chunk.newBuilder[TableRow]
      while (lineIndex < lines.length && currentLine.contains("|")) {
        val rowCells = splitTableRow(currentLine).map(cell => parseInlines(cell))
        rows += TableRow(Chunk.fromIterable(rowCells.toSeq))
        lineIndex += 1
      }

      Right(Table(header, Chunk.fromIterable(alignments.toSeq), rows.result()))
    }

    private def isHtmlBlockStart(line: String): Boolean = {
      val trimmed = line.trim
      if (!trimmed.startsWith("<")) return false

      val closeAngle = trimmed.indexOf('>')
      if (closeAngle < 0) return true

      val tagContent = trimmed.substring(1, closeAngle)

      if (tagContent.contains("@") && !tagContent.contains(" ")) return false
      if (tagContent.startsWith("http://") || tagContent.startsWith("https://")) return false

      val tagName = tagContent.takeWhile(c => c.isLetter || c == '/' || c == '!')
      (tagName.nonEmpty && tagName.head.isLetter) || tagName.startsWith("/") || tagName.startsWith("!")
    }

    private def parseHtmlBlock(): Either[ParseError, HtmlBlock] = {
      val htmlLines = Chunk.newBuilder[String]

      while (lineIndex < lines.length) {
        val line = currentLine
        htmlLines += line
        lineIndex += 1

        if (line.contains("</") || line.endsWith("/>") || line.trim.isEmpty) {
          if (lineIndex < lines.length && lines(lineIndex).trim.isEmpty) {
            return Right(HtmlBlock(htmlLines.result().toList.mkString("\n")))
          }
          if (line.trim.isEmpty || !lines.lift(lineIndex).exists(_.startsWith("<"))) {
            return Right(HtmlBlock(htmlLines.result().toList.mkString("\n")))
          }
        }
      }

      Right(HtmlBlock(htmlLines.result().toList.mkString("\n")))
    }

    private def parseParagraph(): Either[ParseError, Paragraph] = {
      val paraLines = Chunk.newBuilder[String]

      while (lineIndex < lines.length) {
        val line = currentLine
        if (line.trim.isEmpty) {
          lineIndex += 1
          val content = paraLines.result().toList
          return Right(Paragraph(parseInlinesWithBreaks(content)))
        }
        if (
          line.startsWith("#") || line.startsWith("```") || line.startsWith("~~~") ||
          line.startsWith(">") || isBulletListItem(line) || isOrderedListItem(line) ||
          isThematicBreak(line) || (line.startsWith("<") && isHtmlBlockStart(line))
        ) {
          val content = paraLines.result().toList
          return Right(Paragraph(parseInlinesWithBreaks(content)))
        }
        paraLines += line
        lineIndex += 1
      }

      val content = paraLines.result().toList
      Right(Paragraph(parseInlinesWithBreaks(content)))
    }

    private def parseInlinesWithBreaks(lines: List[String]): Chunk[Inline] = {
      val result   = Chunk.newBuilder[Inline]
      var prevLine = ""
      var first    = true

      for (line <- lines) {
        if (!first) {
          if (prevLine.endsWith("  ") || prevLine.endsWith("\\")) {
            result += HardBreak
          } else {
            result += SoftBreak
          }
        }

        val cleanLine =
          if (line.endsWith("  ")) line.dropRight(2)
          else if (line.endsWith("\\")) line.dropRight(1)
          else line

        result ++= parseInlines(cleanLine)
        prevLine = line
        first = false
      }

      result.result()
    }

    private def parseInlines(text: String): Chunk[Inline] = {
      val result = Chunk.newBuilder[Inline]
      var pos    = 0

      while (pos < text.length) {
        val remaining = text.substring(pos)

        if (remaining.startsWith("***") || remaining.startsWith("___")) {
          val marker = remaining.head
          val endIdx = findMatchingDelimiter(remaining, marker.toString * 3, 3)
          if (endIdx > 3) {
            val inner = remaining.substring(3, endIdx)
            result += Strong(Chunk(Emphasis(parseInlines(inner))))
            pos += endIdx + 3
          } else {
            val endIdx2 = findMatchingDelimiter(remaining, marker.toString * 2, 2)
            if (endIdx2 > 2) {
              val inner = remaining.substring(2, endIdx2)
              result += Strong(parseInlines(inner))
              pos += endIdx2 + 2
            } else {
              result += Text(marker.toString)
              pos += 1
            }
          }
        } else if (remaining.startsWith("**") || remaining.startsWith("__")) {
          val marker    = remaining.substring(0, 2)
          val markerCh  = remaining.head
          val endIdx    = findMatchingDelimiter(remaining, marker, 2)
          val hasTriple = endIdx > 2 && endIdx + 2 < remaining.length && remaining(endIdx + 2) == markerCh
          if (hasTriple) {
            val inner = remaining.substring(2, endIdx + 1)
            result += Strong(parseInlines(inner))
            pos += endIdx + 3
          } else if (endIdx > 2) {
            val inner = remaining.substring(2, endIdx)
            result += Strong(parseInlines(inner))
            pos += endIdx + 2
          } else {
            result += Text(marker)
            pos += 2
          }
        } else if (remaining.startsWith("~~")) {
          val endIdx = remaining.indexOf("~~", 2)
          if (endIdx > 2) {
            val inner = remaining.substring(2, endIdx)
            result += Strikethrough(parseInlines(inner))
            pos += endIdx + 2
          } else {
            result += Text("~~")
            pos += 2
          }
        } else if (remaining.startsWith("*") || remaining.startsWith("_")) {
          val marker = remaining.head
          val endIdx = remaining.indexOf(marker, 1)
          if (endIdx > 1) {
            val inner = remaining.substring(1, endIdx)
            result += Emphasis(parseInlines(inner))
            pos += endIdx + 1
          } else {
            result += Text(marker.toString)
            pos += 1
          }
        } else if (remaining.startsWith("``")) {
          val backtickCount = remaining.takeWhile(_ == '`').length
          val afterOpening  = remaining.drop(backtickCount)
          val endIdx        = afterOpening.indexOf("`" * backtickCount)
          if (endIdx >= 0) {
            val code = afterOpening.substring(0, endIdx).trim
            result += Code(code)
            pos += backtickCount + endIdx + backtickCount
          } else {
            result += Text("`" * backtickCount)
            pos += backtickCount
          }
        } else if (remaining.startsWith("`")) {
          val endIdx = remaining.indexOf('`', 1)
          if (endIdx > 1) {
            val code = remaining.substring(1, endIdx)
            result += Code(code)
            pos += endIdx + 1
          } else {
            result += Text("`")
            pos += 1
          }
        } else if (remaining.startsWith("![")) {
          val closeBracket = remaining.indexOf(']', 2)
          if (closeBracket > 2 && remaining.length > closeBracket + 1 && remaining(closeBracket + 1) == '(') {
            val closeParen = remaining.indexOf(')', closeBracket + 2)
            if (closeParen > closeBracket + 2) {
              val alt          = remaining.substring(2, closeBracket)
              val urlPart      = remaining.substring(closeBracket + 2, closeParen)
              val (url, title) = parseUrlAndTitle(urlPart)
              result += Image(alt, url, title)
              pos += closeParen + 1
            } else {
              result += Text("!")
              pos += 1
            }
          } else {
            result += Text("!")
            pos += 1
          }
        } else if (remaining.startsWith("[")) {
          val closeBracket = remaining.indexOf(']', 1)
          if (closeBracket > 1 && remaining.length > closeBracket + 1 && remaining(closeBracket + 1) == '(') {
            val closeParen = remaining.indexOf(')', closeBracket + 2)
            if (closeParen > closeBracket + 2) {
              val linkText     = remaining.substring(1, closeBracket)
              val urlPart      = remaining.substring(closeBracket + 2, closeParen)
              val (url, title) = parseUrlAndTitle(urlPart)
              result += Link(parseInlines(linkText), url, title)
              pos += closeParen + 1
            } else {
              result += Text("[")
              pos += 1
            }
          } else {
            result += Text("[")
            pos += 1
          }
        } else if (remaining.startsWith("<")) {
          val closeAngle = remaining.indexOf('>', 1)
          if (closeAngle > 1) {
            val content = remaining.substring(1, closeAngle)
            if (content.contains("@") && !content.contains(" ")) {
              result += Autolink(content, isEmail = true)
              pos += closeAngle + 1
            } else if (content.startsWith("http://") || content.startsWith("https://")) {
              result += Autolink(content, isEmail = false)
              pos += closeAngle + 1
            } else {
              result += HtmlInline(remaining.substring(0, closeAngle + 1))
              pos += closeAngle + 1
            }
          } else {
            result += Text("<")
            pos += 1
          }
        } else if (remaining.startsWith("https://") || remaining.startsWith("http://")) {
          val urlEnd = remaining.indexWhere(c => c == ' ' || c == '\n' || c == '\t', 0)
          val url    = if (urlEnd > 0) remaining.substring(0, urlEnd) else remaining
          result += Autolink(url, isEmail = false)
          pos += url.length
        } else {
          val nextSpecial = findNextSpecial(remaining)
          if (nextSpecial > 0) {
            result += Text(remaining.substring(0, nextSpecial))
            pos += nextSpecial
          } else if (nextSpecial == 0) {
            result += Text(remaining.head.toString)
            pos += 1
          } else {
            result += Text(remaining)
            pos = text.length
          }
        }
      }

      mergeAdjacentText(result.result())
    }

    private def findMatchingDelimiter(text: String, delimiter: String, startOffset: Int): Int = {
      val idx = text.indexOf(delimiter, startOffset)
      if (idx > startOffset) idx else -1
    }

    private def parseUrlAndTitle(urlPart: String): (String, Option[String]) = {
      val trimmed  = urlPart.trim
      val quoteIdx = trimmed.indexOf('"')
      if (quoteIdx > 0) {
        val url      = trimmed.substring(0, quoteIdx).trim
        val titleEnd = trimmed.lastIndexOf('"')
        if (titleEnd > quoteIdx) {
          val title = trimmed.substring(quoteIdx + 1, titleEnd)
          (url, Some(title))
        } else {
          (trimmed, None)
        }
      } else {
        (trimmed, None)
      }
    }

    private def findNextSpecial(text: String): Int = {
      var i = 0
      while (i < text.length) {
        val c = text(i)
        if (c == '*' || c == '_' || c == '`' || c == '[' || c == '!' || c == '<' || c == '~') {
          return i
        }
        if (i + 6 < text.length && text.substring(i, i + 7) == "http://") {
          return i
        }
        if (i + 7 < text.length && text.substring(i, i + 8) == "https://") {
          return i
        }
        i += 1
      }
      -1
    }

    private def mergeAdjacentText(inlines: Chunk[Inline]): Chunk[Inline] = {
      if (inlines.isEmpty) return inlines

      val result                  = Chunk.newBuilder[Inline]
      var pending: Option[String] = None

      for (inline <- inlines) {
        inline match {
          case Text(value) =>
            pending = Some(pending.getOrElse("") + value)
          case other =>
            pending.foreach(t => result += Text(t))
            pending = None
            result += other
        }
      }

      pending.foreach(t => result += Text(t))
      result.result()
    }
  }
}
