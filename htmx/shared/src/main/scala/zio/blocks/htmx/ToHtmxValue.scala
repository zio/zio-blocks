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

package zio.blocks.htmx

import scala.annotation.implicitNotFound

import zio.blocks.html.{CssSelector, Js}
import zio.blocks.schema.json.Json
import zio.http.{Path, URL}

/**
 * Type class for rendering typed HTMX DSL values into raw attribute strings.
 *
 * `HtmxAttrKey#:=` requires an implicit instance of this type class for the
 * value being assigned. The module ships instances for common primitives,
 * selectors, paths, URLs, JSON values, and the richer HTMX ADTs defined in this
 * package.
 */
@implicitNotFound("No ToHtmxValue instance found for type ${A}.")
trait ToHtmxValue[-A] {
  def toHtmxValue(value: A): String
}

object ToHtmxValue {

  /** Summons the [[ToHtmxValue]] instance for `A`. */
  def apply[A](implicit ev: ToHtmxValue[A]): ToHtmxValue[A] = ev

  implicit val stringValue: ToHtmxValue[String] = new ToHtmxValue[String] {
    def toHtmxValue(value: String): String = value
  }

  implicit val booleanValue: ToHtmxValue[Boolean] = new ToHtmxValue[Boolean] {
    def toHtmxValue(value: Boolean): String = if (value) "true" else "false"
  }

  implicit val intValue: ToHtmxValue[Int] = new ToHtmxValue[Int] {
    def toHtmxValue(value: Int): String = value.toString
  }

  implicit val longValue: ToHtmxValue[Long] = new ToHtmxValue[Long] {
    def toHtmxValue(value: Long): String = value.toString
  }

  implicit val doubleValue: ToHtmxValue[Double] = new ToHtmxValue[Double] {
    def toHtmxValue(value: Double): String = value.toString
  }

  implicit val jsValue: ToHtmxValue[Js] = new ToHtmxValue[Js] {
    def toHtmxValue(value: Js): String = value.value
  }

  implicit val cssSelectorValue: ToHtmxValue[CssSelector] = new ToHtmxValue[CssSelector] {
    def toHtmxValue(value: CssSelector): String = value.render
  }

  implicit val pathValue: ToHtmxValue[Path] = new ToHtmxValue[Path] {
    def toHtmxValue(value: Path): String = value.encode
  }

  implicit val urlValue: ToHtmxValue[URL] = new ToHtmxValue[URL] {
    def toHtmxValue(value: URL): String = value.encode
  }

  implicit val jsonValue: ToHtmxValue[Json] = new ToHtmxValue[Json] {
    def toHtmxValue(value: Json): String = value.print
  }
}
