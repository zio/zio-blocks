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

package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * Typed error returned by the migration interpreter. Each variant carries a
 * structured [[DynamicOptic]] path and renders [[message]] lazily so the
 * success path stays allocation-free.
 */
sealed trait MigrationError {
  def path: DynamicOptic
  def message: String

  /** Renders as `"<ConstructorName>: <message>"` for human-readable output. */
  def toScalaString: String = s"${getClass.getSimpleName}: $message"
}

object MigrationError {

  /**
   * An interpreter arm failed at `path` while executing `actionName`. The
   * optional `cause` carries a short reason folded into [[message]] when
   * present; it defaults to `None` so serialised payloads stay compact.
   */
  final case class ActionFailed(
    path: DynamicOptic,
    actionName: String,
    cause: Option[String] = None
  ) extends MigrationError {
    lazy val message: String = {
      val base = s"Action $actionName failed at ${path.toScalaString}"
      cause match {
        case Some(c) => s"$base: $c"
        case _       => base
      }
    }
  }

  /** Field `fieldName` was not found in the record at `path`'s parent. */
  final case class MissingField(path: DynamicOptic, fieldName: String) extends MigrationError {
    lazy val message: String = s"Missing field '$fieldName' at ${path.toScalaString}"
  }

  /**
   * A value's primitive type contradicts the expected input signature (e.g.,
   * TransformValue / ChangeType receiving the wrong primitive).
   */
  final case class SchemaMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    lazy val message: String = s"Schema mismatch at ${path.toScalaString}: expected $expected, got $actual"
  }

  /**
   * `TransformKeys` produced two map entries with the same transformed key.
   * Surfaces the first colliding source key; `path` points to that key.
   */
  final case class KeyCollision(path: DynamicOptic, key: DynamicValue) extends MigrationError {
    lazy val message: String =
      s"Key collision at ${path.toScalaString}: key $key collides with an existing entry"
  }

  /**
   * A reversed multi-path action (`Join` / `Split`) could not invert the
   * underlying combiner/splitter at apply time. The optional `cause` carries a
   * short structured reason folded into [[message]] when present.
   */
  final case class Irreversible(
    path: DynamicOptic,
    cause: Option[String] = None
  ) extends MigrationError {
    lazy val message: String = {
      val base = s"Irreversible action at ${path.toScalaString}"
      cause match {
        case Some(c) => s"$base: $c"
        case _       => base
      }
    }
  }
}
