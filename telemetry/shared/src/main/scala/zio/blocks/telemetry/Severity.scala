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

package zio.blocks.telemetry

/**
 * Represents the severity/log level of a log record with 24 levels grouped in 6
 * categories: Trace (1-4), Debug (5-8), Info (9-12), Warn (13-16), Error
 * (17-20), Fatal (21-24).
 *
 * Each severity level has a numeric value and a text representation.
 */
sealed trait Severity {
  def number: Int
  def text: String
}

object Severity {

  // Trace levels (1-4)
  case object Trace extends Severity {
    def number: Int  = 1
    def text: String = "TRACE"
  }

  case object Trace2 extends Severity {
    def number: Int  = 2
    def text: String = "TRACE"
  }

  case object Trace3 extends Severity {
    def number: Int  = 3
    def text: String = "TRACE"
  }

  case object Trace4 extends Severity {
    def number: Int  = 4
    def text: String = "TRACE"
  }

  // Debug levels (5-8)
  case object Debug extends Severity {
    def number: Int  = 5
    def text: String = "DEBUG"
  }

  case object Debug2 extends Severity {
    def number: Int  = 6
    def text: String = "DEBUG"
  }

  case object Debug3 extends Severity {
    def number: Int  = 7
    def text: String = "DEBUG"
  }

  case object Debug4 extends Severity {
    def number: Int  = 8
    def text: String = "DEBUG"
  }

  // Info levels (9-12)
  case object Info extends Severity {
    def number: Int  = 9
    def text: String = "INFO"
  }

  case object Info2 extends Severity {
    def number: Int  = 10
    def text: String = "INFO"
  }

  case object Info3 extends Severity {
    def number: Int  = 11
    def text: String = "INFO"
  }

  case object Info4 extends Severity {
    def number: Int  = 12
    def text: String = "INFO"
  }

  // Warn levels (13-16)
  case object Warn extends Severity {
    def number: Int  = 13
    def text: String = "WARN"
  }

  case object Warn2 extends Severity {
    def number: Int  = 14
    def text: String = "WARN"
  }

  case object Warn3 extends Severity {
    def number: Int  = 15
    def text: String = "WARN"
  }

  case object Warn4 extends Severity {
    def number: Int  = 16
    def text: String = "WARN"
  }

  // Error levels (17-20)
  case object Error extends Severity {
    def number: Int  = 17
    def text: String = "ERROR"
  }

  case object Error2 extends Severity {
    def number: Int  = 18
    def text: String = "ERROR"
  }

  case object Error3 extends Severity {
    def number: Int  = 19
    def text: String = "ERROR"
  }

  case object Error4 extends Severity {
    def number: Int  = 20
    def text: String = "ERROR"
  }

  // Fatal levels (21-24)
  case object Fatal extends Severity {
    def number: Int  = 21
    def text: String = "FATAL"
  }

  case object Fatal2 extends Severity {
    def number: Int  = 22
    def text: String = "FATAL"
  }

  case object Fatal3 extends Severity {
    def number: Int  = 23
    def text: String = "FATAL"
  }

  case object Fatal4 extends Severity {
    def number: Int  = 24
    def text: String = "FATAL"
  }

  /**
   * Returns the severity level for the given numeric value (1-24).
   *
   * Returns None if the number is not in the valid range.
   */
  def fromNumber(n: Int): Option[Severity] = n match {
    case 1  => Some(Trace)
    case 2  => Some(Trace2)
    case 3  => Some(Trace3)
    case 4  => Some(Trace4)
    case 5  => Some(Debug)
    case 6  => Some(Debug2)
    case 7  => Some(Debug3)
    case 8  => Some(Debug4)
    case 9  => Some(Info)
    case 10 => Some(Info2)
    case 11 => Some(Info3)
    case 12 => Some(Info4)
    case 13 => Some(Warn)
    case 14 => Some(Warn2)
    case 15 => Some(Warn3)
    case 16 => Some(Warn4)
    case 17 => Some(Error)
    case 18 => Some(Error2)
    case 19 => Some(Error3)
    case 20 => Some(Error4)
    case 21 => Some(Fatal)
    case 22 => Some(Fatal2)
    case 23 => Some(Fatal3)
    case 24 => Some(Fatal4)
    case _  => None
  }

  /**
   * Returns the severity level for the given text (case-insensitive).
   *
   * Valid texts are: TRACE, DEBUG, INFO, WARN, ERROR, FATAL. Returns None if
   * the text does not match any severity level.
   */
  def fromText(s: String): Option[Severity] =
    s.toUpperCase match {
      case "TRACE" => Some(Trace)
      case "DEBUG" => Some(Debug)
      case "INFO"  => Some(Info)
      case "WARN"  => Some(Warn)
      case "ERROR" => Some(Error)
      case "FATAL" => Some(Fatal)
      case _       => None
    }
}
