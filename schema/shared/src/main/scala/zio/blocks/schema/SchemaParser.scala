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

package zio.blocks.schema

import zio.blocks.chunk.Chunk

/**
 * Pure Scala parser for schema representation syntax.
 *
 * This parser converts a schema string (e.g., "record { name: string, age: int
 * }") into a SchemaRepr. Used by the path interpolator to parse schema searches
 * like `p"#Person"` or `p"#record { name: string }"`.
 */
private[schema] object SchemaParser {

  sealed trait ParseError {
    def message: String
    def position: Int
  }

  object ParseError {
    case class UnexpectedChar(char: Char, position: Int, expected: String) extends ParseError {
      def message: String = s"Unexpected character '$char' at position $position. Expected $expected"
    }

    case class InvalidIdentifier(position: Int) extends ParseError {
      def message: String = s"Invalid identifier at position $position"
    }

    case class UnexpectedEnd(expected: String) extends ParseError {
      def position: Int   = -1
      def message: String = s"Unexpected end of input. Expected $expected"
    }

    case class EmptyRecord(position: Int) extends ParseError {
      def message: String = s"Empty record at position $position. Records must have at least one field"
    }

    case class EmptyVariant(position: Int) extends ParseError {
      def message: String = s"Empty variant at position $position. Variants must have at least one case"
    }

    case class InvalidSyntax(msg: String, position: Int) extends ParseError {
      def message: String = s"$msg at position $position"
    }
  }

  // Set of primitive type names that are recognized as Primitive rather than Nominal
  private[this] val primitiveNames: Set[String] = Set(
    "string",
    "int",
    "long",
    "short",
    "byte",
    "float",
    "double",
    "boolean",
    "char",
    "unit",
    "bigint",
    "bigdecimal",
    "uuid",
    "currency",
    "instant",
    "localdate",
    "localtime",
    "localdatetime",
    "offsettime",
    "offsetdatetime",
    "zoneddatetime",
    "dayofweek",
    "month",
    "monthday",
    "year",
    "yearmonth",
    "period",
    "duration",
    "zoneoffset",
    "zoneid"
  )

  /**
   * Parse a schema string into a SchemaRepr. Returns Left with error if parsing
   * fails, Right with SchemaRepr on success.
   */
  def parse(input: String): Either[ParseError, SchemaRepr] = {
    val ctx = new ParseContext(input)
    ctx.skipWhitespace()
    if (ctx.atEnd) return new Left(new ParseError.UnexpectedEnd("schema expression"))
    val result = parseSchema(ctx)
    result.flatMap { repr =>
      ctx.skipWhitespace()
      if (ctx.atEnd) new Right(repr)
      else new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "end of input"))
    }
  }

  private class ParseContext(val input: String) {
    var pos: Int = 0

    def current: Char =
      if (pos < input.length) input.charAt(pos)
      else '\u0000'

    def atEnd: Boolean = pos >= input.length

    def advance(): Unit = pos += 1

    def peek(offset: Int = 1): Char = {
      val p = pos + offset
      if (p < input.length) input.charAt(p) else '\u0000'
    }

    def skipWhitespace(): Unit = while (!atEnd && current.isWhitespace) advance()
  }

  private[this] def parseSchema(ctx: ParseContext): Either[ParseError, SchemaRepr] = {
    ctx.skipWhitespace()
    if (ctx.atEnd) return new Left(new ParseError.UnexpectedEnd("schema expression"))
    ctx.current match {
      case '_' if !isIdentifierPart(ctx.peek()) =>
        // Standalone underscore is Wildcard (not followed by identifier chars)
        ctx.advance()
        new Right(SchemaRepr.Wildcard)
      case c =>
        if (isIdentifierStart(c)) parseIdentifierOrKeyword(ctx)
        else new Left(new ParseError.UnexpectedChar(c, ctx.pos, "identifier, keyword, or '_'"))
    }
  }

  private[this] def parseIdentifierOrKeyword(ctx: ParseContext): Either[ParseError, SchemaRepr] = {
    val start = ctx.pos
    val ident = parseIdentifier(ctx)
    if (ident.isEmpty) return new Left(new ParseError.InvalidIdentifier(start))
    ident match {
      case "record"                  => parseRecord(ctx)
      case "variant"                 => parseVariant(ctx)
      case "list" | "set" | "vector" => parseSequence(ctx)
      case "map"                     => parseMap(ctx)
      case "option"                  => parseOptional(ctx)
      case name                      =>
        new Right({
          if (isPrimitive(name)) SchemaRepr.Primitive(name)
          else SchemaRepr.Nominal(name)
        })
    }
  }

  private[this] def parseRecord(ctx: ParseContext): Either[ParseError, SchemaRepr] = {
    ctx.skipWhitespace()
    val bracePos = ctx.pos
    if (ctx.atEnd || ctx.current != '{')
      return new Left(
        if (ctx.atEnd) new ParseError.UnexpectedEnd("'{'")
        else new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'{'")
      )
    ctx.advance() // Skip '{'
    ctx.skipWhitespace()
    if (ctx.current == '}') return new Left(new ParseError.EmptyRecord(bracePos))
    val fields = Chunk.newBuilder[(String, SchemaRepr)]
    var first  = true
    while (!ctx.atEnd && ctx.current != '}') {
      if (!first) {
        if (ctx.current != ',') return new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "',' or '}'"))
        ctx.advance() // Skip ','
        ctx.skipWhitespace()
      }
      first = false
      parseField(ctx) match {
        case Right(field) => fields.addOne(field)
        case l            => return l.asInstanceOf[Either[ParseError, SchemaRepr]]
      }
      ctx.skipWhitespace()
    }
    if (ctx.atEnd) return new Left(new ParseError.UnexpectedEnd("'}'"))
    ctx.advance() // Skip '}'
    new Right(SchemaRepr.Record(fields.result()))
  }

  private[this] def parseVariant(ctx: ParseContext): Either[ParseError, SchemaRepr] = {
    ctx.skipWhitespace()
    val bracePos = ctx.pos
    if (ctx.atEnd || ctx.current != '{')
      return new Left(
        if (ctx.atEnd) new ParseError.UnexpectedEnd("'{'")
        else new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'{'")
      )
    ctx.advance() // Skip '{'
    ctx.skipWhitespace()
    if (ctx.current == '}') return new Left(new ParseError.EmptyVariant(bracePos))
    val cases = Chunk.newBuilder[(String, SchemaRepr)]
    var first = true
    while (!ctx.atEnd && ctx.current != '}') {
      if (!first) {
        if (ctx.current != ',')
          return new Left(new ParseError.UnexpectedChar(ctx.current, ctx.pos, "',' or '}'"))
        ctx.advance() // Skip ','
        ctx.skipWhitespace()
      }
      first = false
      parseField(ctx) match {
        case Right(field) => cases.addOne(field)
        case l            => return l.asInstanceOf[Either[ParseError, SchemaRepr]]
      }
      ctx.skipWhitespace()
    }
    if (ctx.atEnd) return new Left(new ParseError.UnexpectedEnd("'}'"))
    ctx.advance() // Skip '}'
    new Right(SchemaRepr.Variant(cases.result()))
  }

  private[this] def parseField(ctx: ParseContext): Either[ParseError, (String, SchemaRepr)] = {
    ctx.skipWhitespace()
    val nameStart = ctx.pos
    val name      = parseIdentifier(ctx)
    if (name.isEmpty) return new Left(new ParseError.InvalidIdentifier(nameStart))
    ctx.skipWhitespace()
    if (ctx.atEnd || ctx.current != ':') {
      return new Left(
        if (ctx.atEnd) new ParseError.UnexpectedEnd("':'")
        else new ParseError.UnexpectedChar(ctx.current, ctx.pos, "':'")
      )
    }
    ctx.advance() // Skip ':'
    ctx.skipWhitespace()
    parseSchema(ctx).map(schema => (name, schema))
  }

  private[this] def parseSequence(ctx: ParseContext): Either[ParseError, SchemaRepr] = {
    ctx.skipWhitespace()
    if (ctx.atEnd || ctx.current != '(')
      return new Left(
        if (ctx.atEnd) new ParseError.UnexpectedEnd("'('")
        else new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'('")
      )
    ctx.advance() // Skip '('
    ctx.skipWhitespace()
    parseSchema(ctx).flatMap { element =>
      ctx.skipWhitespace()
      if (ctx.atEnd || ctx.current != ')') {
        new Left(
          if (ctx.atEnd) new ParseError.UnexpectedEnd("')'")
          else new ParseError.UnexpectedChar(ctx.current, ctx.pos, "')'")
        )
      } else {
        ctx.advance() // Skip ')'
        new Right(SchemaRepr.Sequence(element))
      }
    }
  }

  private[this] def parseMap(ctx: ParseContext): Either[ParseError, SchemaRepr] = {
    ctx.skipWhitespace()
    if (ctx.atEnd || ctx.current != '(') {
      return new Left(
        if (ctx.atEnd) new ParseError.UnexpectedEnd("'('")
        else new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'('")
      )
    }
    ctx.advance() // Skip '('
    ctx.skipWhitespace()
    parseSchema(ctx) match {
      case Left(err)  => new Left(err)
      case Right(key) =>
        ctx.skipWhitespace()
        if (ctx.atEnd || ctx.current != ',') {
          new Left(
            if (ctx.atEnd) new ParseError.UnexpectedEnd("','")
            else new ParseError.UnexpectedChar(ctx.current, ctx.pos, "','")
          )
        } else {
          ctx.advance() // Skip ','
          ctx.skipWhitespace()
          parseSchema(ctx) match {
            case Left(err)    => new Left(err)
            case Right(value) =>
              ctx.skipWhitespace()
              if (ctx.atEnd || ctx.current != ')') {
                new Left(
                  if (ctx.atEnd) new ParseError.UnexpectedEnd("')'")
                  else new ParseError.UnexpectedChar(ctx.current, ctx.pos, "')'")
                )
              } else {
                ctx.advance() // Skip ')'
                new Right(SchemaRepr.Map(key, value))
              }
          }
        }
    }
  }

  private[this] def parseOptional(ctx: ParseContext): Either[ParseError, SchemaRepr] = {
    ctx.skipWhitespace()
    if (ctx.atEnd || ctx.current != '(') {
      return new Left(
        if (ctx.atEnd) new ParseError.UnexpectedEnd("'('")
        else new ParseError.UnexpectedChar(ctx.current, ctx.pos, "'('")
      )
    }
    ctx.advance() // Skip '('
    ctx.skipWhitespace()
    parseSchema(ctx).flatMap { inner =>
      ctx.skipWhitespace()
      if (ctx.atEnd || ctx.current != ')') {
        new Left(
          if (ctx.atEnd) new ParseError.UnexpectedEnd("')'")
          else new ParseError.UnexpectedChar(ctx.current, ctx.pos, "')'")
        )
      } else {
        ctx.advance() // Skip ')'
        new Right(SchemaRepr.Optional(inner))
      }
    }
  }

  private[this] def parseIdentifier(ctx: ParseContext): String = {
    val sb = new java.lang.StringBuilder
    while (!ctx.atEnd && isIdentifierPart(ctx.current)) {
      sb.append(ctx.current)
      ctx.advance()
    }
    sb.toString
  }

  private[this] def isPrimitive(name: String): Boolean = primitiveNames.contains(name.toLowerCase)

  private[this] def isIdentifierStart(c: Char): Boolean = c == '_' || Character.isLetter(c)

  private[this] def isIdentifierPart(c: Char): Boolean = c == '_' || Character.isLetterOrDigit(c)
}
