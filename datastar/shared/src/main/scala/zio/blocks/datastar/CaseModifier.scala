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

package zio.http.datastar

/**
 * Modifies how a Datastar attribute key is cased when rendered by the DSL.
 *
 * Case modifiers contribute a `__case.*` suffix to attribute names when the
 * selected case differs from the default case for that attribute builder.
 */
sealed trait CaseModifier extends Product with Serializable {

  /** Renders the explicit Datastar `__case.*` suffix for this modifier. */
  def render: String

  /**
   * Renders this modifier's suffix unless it matches the builder's default case
   * modifier.
   */
  def suffix(default: CaseModifier): String =
    if (this == default) "" else render
}

object CaseModifier {

  /** Standard case modifiers supported by the Datastar attribute DSL. */

  /** Requests camel-case rendering. */
  case object Camel extends CaseModifier {
    def render: String = "__case.camel"
  }

  /** Requests kebab-case rendering. */
  case object Kebab extends CaseModifier {
    def render: String = "__case.kebab"
  }

  /** Requests snake-case rendering. */
  case object Snake extends CaseModifier {
    def render: String = "__case.snake"
  }

  /** Requests PascalCase rendering. */
  case object Pascal extends CaseModifier {
    def render: String = "__case.pascal"
  }
}
