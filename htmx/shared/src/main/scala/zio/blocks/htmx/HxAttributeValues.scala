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

import zio.blocks.schema.Schema
import zio.blocks.schema.json.Json
import zio.http.{Path, URL}

/**
 * Typed values for HTMX URL-bearing attributes such as `hx-push-url` and
 * `hx-replace-url`.
 */
sealed trait HxUrlUpdate extends Product with Serializable {
  def render: String
}

object HxUrlUpdate {
  case object Enabled extends HxUrlUpdate {
    def render: String = "true"
  }

  case object Disabled extends HxUrlUpdate {
    def render: String = "false"
  }

  final case class Url private[htmx] (value: String) extends HxUrlUpdate {
    def render: String = value
  }

  def apply(value: Boolean): HxUrlUpdate = if (value) Enabled else Disabled
  def apply(value: String): HxUrlUpdate  = new Url(HtmxSupport.requireNonBlank(value, "HTMX URL update"))
  def apply(value: Path): HxUrlUpdate    = new Url(value.encode)
  def apply(value: URL): HxUrlUpdate     = new Url(value.encode)

  def parse(value: String): Either[String, HxUrlUpdate] = value.trim.toLowerCase match {
    case "true"  => Right(Enabled)
    case "false" => Right(Disabled)
    case _        => parseValidated(() => apply(value))
  }

  private def parseValidated(value: () => HxUrlUpdate): Either[String, HxUrlUpdate] =
    try Right(value())
    catch {
      case error: IllegalArgumentException => Left(error.getMessage)
    }

  implicit val toHtmxValue: ToHtmxValue[HxUrlUpdate] = new ToHtmxValue[HxUrlUpdate] {
    def toHtmxValue(value: HxUrlUpdate): String = value.render
  }
}

/**
 * Schema-backed or raw-JSON value for `hx-vals`.
 *
 * Use [[from]] to encode a schema-backed value to JSON, or [[json]] when you
 * already have a `Json` value.
 */
final case class HxVals private (serialized: String)

object HxVals {
  def from[A](value: A)(implicit schema: Schema[A]): HxVals = HxVals(schema.jsonCodec.encodeToString(value))
  def json(value: Json): HxVals                             = HxVals(value.print)

  implicit val toHtmxValue: ToHtmxValue[HxVals] = new ToHtmxValue[HxVals] {
    def toHtmxValue(value: HxVals): String = value.serialized
  }
}

/**
 * Schema-backed or raw-JSON value for `hx-headers`.
 *
 * Use [[from]] to encode a schema-backed value to JSON, or [[json]] when you
 * already have a `Json` value.
 */
final case class HxHeadersValue private (serialized: String)

object HxHeadersValue {
  def from[A](value: A)(implicit schema: Schema[A]): HxHeadersValue =
    HxHeadersValue(schema.jsonCodec.encodeToString(value))

  def json(value: Json): HxHeadersValue = HxHeadersValue(value.print)

  implicit val toHtmxValue: ToHtmxValue[HxHeadersValue] = new ToHtmxValue[HxHeadersValue] {
    def toHtmxValue(value: HxHeadersValue): String = value.serialized
  }
}

/**
 * Typed value for `hx-swap-oob`.
 *
 * The value can be a simple boolean flag or a swap strategy optionally scoped
 * to a selector.
 */
final case class HxSwapOob private (rendered: String)

object HxSwapOob {
  val True: HxSwapOob  = HxSwapOob("true")
  val False: HxSwapOob = HxSwapOob("false")

  def apply(enabled: Boolean): HxSwapOob = if (enabled) True else False

  def using(swap: HxSwap): HxSwapOob = HxSwapOob(swap.render)

  def using(swap: HxSwap, selector: HxTarget): HxSwapOob =
    HxSwapOob(swap.render + ":" + selector.render)

  implicit val toHtmxValue: ToHtmxValue[HxSwapOob] = new ToHtmxValue[HxSwapOob] {
    def toHtmxValue(value: HxSwapOob): String = value.rendered
  }
}

/** Space-separated set of HTMX attribute names rendered for `hx-disinherit`. */
final case class HxAttributeNames private (rendered: String)

object HxAttributeNames {
  def apply(first: String, rest: String*): HxAttributeNames =
    HxAttributeNames(validateNames(first +: rest).mkString(" "))

  private def validateNames(names: Seq[String]): Seq[String] =
    names.map(name => HtmxSupport.requireNonBlank(name, "HTMX attribute name"))

  implicit val toHtmxValue: ToHtmxValue[HxAttributeNames] = new ToHtmxValue[HxAttributeNames] {
    def toHtmxValue(value: HxAttributeNames): String = value.rendered
  }
}

/** Comma-separated set of HTMX extension names rendered for `hx-ext`. */
final case class HxExtensions private (rendered: String)

object HxExtensions {
  def apply(first: String, rest: String*): HxExtensions =
    HxExtensions(validateNames(first +: rest).mkString(","))

  private def validateNames(names: Seq[String]): Seq[String] =
    names.map(name => HtmxSupport.requireNonBlank(name, "HTMX extension name"))

  implicit val toHtmxValue: ToHtmxValue[HxExtensions] = new ToHtmxValue[HxExtensions] {
    def toHtmxValue(value: HxExtensions): String = value.rendered
  }
}
