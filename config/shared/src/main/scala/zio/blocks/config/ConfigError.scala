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

sealed trait ConfigError extends NoStackTrace {
  def message: String
}

object ConfigError {
  case class MissingKey(path: String, source: String) extends ConfigError {
    def message: String = s"Missing required key '$path' in source '$source'"
  }

  case class InvalidValue(
    path: String,
    value: String,
    expectedType: String,
    source: String,
    cause: Option[Throwable] = None
  ) extends ConfigError {
    def message: String = {
      val base = s"Invalid value '$value' for key '$path' (expected $expectedType) in source '$source'"
      cause match {
        case Some(t) => s"$base: ${t.getMessage}"
        case None    => base
      }
    }
  }

  case class DuplicateKey(path: String, sources: Seq[String]) extends ConfigError {
    def message: String = s"Duplicate key '$path' found in conflicting sources: ${sources.mkString(", ")}"
  }

  case class Composite(errors: ::[ConfigError]) extends ConfigError {
    def message: String = {
      val lines = errors.toList.map(e => e.message)
      lines.mkString("\n")
    }
  }
}
