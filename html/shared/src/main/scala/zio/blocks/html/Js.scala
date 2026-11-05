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

package zio.blocks.html

/**
 * JavaScript source code, used as the body of a `script` element or as an
 * attribute value such as `onclick`. A script body is written without HTML
 * escaping (only `</` becomes `<\/`); an attribute value is HTML-escaped.
 *
 * Build code with `js"..."`, which turns each interpolated value into a
 * JavaScript literal through [[ToJs]], so strings are quoted and escaped.
 * `Js(...)` wraps a string as is, so pass it only trusted code.
 *
 * {{{
 * val item = "Tea \"Earl Grey\""
 * script(js"cart.add(\$item, \${2})").render
 * // <script>cart.add("Tea \"Earl Grey\"", 2)</script>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/html/#js-interpolator js Interpolator]]
 */
sealed abstract case class Js(value: String) {

  /** Returns the source code. */
  override def toString: String = value

  /** Returns a copy with `stripMargin` applied to the code. */
  def stripMargin: Js = Js(value.stripMargin)
}

/** Constructors for [[Js]]. */
object Js {

  /** Wraps `value` as code, without escaping or checking it. */
  def apply(value: String): Js = new Js(value) {}
}
