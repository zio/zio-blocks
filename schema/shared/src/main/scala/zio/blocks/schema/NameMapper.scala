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

import java.lang
import java.lang.Character._

/**
 * Maps a Scala name, such as a field or case name, to the name a codec writes
 * and reads, such as `published_in` for `publishedIn`. Pass one to
 * `JsonCodecDeriver#withFieldNameMapper` or `#withCaseNameMapper`, or to the
 * options of the same names on other derivers. The strategies are in the
 * companion; wrap any other function in [[NameMapper.Custom]]. The built-in
 * strategies never throw, and their `toString` is `<function1>`.
 *
 * {{{
 * val snake  = NameMapper.SnakeCase("parseHTTPResponse") // parse_http_response
 * val kebab  = NameMapper.KebabCase("address2Line")      // address2-line
 * val camel  = NameMapper.CamelCase("published_in")      // publishedIn
 * val pascal = NameMapper.PascalCase("HTTPServer")       // HttpServer
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/codec/ Codec]]
 */
sealed trait NameMapper extends (String => String)

/**
 * The naming strategies: [[NameMapper.SnakeCase]], [[NameMapper.KebabCase]],
 * [[NameMapper.CamelCase]], [[NameMapper.PascalCase]], [[NameMapper.Identity]]
 * and [[NameMapper.Custom]], and `fromString`, which picks one by name.
 */
object NameMapper {
  private[this] val validStrategies = "identity, snake_case, camelCase, kebab-case, PascalCase"

  private[this] def enforceCamelOrPascalCase(s: String, toPascal: Boolean): String = {
    val len = s.length
    val sb  = new lang.StringBuilder(len)
    if (s.indexOf('_') < 0 && s.indexOf('-') < 0) {
      if (len > 0) {
        val firstChar = s.charAt(0)
        sb.append(
          if (toPascal) toUpperCase(firstChar)
          else toLowerCase(firstChar)
        )
        var idx = 0
        while (idx < len && isUpperCase(s.charAt(idx))) idx += 1
        if (idx > 1 && idx < len && isLowerCase(s.charAt(idx))) idx -= 1
        val limit = Math.max(idx, 1)
        idx = 1
        while (idx < limit) {
          sb.append(toLowerCase(s.charAt(idx)))
          idx += 1
        }
        while (idx < len) {
          sb.append(s.charAt(idx))
          idx += 1
        }
      }
    } else {
      var idx             = 0
      var isPrecedingDash = toPascal
      while (idx < len) isPrecedingDash = {
        val ch = s.charAt(idx)
        idx += 1
        (ch == '_' || ch == '-') || {
          val fixedCh =
            if (isPrecedingDash) toUpperCase(ch)
            else toLowerCase(ch)
          sb.append(fixedCh)
          false
        }
      }
    }
    sb.toString
  }

  private[this] def enforceSnakeOrKebabCase(s: String, separator: Char): String = {
    val len                      = s.length
    val sb                       = new lang.StringBuilder(len << 1)
    var idx                      = 0
    var isPrecedingNotUpperCased = false
    while (idx < len) isPrecedingNotUpperCased = {
      val ch = s.charAt(idx)
      idx += 1
      if (ch == '_' || ch == '-') {
        if (idx > 1 && idx < len && !isAlphabetic(s.charAt(idx))) isPrecedingNotUpperCased
        else {
          sb.append(separator)
          false
        }
      } else if (!isUpperCase(ch)) {
        sb.append(ch)
        true
      } else {
        if (isPrecedingNotUpperCased || idx > 1 && idx < len && isLowerCase(s.charAt(idx))) sb.append(separator)
        sb.append(toLowerCase(ch))
        false
      }
    }
    sb.toString
  }

  /**
   * Maps a name with the function `f`, such as
   * `NameMapper.Custom(_.toUpperCase)`. It throws whatever `f` throws.
   */
  case class Custom(f: String => String) extends NameMapper {
    override def apply(memberName: String): String = f(memberName)
  }

  /**
   * Converts a name to snake_case: lower-case words joined by `_`, as in
   * `publishedIn` to `published_in`.
   *
   * A word starts at a capital letter that follows any character other than a
   * capital, `_` or `-`, and at the last capital of a run of capitals that a
   * lower-case letter follows, so an acronym stays one word: `userID` becomes
   * `user_id` and `parseHTTPResponse` becomes `parse_http_response`. A digit
   * stays with the word before it: `address2Line` becomes `address2_line` and
   * `isbn13` stays `isbn13`. An existing `_` or `-` becomes `_`, except one
   * inside the name that a digit or symbol follows, which is dropped:
   * `version_2` becomes `version2`. A capital right after an existing `_` or
   * `-` gets a second separator when a lower-case letter follows it, so
   * `my_Field` becomes `my__field` (see `docs/dev/issues.md`).
   */
  case object SnakeCase extends NameMapper {
    override def apply(memberName: String): String = enforceSnakeOrKebabCase(memberName, '_')
  }

  /**
   * Converts a name to camelCase, as in `published_in` to `publishedIn`.
   *
   * A name that contains `_` or `-` is split there: the separators are dropped,
   * the first character after each one is upper-cased and every other character
   * is lower-cased, so `my_HTTPServer` becomes `myHttpserver`. A name without
   * separators keeps its characters, except that its first character and a run
   * of capitals at its start are lower-cased, apart from the last capital of
   * the run when a lower-case letter follows it: `HTTPServer` becomes
   * `httpServer`, `URL` becomes `url`, and `userID` stays `userID`.
   */
  case object CamelCase extends NameMapper {
    override def apply(memberName: String): String = enforceCamelOrPascalCase(memberName, toPascal = false)
  }

  /**
   * Converts a name to PascalCase, as in `published_in` to `PublishedIn`. It
   * works like [[CamelCase]], except that the first character is upper-cased:
   * `HTTPServer` becomes `HttpServer` and `URL` becomes `Url`.
   */
  case object PascalCase extends NameMapper {
    override def apply(memberName: String): String = enforceCamelOrPascalCase(memberName, toPascal = true)
  }

  /**
   * Converts a name to kebab-case: lower-case words joined by `-`, as in
   * `publishedIn` to `published-in`. It splits words like [[SnakeCase]], so
   * `parseHTTPResponse` becomes `parse-http-response`, `address2Line` becomes
   * `address2-line`, and `my_Field` becomes `my--field`.
   */
  case object KebabCase extends NameMapper {
    override def apply(memberName: String): String = enforceSnakeOrKebabCase(memberName, '-')
  }

  /**
   * Returns a name unchanged. It is the default field and case name mapper of
   * `JsonCodecDeriver`.
   */
  case object Identity extends NameMapper {
    override def apply(memberName: String): String = memberName
  }

  /**
   * Returns the built-in strategy named `s`: `identity`, `snake_case`,
   * `camelCase`, `kebab-case` or `PascalCase`, spelled exactly so.
   * `JsonCodecDeriver` resolves the strategy of `Modifier.fieldNaming` and
   * `Modifier.caseNaming` with it while deriving.
   *
   * @throws java.lang.IllegalArgumentException
   *   when `s` is any other string, such as `SnakeCase` or `pascalcase`.
   */
  def fromString(s: String): NameMapper = s match {
    case "identity"   => Identity
    case "snake_case" => SnakeCase
    case "camelCase"  => CamelCase
    case "kebab-case" => KebabCase
    case "PascalCase" => PascalCase
    case other        =>
      throw new IllegalArgumentException(
        s"Unknown naming strategy '$other'. Expected one of: $validStrategies"
      )
  }
}
