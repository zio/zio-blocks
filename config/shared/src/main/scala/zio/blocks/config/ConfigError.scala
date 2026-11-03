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

package zio.blocks.config

import scala.util.control.NoStackTrace

/**
 * A configuration error. Decoders return these in a non-empty list, and
 * `ConfigSource.fromHocon`, `fromJson`, `fromYaml`, `Rollout.parseChoices` and
 * the flag readers return one. It is a `Throwable` without a stack trace whose
 * `getMessage` is `message`.
 *
 * Match on a case class for one kind of error, or on a category trait for a
 * group:
 *   - [[ConfigParseError]]: a value or a document that cannot be parsed
 *     ([[ConfigError.InvalidValue]], [[ConfigError.ParseError]])
 *   - [[ConfigSourceError]]: a key the source cannot supply
 *     ([[ConfigError.MissingKey]], [[ConfigError.Unauthorized]],
 *     [[ConfigError.DuplicateKey]])
 *   - [[ConfigDerivationError]]: a sealed trait whose case cannot be chosen
 *     ([[ConfigError.MissingDiscriminatorKey]],
 *     [[ConfigError.UnknownDiscriminator]])
 *   - [[ConfigValidationError]]: no error in this module has this category
 *
 * [[ConfigError.Composite]], which wraps the errors of one record, has no
 * category.
 *
 * {{{
 * import zio.blocks.schema.Schema
 *
 * final case class Http(host: String, port: Int)
 * object Http { implicit val schema: Schema[Http] = Schema.derived }
 *
 * val result = Config.load[Http](ConfigSource.fromMap(Map("host" -> "0.0.0.0", "port" -> "80a"), "env"))
 * val advice = result match {
 *   case Left(::(e: ConfigParseError, _))  => "fix the value: " + e.message
 *   case Left(::(e: ConfigSourceError, _)) => "add the key: " + e.message
 *   case Left(errors)                      => errors.map(_.message).mkString("\n")
 *   case Right(http)                       => "loaded " + http
 * }
 * // fix the value: Invalid value '80a' for key 'port' (expected Int) in source 'env': For input string: "80a"
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/config/errors/ Errors]]
 */
sealed trait ConfigError extends NoStackTrace {
  def message: String
  override def getMessage: String = message
}

/**
 * Category of errors for a value that cannot be parsed into the expected type,
 * or a document that cannot be parsed at all.
 */
sealed trait ConfigParseError extends ConfigError

/**
 * Category for a value that parses but fails a semantic check. No error in this
 * module has this category.
 */
sealed trait ConfigValidationError extends ConfigError

/**
 * Category of errors for a key the source cannot supply: missing, unauthorized
 * or defined twice.
 */
sealed trait ConfigSourceError extends ConfigError

/**
 * Category of errors raised while decoding a sealed trait, when the
 * discriminator key is missing or names no known case.
 */
sealed trait ConfigDerivationError extends ConfigError

/** The [[ConfigError]] cases. */
object ConfigError {

  /**
   * The discriminator key of a sealed trait names no known case.
   *
   * @param path
   *   the full discriminator key, such as `storage.type`
   * @param found
   *   the case name that was read
   * @param expected
   *   the names of the sealed trait's cases, sorted
   */
  case class UnknownDiscriminator(path: String, found: String, expected: Seq[String]) extends ConfigDerivationError {
    def message: String =
      s"Unknown discriminator value '$found' at '$path'; expected one of: ${expected.mkString(", ")}"
  }

  /**
   * The discriminator key of a sealed trait is missing, so its case cannot be
   * chosen.
   *
   * @param path
   *   the path of the sealed trait, such as `storage`
   * @param key
   *   the discriminator key that was looked up under `path`
   */
  case class MissingDiscriminatorKey(path: String, key: String) extends ConfigDerivationError {
    def message: String = s"Missing discriminator key '$key' at '$path'"
  }

  /**
   * A required key has no value in the source: the field has no default and is
   * not an `Option`.
   *
   * @param path
   *   dot-separated key path that was looked up
   * @param source
   *   `sourceId` of the source that was searched; for a source built with
   *   `orElse`, the combined id such as `env|defaults`
   */
  case class MissingKey(path: String, source: String) extends ConfigSourceError {
    def message: String = s"Missing required key '$path' in source '$source'"
  }

  /**
   * A value could not be converted to the expected type. Decoders raise it for
   * a field, and it also reports a malformed rollout expression (key `rollout`)
   * and a flag value its [[Flag.Reader]] rejects (source `flag`).
   *
   * When `path` looks sensitive (in lower case it contains `password`,
   * `secret`, `token` or a similar marker), `message` shows the value as
   * `<secret>` and leaves out the cause.
   *
   * @param path
   *   dot-separated key path
   * @param value
   *   the value that failed to convert, as a string
   * @param expectedType
   *   human-readable name of the expected type
   * @param source
   *   identifier of the source that provided the value
   * @param cause
   *   optional underlying parse exception
   */
  case class InvalidValue(
    path: String,
    value: String,
    expectedType: String,
    source: String,
    cause: Option[Throwable] = None
  ) extends ConfigParseError {
    def message: String = {
      val isSensitive = Sensitive.isSensitive(path)
      val display     = if (isSensitive) "<secret>" else value
      val base        = s"Invalid value '$display' for key '$path' (expected $expectedType) in source '$source'"
      if (isSensitive) base
      else
        cause match {
          case Some(t) => s"$base: ${t.getMessage}"
          case None    => base
        }
    }
  }

  /**
   * The same key appeared in multiple conflicting sources. No source in this
   * module raises it; it is there for custom sources.
   *
   * @param path
   *   dot-separated key path
   * @param sources
   *   identifiers of all sources that defined the key
   */
  case class DuplicateKey(path: String, sources: Seq[String]) extends ConfigSourceError {
    def message: String = s"Duplicate key '$path' found in conflicting sources: ${sources.mkString(", ")}"
  }

  /**
   * Several errors of one record. A derived record decoder returns one
   * `Composite` when two or more of its fields fail. Its `message` puts each
   * error's message on its own line.
   *
   * @param errors
   *   non-empty list of individual errors
   */
  case class Composite(errors: ::[ConfigError]) extends ConfigError {
    def message: String = {
      val lines = errors.toList.map(e => e.message)
      lines.mkString("\n")
    }
  }

  /**
   * Access to the key was denied by the configuration source. No source in this
   * module raises it; it is there for custom sources.
   *
   * @param path
   *   dot-separated key path that was requested
   * @param source
   *   identifier of the source that denied access
   */
  case class Unauthorized(path: String, source: String) extends ConfigSourceError {
    def message: String = s"Unauthorized access to key '$path' in source '$source'"
  }

  /**
   * A document or file could not be read or parsed, as opposed to a single
   * value of the wrong type ([[InvalidValue]]).
   *
   * `ConfigSource.fromHocon`, `fromJson` and `fromYaml` raise it with an empty
   * `path` when the text does not parse. `ConfigSource.fromFile` raises it with
   * the file's path when the file cannot be read, and with an empty `path` when
   * its text does not parse. When `path` is empty, `message` leaves out the
   * cause, so the line and column of a HOCON syntax error are only in `cause`,
   * a known bug.
   *
   * @param path
   *   the key or file path, or empty for a whole document
   * @param source
   *   identifier of the source that provided the value
   * @param expectedType
   *   human-readable name of the expected type or format
   * @param cause
   *   optional underlying parse exception
   */
  case class ParseError(
    path: String,
    source: String,
    expectedType: String,
    cause: Option[Throwable] = None
  ) extends ConfigParseError {
    def message: String = {
      val base = s"Parse error for key '$path' (expected $expectedType) in source '$source'"
      if (path.isEmpty) base
      else
        cause match {
          case Some(t) => s"$base: ${t.getMessage}"
          case None    => base
        }
    }
  }
}
