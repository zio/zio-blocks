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
 * The case of a name in a Datastar attribute key. HTML attribute names are not
 * case-sensitive, so the builders write names in kebab case and add a
 * `__case.*` suffix that tells Datastar which case to turn the name back into.
 * The suffix is left out when the case is the builder's default: kebab for
 * [[DataOn]], camel for [[DataSignalsBuilder]].
 */
sealed trait CaseModifier extends Product with Serializable {

  /** Returns the `__case.*` suffix, such as `__case.camel`. */
  def render: String

  /** Returns [[render]], or `""` when this case is `default`. */
  def suffix(default: CaseModifier): String =
    if (this == default) "" else render
}

/** The four cases that Datastar supports. */
object CaseModifier {

  /** Camel case, as in `myEvent`. */
  case object Camel extends CaseModifier {

    /** Returns `__case.camel`. */
    def render: String = "__case.camel"
  }

  /** Kebab case, as in `my-event`. */
  case object Kebab extends CaseModifier {

    /** Returns `__case.kebab`. */
    def render: String = "__case.kebab"
  }

  /** Snake case, as in `my_event`. */
  case object Snake extends CaseModifier {

    /** Returns `__case.snake`. */
    def render: String = "__case.snake"
  }

  /** Pascal case, as in `MyEvent`. */
  case object Pascal extends CaseModifier {

    /** Returns `__case.pascal`. */
    def render: String = "__case.pascal"
  }
}
