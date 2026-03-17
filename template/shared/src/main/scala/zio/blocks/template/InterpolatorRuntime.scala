package zio.blocks.template

import zio.blocks.chunk.Chunk

private[template] object InterpolatorRuntime {

  def buildCss(sc: StringContext, args: Seq[String]): Css = {
    require(args.length == sc.parts.length - 1, "wrong number of arguments for interpolation")
    val sb        = new java.lang.StringBuilder
    val partsIter = sc.parts.iterator
    val argsIter  = args.iterator
    while (partsIter.hasNext) {
      sb.append(partsIter.next())
      if (argsIter.hasNext) sb.append(argsIter.next())
    }
    Css.Raw(sb.toString)
  }

  def buildSelector(sc: StringContext, args: Seq[String]): CssSelector = {
    require(args.length == sc.parts.length - 1, "wrong number of arguments for interpolation")
    val sb        = new java.lang.StringBuilder
    val partsIter = sc.parts.iterator
    val argsIter  = args.iterator
    while (partsIter.hasNext) {
      sb.append(partsIter.next())
      if (argsIter.hasNext) sb.append(argsIter.next())
    }
    CssSelector.Raw(sb.toString)
  }

  def buildJs(sc: StringContext, args: Seq[String]): Js = {
    require(args.length == sc.parts.length - 1, "wrong number of arguments for interpolation")
    val sb        = new java.lang.StringBuilder
    val partsIter = sc.parts.iterator
    val argsIter  = args.iterator
    while (partsIter.hasNext) {
      sb.append(partsIter.next())
      if (argsIter.hasNext) sb.append(argsIter.next())
    }
    Js(sb.toString)
  }

  def buildHtml(sc: StringContext, args: Seq[Either[String, Chunk[Dom]]]): Dom = {
    require(args.length == sc.parts.length - 1, "wrong number of arguments for interpolation")
    val sb    = new java.lang.StringBuilder
    val parts = sc.parts
    var i     = 0
    while (i < parts.length) {
      sb.append(parts(i))
      if (i < args.length) {
        args(i) match {
          case Left(attrValue) =>
            sb.append(attrValue)
          case Right(_) =>
            sb.append(SentinelStr)
            sb.append(i)
            sb.append(SentinelStr)
        }
      }
      i += 1
    }
    val contentArgs = args.zipWithIndex.collect { case (Right(chunk), idx) => (idx, chunk) }.toMap
    val parsed      = parseHtml(sb.toString)
    val substituted = substituteContentArgs(parsed, contentArgs)
    if (substituted.isEmpty) Dom.Empty
    else if (substituted.length == 1) substituted(0)
    else {
      val builder = Chunk.newBuilder[Dom]
      var j       = 0
      while (j < substituted.length) {
        builder += substituted(j)
        j += 1
      }
      Dom.Element.Generic("span", Chunk.empty, builder.result())
    }
  }

  // Reserved sentinel unlikely to appear in user content; if present, behavior is undefined
  private final val SentinelStr  = "\u0000\u0001"
  private final val SentinelChar = '\u0000'

  private val voidElements: Set[String] = Dom.voidElements

  private val rawContentElements: Set[String] = Set("script", "style")

  def parseHtml(input: String): Chunk[Dom] = {
    val len    = input.length
    val result = Chunk.newBuilder[Dom]
    val stack  = new java.util.ArrayDeque[StackFrame]()
    var pos    = 0

    while (pos < len) {
      val ch = input.charAt(pos)
      if (ch == '<' && pos + 1 < len) {
        val next = input.charAt(pos + 1)
        if (next == '/') {
          val gtPos = indexOf(input, '>', pos + 2)
          if (gtPos < 0) {
            addText(result, stack, input.substring(pos))
            pos = len
          } else {
            val tagName = input.substring(pos + 2, gtPos).trim.toLowerCase
            popToTag(stack, result, tagName)
            pos = gtPos + 1
          }
        } else if (next == '!' || next == '?') {
          val gtPos = indexOf(input, '>', pos + 2)
          if (gtPos < 0) {
            addText(result, stack, input.substring(pos))
            pos = len
          } else {
            pos = gtPos + 1
          }
        } else {
          val tagParseResult = parseOpenTag(input, pos)
          if (tagParseResult == null) {
            addText(result, stack, "<")
            pos += 1
          } else {
            val tagName   = tagParseResult.tagName
            val attrs     = tagParseResult.attributes
            val selfClose = tagParseResult.selfClosing
            val endPos    = tagParseResult.endPos

            if (selfClose || voidElements.contains(tagName)) {
              val elem = createElement(tagName, attrs, Chunk.empty)
              addChild(result, stack, elem)
              pos = endPos
            } else if (rawContentElements.contains(tagName)) {
              val closeTag   = "</" + tagName + ">"
              val contentEnd = indexOfIgnoreCase(input, closeTag, endPos)
              if (contentEnd < 0) {
                val rawContent = input.substring(endPos)
                val children   = textChunk(rawContent)
                val elem       = createElement(tagName, attrs, children)
                addChild(result, stack, elem)
                pos = len
              } else {
                val rawContent = input.substring(endPos, contentEnd)
                val children   = textChunk(rawContent)
                val elem       = createElement(tagName, attrs, children)
                addChild(result, stack, elem)
                pos = contentEnd + closeTag.length
              }
            } else {
              stack.push(new StackFrame(tagName, attrs))
              pos = endPos
            }
          }
        }
      } else if (ch == SentinelChar && pos + 1 < len && input.charAt(pos + 1) == '\u0001') {
        // Sentinel marker for interpolation: \u0000\u0001INDEX\u0000\u0001
        val endSentinel = input.indexOf(SentinelStr, pos + 2)
        if (endSentinel < 0) {
          addText(result, stack, input.substring(pos))
          pos = len
        } else {
          val indexStr = input.substring(pos + 2, endSentinel)
          // Store sentinel in Dom.Text for later substitution
          addText(result, stack, SentinelStr + indexStr + SentinelStr)
          pos = endSentinel + 2
        }
      } else {
        val textEnd = nextSpecialChar(input, pos + 1)
        val text    = input.substring(pos, textEnd)
        addText(result, stack, text)
        pos = textEnd
      }
    }

    while (!stack.isEmpty) {
      val frame    = stack.pop()
      val children = frame.childrenResult()
      val elem     = createElement(frame.tagName, frame.attributes, children)
      addChild(result, stack, elem)
    }

    result.result()
  }

  private def substituteContentArgs(nodes: Chunk[Dom], args: Map[Int, Chunk[Dom]]): Chunk[Dom] = {
    if (args.isEmpty) return nodes
    val result = Chunk.newBuilder[Dom]
    var i      = 0
    while (i < nodes.length) {
      nodes(i) match {
        case Dom.Text(content) if content.contains(SentinelStr) =>
          result ++= splitAndSubstituteContent(content, args)
        case el: Dom.Element =>
          val newChildren = substituteContentArgs(el.children, args)
          result += el.withChildren(newChildren)
        case other =>
          result += other
      }
      i += 1
    }
    result.result()
  }

  private def splitAndSubstituteContent(text: String, args: Map[Int, Chunk[Dom]]): Chunk[Dom] = {
    val result   = Chunk.newBuilder[Dom]
    val segments = text.split(SentinelStr, -1)
    var i        = 0
    while (i < segments.length) {
      if (i % 2 == 0) {
        if (segments(i).nonEmpty) result += Dom.Text(segments(i))
      } else {
        try {
          val argIdx = java.lang.Integer.parseInt(segments(i))
          args.get(argIdx) match {
            case Some(chunk) => result ++= chunk
            case None        => result += Dom.Text(segments(i))
          }
        } catch {
          case _: NumberFormatException =>
            result += Dom.Text(segments(i))
        }
      }
      i += 1
    }
    result.result()
  }

  private final class StackFrame(
    val tagName: String,
    val attributes: Chunk[Dom.Attribute]
  ) {
    private val children = Chunk.newBuilder[Dom]

    def addChild(child: Dom): Unit = children += child

    def childrenResult(): Chunk[Dom] = children.result()
  }

  private final class TagParseResult(
    val tagName: String,
    val attributes: Chunk[Dom.Attribute],
    val selfClosing: Boolean,
    val endPos: Int
  )

  private def createElement(tag: String, attrs: Chunk[Dom.Attribute], children: Chunk[Dom]): Dom.Element =
    if (tag == "script") Dom.Element.Script(attrs, children)
    else if (tag == "style") Dom.Element.Style(attrs, children)
    else Dom.Element.Generic(tag, attrs, children)

  private def textChunk(text: String): Chunk[Dom] =
    if (text.isEmpty) Chunk.empty
    else Chunk(Dom.Text(text))

  private def addChild(
    result: scala.collection.mutable.Builder[Dom, Chunk[Dom]],
    stack: java.util.ArrayDeque[StackFrame],
    child: Dom
  ): Unit =
    if (stack.isEmpty) result += child
    else stack.peek().addChild(child)

  private def addText(
    result: scala.collection.mutable.Builder[Dom, Chunk[Dom]],
    stack: java.util.ArrayDeque[StackFrame],
    text: String
  ): Unit =
    if (text.nonEmpty) addChild(result, stack, Dom.Text(text))

  private def popToTag(
    stack: java.util.ArrayDeque[StackFrame],
    result: scala.collection.mutable.Builder[Dom, Chunk[Dom]],
    tagName: String
  ): Unit = {
    var found = false
    val iter  = stack.iterator()
    while (iter.hasNext && !found) {
      if (iter.next().tagName == tagName) found = true
    }
    if (!found) return
    var done = false
    while (!done && !stack.isEmpty) {
      val frame    = stack.pop()
      val children = frame.childrenResult()
      val elem     = createElement(frame.tagName, frame.attributes, children)
      if (frame.tagName == tagName) {
        addChild(result, stack, elem)
        done = true
      } else {
        addChild(result, stack, elem)
      }
    }
  }

  private def parseOpenTag(input: String, pos: Int): TagParseResult = {
    val len = input.length
    var i   = pos + 1
    while (i < len && input.charAt(i) == ' ') i += 1
    val tagStart = i
    while (i < len) {
      val c = input.charAt(i)
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>' || c == '/') {
        return parseOpenTagFrom(input, tagStart, i)
      }
      i += 1
    }
    if (i > tagStart) parseOpenTagFrom(input, tagStart, i)
    else null
  }

  private def parseOpenTagFrom(input: String, tagStart: Int, tagEnd: Int): TagParseResult = {
    val len     = input.length
    val tagName = input.substring(tagStart, tagEnd).toLowerCase
    if (tagName.isEmpty) return null

    val firstChar = tagName.charAt(0)
    if (!((firstChar >= 'a' && firstChar <= 'z') || (firstChar >= 'A' && firstChar <= 'Z'))) return null

    val attrs       = Chunk.newBuilder[Dom.Attribute]
    var i           = tagEnd
    var selfClosing = false

    while (i < len) {
      val c = input.charAt(i)
      if (c == '>') {
        return new TagParseResult(tagName, attrs.result(), selfClosing, i + 1)
      } else if (c == '/') {
        selfClosing = true
        i += 1
      } else if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        i += 1
      } else {
        val attrNameStart = i
        while (i < len) {
          val ac = input.charAt(i)
          if (ac == '=' || ac == ' ' || ac == '\t' || ac == '\n' || ac == '\r' || ac == '>' || ac == '/') {
            return parseAttrContinue(input, tagName, attrs, attrNameStart, i, selfClosing)
          }
          i += 1
        }
        if (i > attrNameStart) {
          attrs += Dom.Attribute.BooleanAttribute(input.substring(attrNameStart, i).toLowerCase)
        }
        return new TagParseResult(tagName, attrs.result(), selfClosing, i)
      }
    }

    new TagParseResult(tagName, attrs.result(), selfClosing, i)
  }

  private def parseAttrContinue(
    input: String,
    tagName: String,
    attrs: scala.collection.mutable.Builder[Dom.Attribute, Chunk[Dom.Attribute]],
    attrNameStart: Int,
    attrNameEnd: Int,
    initialSelfClosing: Boolean
  ): TagParseResult = {
    val len         = input.length
    val attrName    = input.substring(attrNameStart, attrNameEnd).toLowerCase
    var i           = attrNameEnd
    var selfClosing = initialSelfClosing

    while (i < len && isWhitespace(input.charAt(i))) i += 1

    if (i < len && input.charAt(i) == '=') {
      i += 1
      while (i < len && isWhitespace(input.charAt(i))) i += 1

      if (i < len) {
        val quoteChar = input.charAt(i)
        if (quoteChar == '"' || quoteChar == '\'') {
          i += 1
          val valueStart = i
          while (i < len && input.charAt(i) != quoteChar) i += 1
          val value = input.substring(valueStart, i)
          if (i < len) i += 1
          attrs += Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value))
        } else {
          val valueStart = i
          while (i < len && !isWhitespace(input.charAt(i)) && input.charAt(i) != '>' && input.charAt(i) != '/') i += 1
          val value = input.substring(valueStart, i)
          attrs += Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value))
        }
      } else {
        attrs += Dom.Attribute.BooleanAttribute(attrName)
      }
    } else {
      attrs += Dom.Attribute.BooleanAttribute(attrName)
    }

    while (i < len) {
      val c = input.charAt(i)
      if (c == '>') {
        return new TagParseResult(tagName, attrs.result(), selfClosing, i + 1)
      } else if (c == '/') {
        selfClosing = true
        i += 1
      } else if (isWhitespace(c)) {
        i += 1
      } else {
        val nextAttrNameStart = i
        while (i < len) {
          val ac = input.charAt(i)
          if (ac == '=' || isWhitespace(ac) || ac == '>' || ac == '/') {
            return parseAttrContinue(input, tagName, attrs, nextAttrNameStart, i, selfClosing)
          }
          i += 1
        }
        if (i > nextAttrNameStart) {
          attrs += Dom.Attribute.BooleanAttribute(input.substring(nextAttrNameStart, i).toLowerCase)
        }
        return new TagParseResult(tagName, attrs.result(), selfClosing, i)
      }
    }

    new TagParseResult(tagName, attrs.result(), selfClosing, i)
  }

  private def isWhitespace(c: Char): Boolean =
    c == ' ' || c == '\t' || c == '\n' || c == '\r'

  private def indexOf(s: String, ch: Char, from: Int): Int = {
    var i   = from
    val len = s.length
    while (i < len) {
      if (s.charAt(i) == ch) return i
      i += 1
    }
    -1
  }

  private def indexOfIgnoreCase(s: String, target: String, from: Int): Int = {
    val sLen = s.length
    val tLen = target.length
    if (tLen == 0) return from
    val limit = sLen - tLen
    var i     = from
    while (i <= limit) {
      var j      = 0
      var match_ = true
      while (j < tLen && match_) {
        val sc = java.lang.Character.toLowerCase(s.charAt(i + j))
        val tc = java.lang.Character.toLowerCase(target.charAt(j))
        if (sc != tc) match_ = false
        j += 1
      }
      if (match_) return i
      i += 1
    }
    -1
  }

  private def nextSpecialChar(input: String, from: Int): Int = {
    var i   = from
    val len = input.length
    while (i < len) {
      val c = input.charAt(i)
      if (c == '<' || c == SentinelChar) return i
      i += 1
    }
    len
  }
}
