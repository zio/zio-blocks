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
 * Base type for all configuration errors.
 *
 * Errors are partitioned into category traits for structured matching:
 *   - [[ConfigParseError]] — value could not be parsed into the expected type
 *   - [[ConfigValidationError]] — value parsed but failed semantic validation
 *   - [[ConfigSourceError]] — problem with the configuration source itself
 *   - [[ConfigDerivationError]] — error during schema-driven config derivation
 */
sealed trait ConfigError extends NoStackTrace {
  def message: String
  override def getMessage: String = message
}

/** Category: value could not be parsed into the expected type. */
sealed trait ConfigParseError extends ConfigError

/** Category: value was parsed but failed semantic validation. */
sealed trait ConfigValidationError extends ConfigError

/**
 * Category: problem with the configuration source (missing key, unauthorized,
 * duplicate).
 */
sealed trait ConfigSourceError extends ConfigError

/** Category: error during schema-driven config derivation. */
sealed trait ConfigDerivationError extends ConfigError

object ConfigError {

  case class UnknownDiscriminator(path: String, found: String, expected: Seq[String]) extends ConfigDerivationError {
    def message: String =
      s"Unknown discriminator value '$found' at '$path'; expected one of: ${expected.mkString(", ")}"
  }

  case class MissingDiscriminatorKey(path: String, key: String) extends ConfigDerivationError {
    def message: String = s"Missing discriminator key '$key' at '$path'"
  }

  /**
   * A required key was not present in the configuration source.
   *
   * @param path
   *   dot-separated key path that was looked up
   * @param source
   *   identifier of the source that was searched
   */
  case class MissingKey(path: String, source: String) extends ConfigSourceError {
    def message: String = s"Missing required key '$path' in source '$source'"
  }

  /**
   * A key was present but its value could not be converted to the expected
   * type.
   *
   * @param path
   *   dot-separated key path
   * @param value
   *   the raw string value that failed to parse
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
      val base = s"Invalid value '$value' for key '$path' (expected $expectedType) in source '$source'"
      cause match {
        case Some(t) => s"$base: ${t.getMessage}"
        case None    => base
      }
    }
  }

  /**
   * The same key appeared in multiple conflicting sources.
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
   * Multiple errors accumulated during decoding.
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
   * Access to the key was denied by the configuration source.
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
   * The raw value for a key could not be decoded at the format level (e.g.
   * malformed JSON, invalid base64), distinct from a type-mismatch
   * [[InvalidValue]].
   *
   * @param path
   *   dot-separated key path
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
      cause match {
        case Some(t) => s"$base: ${t.getMessage}"
        case None    => base
      }
    }
  }
}
