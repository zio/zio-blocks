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

package zio.blocks.config.hocon

/**
 * A hand-written, zero-dependency HOCON parser suitable for JVM and Scala.js.
 *
 * Two-pass approach:
 *   1. Parse text into a raw AST that may contain unresolved substitutions.
 *   2. Resolve substitutions in a second pass, detecting circular references.
 */
object HoconParser {

  /**
   * Parse a HOCON string and return the resolved AST.
   *
   * @param includeCallback
   *   Optional callback for `include "file"` directives. Returns the contents
   *   of the included resource as a string. Default: no includes supported.
   */
  def parse(
    input: String,
    includeCallback: String => Option[String] = _ => None
  ): Either[HoconError, HoconValue] = {
    val normalized = input.replace("\r\n", "\n").replace("\r", "\n")
    val lexer      = new Lexer(normalized)
    try {
      val raw = lexer.parseRoot(includeCallback)
      resolve(raw).left.map { msg =>
        HoconError(msg, 0, 0)
      }
    } catch {
      case e: HoconError => Left(e)
    }
  }

  // ── Internal AST that may contain unresolved substitutions ──────────

  private sealed trait RawValue
  private case class RawObj(fields: Seq[(String, RawValue, Boolean)])    extends RawValue // Boolean = isAppend (+=)
  private case class RawArr(elements: Seq[RawValue])                     extends RawValue
  private case class RawStr(value: String)                               extends RawValue
  private case class RawNum(value: Double)                               extends RawValue
  private case class RawBool(value: Boolean)                             extends RawValue
  private case object RawNull                                            extends RawValue
  private case class RawSubst(path: String, optional: Boolean)           extends RawValue
  private case class RawConcat(parts: Seq[RawValue])                     extends RawValue

  // ── Lexer / Parser (pass 1) ────────────────────────────────────────

  private class Lexer(input: String) {
    private val len: Int = input.length
    private var pos: Int = 0

    private def ch: Char   = if (pos < len) input.charAt(pos) else '\u0000'
    private def eof: Boolean = pos >= len

    private def lineCol: (Int, Int) = {
      var line = 1
      var col  = 1
      var i    = 0
      while (i < pos && i < len) {
        if (input.charAt(i) == '\n') { line += 1; col = 1 }
        else col += 1
        i += 1
      }
      (line, col)
    }

    private def error(msg: String): Nothing = {
      val (l, c) = lineCol
      throw HoconError(msg, l, c)
    }

    // ── Whitespace / comment helpers ──────────────────────────────────

    private def skipWhitespaceAndComments(): Unit = {
      while (!eof) {
        val c = ch
        if (c == ' ' || c == '\t' || c == '\n') { pos += 1 }
        else if (c == '#') skipToEndOfLine()
        else if (c == '/' && pos + 1 < len && input.charAt(pos + 1) == '/') skipToEndOfLine()
        else return
      }
    }

    private def skipToEndOfLine(): Unit = {
      while (!eof && ch != '\n') pos += 1
      if (!eof) pos += 1 // skip the \n
    }

    private def skipComma(): Unit = {
      skipWhitespaceAndComments()
      if (!eof && ch == ',') pos += 1
    }

    // ── Root parsing ─────────────────────────────────────────────────

    def parseRoot(includeCallback: String => Option[String]): RawValue = {
      skipWhitespaceAndComments()
      if (eof) return RawObj(Seq.empty)
      if (ch == '{') parseObject(includeCallback)
      else {
        // Root braces optional: treat as object fields
        val fields = parseObjectFields(includeCallback)
        RawObj(fields)
      }
    }

    // ── Object ───────────────────────────────────────────────────────

    private def parseObject(includeCallback: String => Option[String]): RawObj = {
      expect('{')
      val fields = parseObjectFields(includeCallback)
      skipWhitespaceAndComments()
      expect('}')
      RawObj(fields)
    }

    private def parseObjectFields(
      includeCallback: String => Option[String]
    ): Seq[(String, RawValue, Boolean)] = {
      val buf = new scala.collection.mutable.ArrayBuffer[(String, RawValue, Boolean)]()
      while (true) {
        skipWhitespaceAndComments()
        if (eof || ch == '}') return buf.toSeq
        if (isInclude) {
          handleInclude(includeCallback, buf)
          skipComma()
        } else {
          val key = parseKey()
          skipWhitespaceAndComments()
          if (eof) error("Unexpected end of input after key")

          if (ch == '{') {
            // Key followed by { means nested object without separator
            val value = parseObject(includeCallback)
            buf += ((key, value, false))
          } else if (ch == '+' && pos + 1 < len && input.charAt(pos + 1) == '=') {
            pos += 2 // skip +=
            skipWhitespaceAndComments()
            val value = parseValue(includeCallback)
            buf += ((key, value, true))
          } else {
            if (ch == '=' || ch == ':') pos += 1
            else error(s"Expected '=', ':', or '{' after key '$key', got '${ch}'")
            skipWhitespaceAndComments()
            val value = parseValue(includeCallback)
            buf += ((key, value, false))
          }
          skipComma()
        }
      }
      buf.toSeq // unreachable but needed for compiler
    }

    // ── Key parsing ──────────────────────────────────────────────────

    private def parseKey(): String = {
      if (ch == '"') parseQuotedString()
      else parseUnquotedKey()
    }

    private def parseUnquotedKey(): String = {
      val sb  = new java.lang.StringBuilder()
      while (!eof && !isKeySeparator(ch) && ch != '{' && ch != '}' && ch != '[' && ch != ']' &&
             ch != ',' && ch != '\n' && ch != '#') {
        if (ch == '/' && pos + 1 < len && input.charAt(pos + 1) == '/') {
          // start of // comment — stop here
          val result = sb.toString.trim
          if (result.isEmpty) error("Empty key")
          return result
        }
        sb.append(ch)
        pos += 1
      }
      val result = sb.toString.trim
      if (result.isEmpty) error("Empty key")
      result
    }

    private def isKeySeparator(c: Char): Boolean = c == '=' || c == ':' || c == '+' // += handled by caller

    // ── Value parsing ────────────────────────────────────────────────

    private def parseValue(includeCallback: String => Option[String]): RawValue = {
      skipWhitespaceAndComments()
      if (eof) error("Unexpected end of input, expected a value")
      val first = parseSingleValue(includeCallback)
      // Value concatenation: if immediately followed by more non-separator content on the same line, concat
      val parts = new scala.collection.mutable.ArrayBuffer[RawValue]()
      parts += first
      while (!eof && !isValueTerminator) {
        val before = pos
        val next   = parseSingleValue(includeCallback)
        if (pos == before) {
          // no progress, break
          return if (parts.size == 1) parts(0) else RawConcat(parts.toSeq)
        }
        parts += next
      }
      if (parts.size == 1) parts(0) else RawConcat(parts.toSeq)
    }

    private def isValueTerminator: Boolean = {
      val c = ch
      c == '\n' || c == ',' || c == '}' || c == ']' || c == '#' ||
      (c == '/' && pos + 1 < len && input.charAt(pos + 1) == '/')
    }

    private def parseSingleValue(includeCallback: String => Option[String]): RawValue = {
      skipInlineWhitespace()
      if (eof) error("Unexpected end of input, expected a value")
      val c = ch
      if (c == '{') parseObject(includeCallback)
      else if (c == '[') parseArray(includeCallback)
      else if (c == '"') {
        if (pos + 2 < len && input.charAt(pos + 1) == '"' && input.charAt(pos + 2) == '"')
          RawStr(parseTripleQuotedString())
        else RawStr(parseQuotedString())
      } else if (c == '$' && pos + 1 < len && input.charAt(pos + 1) == '{') parseSubstitution()
      else parseUnquotedValue()
    }

    private def skipInlineWhitespace(): Unit =
      while (!eof && (ch == ' ' || ch == '\t')) pos += 1

    // ── Array ────────────────────────────────────────────────────────

    private def parseArray(includeCallback: String => Option[String]): RawArr = {
      expect('[')
      val buf = new scala.collection.mutable.ArrayBuffer[RawValue]()
      while (true) {
        skipWhitespaceAndComments()
        if (eof) error("Unexpected end of input in array")
        if (ch == ']') { pos += 1; return RawArr(buf.toSeq) }
        buf += parseValue(includeCallback)
        skipComma()
      }
      RawArr(buf.toSeq) // unreachable
    }

    // ── Strings ──────────────────────────────────────────────────────

    private def parseQuotedString(): String = {
      expect('"')
      val sb = new java.lang.StringBuilder()
      while (!eof) {
        val c = ch
        if (c == '\\') {
          pos += 1
          if (eof) error("Unexpected end of input in string escape")
          val escaped = ch
          pos += 1
          escaped match {
            case 'n'  => sb.append('\n')
            case 't'  => sb.append('\t')
            case 'r'  => sb.append('\r')
            case '\\' => sb.append('\\')
            case '"'  => sb.append('"')
            case '/'  => sb.append('/')
            case 'b'  => sb.append('\b')
            case 'f'  => sb.append('\f')
            case 'u'  =>
              if (pos + 4 > len) error("Invalid unicode escape")
              val hex = input.substring(pos, pos + 4)
              try {
                sb.append(Integer.parseInt(hex, 16).toChar)
                pos += 4
              } catch { case _: NumberFormatException => error(s"Invalid unicode escape: \\u$hex") }
            case other => sb.append('\\').append(other)
          }
        } else if (c == '"') {
          pos += 1
          return sb.toString
        } else {
          sb.append(c)
          pos += 1
        }
      }
      error("Unterminated quoted string")
    }

    private def parseTripleQuotedString(): String = {
      pos += 3 // skip opening """
      val sb = new java.lang.StringBuilder()
      while (!eof) {
        if (ch == '"' && pos + 2 < len && input.charAt(pos + 1) == '"' && input.charAt(pos + 2) == '"') {
          pos += 3
          return sb.toString
        }
        sb.append(ch)
        pos += 1
      }
      error("Unterminated triple-quoted string")
    }

    // ── Unquoted values ──────────────────────────────────────────────

    private def parseUnquotedValue(): RawValue = {
      val sb = new java.lang.StringBuilder()
      while (!eof) {
        val c = ch
        if (c == '\n' || c == ',' || c == '}' || c == ']' || c == '#' || c == '{' || c == '[' || c == '"') {
          val result = sb.toString.trim
          if (result.isEmpty) return RawStr("")
          return interpretUnquoted(result)
        }
        if (c == '/' && pos + 1 < len && input.charAt(pos + 1) == '/') {
          val result = sb.toString.trim
          return interpretUnquoted(result)
        }
        if (c == '$' && pos + 1 < len && input.charAt(pos + 1) == '{') {
          val result = sb.toString.trim
          if (result.isEmpty) return parseSubstitution()
          // We have text before a substitution — return what we have
          return interpretUnquoted(result)
        }
        sb.append(c)
        pos += 1
      }
      val result = sb.toString.trim
      if (result.isEmpty) RawStr("")
      else interpretUnquoted(result)
    }

    private def interpretUnquoted(s: String): RawValue = {
      if (s == "true") RawBool(true)
      else if (s == "false") RawBool(false)
      else if (s == "null") RawNull
      else {
        // Try number
        try {
          if (s.contains('.') || s.contains('e') || s.contains('E')) {
            val d = java.lang.Double.parseDouble(s)
            if (!d.isNaN && !d.isInfinite) return RawNum(d)
          } else {
            val l = java.lang.Long.parseLong(s)
            return RawNum(l.toDouble)
          }
        } catch { case _: NumberFormatException => () }
        RawStr(s)
      }
    }

    // ── Substitutions ────────────────────────────────────────────────

    private def parseSubstitution(): RawValue = {
      pos += 2 // skip ${
      val optional = if (!eof && ch == '?') { pos += 1; true } else false
      val sb       = new java.lang.StringBuilder()
      while (!eof && ch != '}') {
        sb.append(ch)
        pos += 1
      }
      if (eof) error("Unterminated substitution ${...}")
      pos += 1 // skip }
      val path = sb.toString.trim
      if (path.isEmpty) error("Empty substitution path")
      RawSubst(path, optional)
    }

    // ── Include ──────────────────────────────────────────────────────

    private def isInclude: Boolean = {
      if (pos + 7 > len) false
      else input.substring(pos, pos + 7) == "include" && {
        val after = if (pos + 7 < len) input.charAt(pos + 7) else ' '
        after == ' ' || after == '"' || after == '\t'
      }
    }

    private def handleInclude(
      includeCallback: String => Option[String],
      buf: scala.collection.mutable.ArrayBuffer[(String, RawValue, Boolean)]
    ): Unit = {
      pos += 7 // skip "include"
      skipInlineWhitespace()
      if (eof || ch != '"') error("Expected quoted string after 'include'")
      val resource = parseQuotedString()
      includeCallback(resource) match {
        case Some(content) =>
          val parsed = new Lexer(content.replace("\r\n", "\n").replace("\r", "\n"))
            .parseRoot(includeCallback)
          parsed match {
            case RawObj(fields) => buf ++= fields
            case _              => error(s"Included resource '$resource' must be an object")
          }
        case None => () // silently ignore missing includes
      }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private def expect(expected: Char): Unit = {
      if (eof || ch != expected) error(s"Expected '$expected', got '${if (eof) "EOF" else ch.toString}'")
      pos += 1
    }
  }

  // ── Pass 2: Build HoconValue from raw AST, resolving substitutions ──

  private def resolve(raw: RawValue): Either[String, HoconValue] = {
    // First, build the full unresolved tree (merging objects, handling +=)
    val merged = buildMerged(raw)
    // Then resolve substitutions
    resolveSubstitutions(merged, merged, Set.empty)
  }

  /**
   * Build a HoconValue from the raw AST, performing object merging and
   * += array appends, but leaving substitutions as sentinel Subst nodes.
   */
  private sealed trait MergedValue
  private case class MObj(fields: Map[String, MergedValue])    extends MergedValue
  private case class MArr(elements: Seq[MergedValue])          extends MergedValue
  private case class MStr(value: String)                       extends MergedValue
  private case class MNum(value: Double)                       extends MergedValue
  private case class MBool(value: Boolean)                     extends MergedValue
  private case object MNull                                    extends MergedValue
  private case class MSubst(path: String, optional: Boolean)   extends MergedValue
  private case class MConcat(parts: Seq[MergedValue])          extends MergedValue

  private def buildMerged(raw: RawValue): MergedValue = raw match {
    case RawObj(fields) =>
      var result = Map.empty[String, MergedValue]
      fields.foreach { case (key, value, isAppend) =>
        val built = buildMerged(value)
        // Handle key path syntax: a.b.c = val -> nested objects
        val pathParts = splitKeyPath(key)
        val nested    = wrapInPath(pathParts, built, isAppend, result)
        result = deepMergeMaps(result, nested)
      }
      MObj(result)

    case RawArr(elements) => MArr(elements.map(buildMerged))
    case RawStr(v)        => MStr(v)
    case RawNum(v)        => MNum(v)
    case RawBool(v)       => MBool(v)
    case RawNull          => MNull
    case RawSubst(p, o)   => MSubst(p, o)
    case RawConcat(parts) =>
      val built = parts.map(buildMerged)
      MConcat(built)
  }

  private def splitKeyPath(key: String): Seq[String] = {
    // Split on unquoted dots. Quoted keys preserve dots inside.
    val parts = new scala.collection.mutable.ArrayBuffer[String]()
    val sb    = new java.lang.StringBuilder()
    var i     = 0
    var inQ   = false
    while (i < key.length) {
      val c = key.charAt(i)
      if (c == '"') { inQ = !inQ; i += 1 }
      else if (c == '.' && !inQ) {
        parts += sb.toString
        sb.setLength(0)
        i += 1
      } else {
        sb.append(c)
        i += 1
      }
    }
    parts += sb.toString
    parts.toSeq
  }

  private def wrapInPath(
    parts: Seq[String],
    value: MergedValue,
    isAppend: Boolean,
    existingRoot: Map[String, MergedValue]
  ): Map[String, MergedValue] =
    if (parts.isEmpty) Map.empty
    else if (parts.size == 1) {
      val key = parts.head
      if (isAppend) {
        existingRoot.get(key) match {
          case Some(MArr(existing)) =>
            value match {
              case MArr(newElements) => Map(key -> MArr(existing ++ newElements))
              case _                 => Map(key -> MArr(existing :+ value))
            }
          case Some(_)              =>
            value match {
              case MArr(newElements) => Map(key -> MArr(newElements))
              case _                 => Map(key -> MArr(Seq(value)))
            }
          case None                 =>
            value match {
              case MArr(newElements) => Map(key -> MArr(newElements))
              case _                 => Map(key -> MArr(Seq(value)))
            }
        }
      } else Map(key -> value)
    } else {
      val key  = parts.head
      val rest = parts.tail
      val inner = existingRoot.get(key) match {
        case Some(MObj(m)) => wrapInPath(rest, value, isAppend, m)
        case _             => wrapInPath(rest, value, isAppend, Map.empty)
      }
      Map(key -> MObj(inner))
    }

  private def deepMergeMaps(
    left: Map[String, MergedValue],
    right: Map[String, MergedValue]
  ): Map[String, MergedValue] =
    right.foldLeft(left) { case (acc, (k, rv)) =>
      acc.get(k) match {
        case Some(MObj(lf)) =>
          rv match {
            case MObj(rf) => acc.updated(k, MObj(deepMergeMaps(lf, rf)))
            case _        => acc.updated(k, rv)
          }
        case _              => acc.updated(k, rv)
      }
    }

  // ── Substitution resolution ────────────────────────────────────────

  private def resolveSubstitutions(
    value: MergedValue,
    root: MergedValue,
    resolving: Set[String]
  ): Either[String, HoconValue] =
    value match {
      case MObj(fields) =>
        var result = Map.empty[String, HoconValue]
        val it     = fields.iterator
        while (it.hasNext) {
          val (k, v) = it.next()
          resolveSubstitutions(v, root, resolving) match {
            case Right(resolved) => result = result.updated(k, resolved)
            case Left(err)       => return Left(err)
          }
        }
        Right(HoconValue.Obj(result))

      case MArr(elements) =>
        var result = Seq.empty[HoconValue]
        val it     = elements.iterator
        while (it.hasNext) {
          resolveSubstitutions(it.next(), root, resolving) match {
            case Right(resolved) => result = result :+ resolved
            case Left(err)       => return Left(err)
          }
        }
        Right(HoconValue.Arr(result))

      case MStr(v)  => Right(HoconValue.Str(v))
      case MNum(v)  => Right(HoconValue.Num(v))
      case MBool(v) => Right(HoconValue.Bool(v))
      case MNull    => Right(HoconValue.Null)

      case MSubst(path, optional) =>
        if (resolving.contains(path))
          Left(s"Circular substitution detected for path: $path")
        else {
          lookupPath(root, path) match {
            case Some(found) =>
              resolveSubstitutions(found, root, resolving + path)
            case None         =>
              if (optional) Right(HoconValue.Null)
              else Left(s"Unresolved substitution: $${$path}")
          }
        }

      case MConcat(parts) =>
        // Resolve all parts then concatenate as strings
        val sb = new java.lang.StringBuilder()
        val it = parts.iterator
        while (it.hasNext) {
          resolveSubstitutions(it.next(), root, resolving) match {
            case Right(v) => sb.append(hoconValueToString(v))
            case Left(e)  => return Left(e)
          }
        }
        Right(HoconValue.Str(sb.toString))
    }

  private def hoconValueToString(v: HoconValue): String = v match {
    case HoconValue.Str(s)  => s
    case HoconValue.Num(n)  => if (n == n.toLong && !n.isInfinite) n.toLong.toString else n.toString
    case HoconValue.Bool(b) => b.toString
    case HoconValue.Null    => "null"
    case _                  => "" // objects/arrays don't stringify well in concat
  }

  private def lookupPath(root: MergedValue, path: String): Option[MergedValue] = {
    val parts = splitKeyPath(path)
    var current: MergedValue = root
    var i = 0
    while (i < parts.length) {
      current match {
        case MObj(fields) =>
          fields.get(parts(i)) match {
            case Some(v) => current = v
            case None    => return None
          }
        case _            => return None
      }
      i += 1
    }
    Some(current)
  }
}
