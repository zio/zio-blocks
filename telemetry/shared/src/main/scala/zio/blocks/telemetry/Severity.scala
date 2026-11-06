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

import java.util.Locale

/**
 * The level of a log record, on the 24-step scale of the OpenTelemetry log
 * data model: Trace (1 to 4), Debug (5 to 8), Info (9 to 12), Warn (13 to 16),
 * Error (17 to 20) and Fatal (21 to 24).
 *
 * The six named levels (`Trace`, `Debug`, `Info`, `Warn`, `Error`, `Fatal`) are
 * the first of each group; the numbered ones (`Warn2` and so on) are finer
 * steps. A higher `number` is more severe, and `log.setMinSeverity` drops the
 * calls below a level. All four levels of a group share its `text`.
 *
 * {{{
 * Severity.fromText("warn") // Some(Warn)
 * Severity.fromNumber(18) // Some(Error2)
 * Severity.fromNumber(25) // None
 * Severity.Warn2.number // 14
 * Severity.Warn2.text // WARN
 *
 * val floor = Severity.fromText(sys.env.getOrElse("LOG_LEVEL", "info")).getOrElse(Severity.Info)
 * log.setMinSeverity(floor)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/logging/severity/ Severity]]
 */
sealed trait Severity {

  /** Returns the level's position on the 1 to 24 scale. */
  def number: Int

  /**
   * Returns the name of the level's group in upper case: `"TRACE"`, `"DEBUG"`,
   * `"INFO"`, `"WARN"`, `"ERROR"` or `"FATAL"`.
   */
  def text: String
}

/** The 24 severity levels, and parsers for a level's number or text. */
object Severity {

  // Trace levels (1-4)
  /** Severity 1, the first TRACE level. */
  case object Trace extends Severity {
    /** Returns 1. */
    def number: Int  = 1
    /** Returns `"TRACE"`. */
    def text: String = "TRACE"
  }

  /** Severity 2, TRACE level 2 of 4. */
  case object Trace2 extends Severity {
    /** Returns 2. */
    def number: Int  = 2
    /** Returns `"TRACE"`. */
    def text: String = "TRACE"
  }

  /** Severity 3, TRACE level 3 of 4. */
  case object Trace3 extends Severity {
    /** Returns 3. */
    def number: Int  = 3
    /** Returns `"TRACE"`. */
    def text: String = "TRACE"
  }

  /** Severity 4, TRACE level 4 of 4. */
  case object Trace4 extends Severity {
    /** Returns 4. */
    def number: Int  = 4
    /** Returns `"TRACE"`. */
    def text: String = "TRACE"
  }

  // Debug levels (5-8)
  /** Severity 5, the first DEBUG level. */
  case object Debug extends Severity {
    /** Returns 5. */
    def number: Int  = 5
    /** Returns `"DEBUG"`. */
    def text: String = "DEBUG"
  }

  /** Severity 6, DEBUG level 2 of 4. */
  case object Debug2 extends Severity {
    /** Returns 6. */
    def number: Int  = 6
    /** Returns `"DEBUG"`. */
    def text: String = "DEBUG"
  }

  /** Severity 7, DEBUG level 3 of 4. */
  case object Debug3 extends Severity {
    /** Returns 7. */
    def number: Int  = 7
    /** Returns `"DEBUG"`. */
    def text: String = "DEBUG"
  }

  /** Severity 8, DEBUG level 4 of 4. */
  case object Debug4 extends Severity {
    /** Returns 8. */
    def number: Int  = 8
    /** Returns `"DEBUG"`. */
    def text: String = "DEBUG"
  }

  // Info levels (9-12)
  /** Severity 9, the first INFO level. */
  case object Info extends Severity {
    /** Returns 9. */
    def number: Int  = 9
    /** Returns `"INFO"`. */
    def text: String = "INFO"
  }

  /** Severity 10, INFO level 2 of 4. */
  case object Info2 extends Severity {
    /** Returns 10. */
    def number: Int  = 10
    /** Returns `"INFO"`. */
    def text: String = "INFO"
  }

  /** Severity 11, INFO level 3 of 4. */
  case object Info3 extends Severity {
    /** Returns 11. */
    def number: Int  = 11
    /** Returns `"INFO"`. */
    def text: String = "INFO"
  }

  /** Severity 12, INFO level 4 of 4. */
  case object Info4 extends Severity {
    /** Returns 12. */
    def number: Int  = 12
    /** Returns `"INFO"`. */
    def text: String = "INFO"
  }

  // Warn levels (13-16)
  /** Severity 13, the first WARN level. */
  case object Warn extends Severity {
    /** Returns 13. */
    def number: Int  = 13
    /** Returns `"WARN"`. */
    def text: String = "WARN"
  }

  /** Severity 14, WARN level 2 of 4. */
  case object Warn2 extends Severity {
    /** Returns 14. */
    def number: Int  = 14
    /** Returns `"WARN"`. */
    def text: String = "WARN"
  }

  /** Severity 15, WARN level 3 of 4. */
  case object Warn3 extends Severity {
    /** Returns 15. */
    def number: Int  = 15
    /** Returns `"WARN"`. */
    def text: String = "WARN"
  }

  /** Severity 16, WARN level 4 of 4. */
  case object Warn4 extends Severity {
    /** Returns 16. */
    def number: Int  = 16
    /** Returns `"WARN"`. */
    def text: String = "WARN"
  }

  // Error levels (17-20)
  /** Severity 17, the first ERROR level. */
  case object Error extends Severity {
    /** Returns 17. */
    def number: Int  = 17
    /** Returns `"ERROR"`. */
    def text: String = "ERROR"
  }

  /** Severity 18, ERROR level 2 of 4. */
  case object Error2 extends Severity {
    /** Returns 18. */
    def number: Int  = 18
    /** Returns `"ERROR"`. */
    def text: String = "ERROR"
  }

  /** Severity 19, ERROR level 3 of 4. */
  case object Error3 extends Severity {
    /** Returns 19. */
    def number: Int  = 19
    /** Returns `"ERROR"`. */
    def text: String = "ERROR"
  }

  /** Severity 20, ERROR level 4 of 4. */
  case object Error4 extends Severity {
    /** Returns 20. */
    def number: Int  = 20
    /** Returns `"ERROR"`. */
    def text: String = "ERROR"
  }

  // Fatal levels (21-24)
  /** Severity 21, the first FATAL level. */
  case object Fatal extends Severity {
    /** Returns 21. */
    def number: Int  = 21
    /** Returns `"FATAL"`. */
    def text: String = "FATAL"
  }

  /** Severity 22, FATAL level 2 of 4. */
  case object Fatal2 extends Severity {
    /** Returns 22. */
    def number: Int  = 22
    /** Returns `"FATAL"`. */
    def text: String = "FATAL"
  }

  /** Severity 23, FATAL level 3 of 4. */
  case object Fatal3 extends Severity {
    /** Returns 23. */
    def number: Int  = 23
    /** Returns `"FATAL"`. */
    def text: String = "FATAL"
  }

  /** Severity 24, FATAL level 4 of 4. */
  case object Fatal4 extends Severity {
    /** Returns 24. */
    def number: Int  = 24
    /** Returns `"FATAL"`. */
    def text: String = "FATAL"
  }

  /** Returns the level with this number, or `None` outside 1 to 24. */
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
   * Returns the first level of the group named by `s`, ignoring case: `"warn"`
   * gives `Warn`. Returns `None` for any other text, such as `"warning"`.
   */
  def fromText(s: String): Option[Severity] =
    s.toUpperCase(Locale.ROOT) match {
      case "TRACE" => Some(Trace)
      case "DEBUG" => Some(Debug)
      case "INFO"  => Some(Info)
      case "WARN"  => Some(Warn)
      case "ERROR" => Some(Error)
      case "FATAL" => Some(Fatal)
      case _       => None
    }
}
