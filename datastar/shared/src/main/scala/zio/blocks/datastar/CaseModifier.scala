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

package zio.blocks.datastar

sealed trait CaseModifier extends Product with Serializable {

  def render: String

  def suffix(default: CaseModifier): String =
    if (this == default) "" else render
}

object CaseModifier {

  case object Camel extends CaseModifier {
    def render: String = "__case.camel"
  }

  case object Kebab extends CaseModifier {
    def render: String = "__case.kebab"
  }

  case object Snake extends CaseModifier {
    def render: String = "__case.snake"
  }

  case object Pascal extends CaseModifier {
    def render: String = "__case.pascal"
  }
}
