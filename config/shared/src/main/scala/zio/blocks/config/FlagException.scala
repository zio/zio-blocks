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
 * Base class for all flag-related exceptions.
 *
 * Extends [[scala.util.control.NoStackTrace]] so that instances are lightweight
 * and do not capture a stack trace, which is appropriate for flag resolution
 * errors that are diagnosed at startup rather than during request handling.
 *
 * Concrete subtypes live in [[FlagException$ FlagException companion]]:
 *   - [[FlagException.FlagValueParseException]] — flag value could not be
 *     parsed
 *   - [[FlagException.FlagNameException]] — flag name is structurally invalid
 *   - [[FlagException.FlagDuplicateNameException]] — flag name already
 *     registered
 *   - [[FlagException.FlagExpressionParseException]] — rollout expression is
 *     malformed
 */
sealed abstract class FlagException extends Exception with NoStackTrace {
  override def getMessage: String
}

object FlagException {

  /**
   * Thrown when a raw flag value string cannot be parsed into the expected
   * type.
   *
   * Thrown by [[StaticFlag]] during object initialisation when the value
   * supplied via a [[FlagSource]], system property, or environment variable
   * cannot be parsed by the flag's [[Flag.Reader]].
   *
   * @param flagName
   *   the name of the flag whose value failed to parse
   * @param rawValue
   *   the raw string that was provided
   * @param expectedType
   *   human-readable name of the type that was expected
   * @param cause
   *   optional underlying parse exception
   */
  final case class FlagValueParseException(
    flagName: String,
    rawValue: String,
    expectedType: String,
    cause: Option[Throwable] = None
  ) extends FlagException {
    override def getMessage: String = {
      val base = s"Failed to parse value '$rawValue' for flag '$flagName' (expected $expectedType)"
      cause.fold(base)(t => s"$base: ${t.getMessage}")
    }
  }

  /**
   * Thrown when a flag name fails structural validation (e.g. the defining
   * class is not a Scala object, or is a lambda / anonymous class).
   *
   * Thrown by [[StaticFlag]] and [[DynamicFlag]] during object initialisation
   * when the runtime class name does not conform to the expected Scala object
   * naming convention.
   *
   * @param flagName
   *   the class name that was rejected
   * @param details
   *   human-readable explanation of the validation failure
   */
  final case class FlagNameException(flagName: String, details: String) extends FlagException {
    override def getMessage: String = s"Invalid flag name '$flagName': $details"
  }

  /**
   * Thrown when a flag name is registered more than once in the global
   * registry.
   *
   * Thrown by [[StaticFlag]] and [[DynamicFlag]] when a second object with an
   * identical derived name attempts to register itself.
   *
   * @param flagName
   *   the duplicated flag name
   */
  final case class FlagDuplicateNameException(flagName: String) extends FlagException {
    override def getMessage: String = s"Duplicate flag name '$flagName'"
  }

  /**
   * Thrown when the rollout expression associated with a named flag cannot be
   * parsed.
   *
   * Thrown by [[DynamicFlag]] during object initialisation when the initial
   * rollout expression (from a [[FlagSource]], system property, environment
   * variable, or constructor default) is malformed.
   *
   * @param flagName
   *   the name of the flag whose expression is malformed
   * @param expression
   *   the raw expression string that could not be parsed
   * @param details
   *   human-readable description of the parse failure
   */
  final case class FlagExpressionParseException(
    flagName: String,
    expression: String,
    details: String
  ) extends FlagException {
    override def getMessage: String =
      s"Invalid rollout expression '$expression' for flag '$flagName': $details"
  }
}
