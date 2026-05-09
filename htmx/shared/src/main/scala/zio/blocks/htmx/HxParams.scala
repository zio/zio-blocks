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

/**
 * Typed parameter inclusion strategies for the `hx-params` attribute.
 */
sealed trait HxParams extends Product with Serializable {
  def render: String
}

object HxParams {

  /** Includes all parameters. */
  case object All extends HxParams {
    def render: String = "*"
  }

  /** Includes no parameters. */
  case object None extends HxParams {
    def render: String = "none"
  }

  /** Excludes the listed parameters. */
  final case class Not(params: Seq[String]) extends HxParams {
    def render: String = "not " + params.mkString(",")
  }

  /** Includes only the listed parameters. */
  final case class Only(params: Seq[String]) extends HxParams {
    def render: String = params.mkString(",")
  }

  /** Creates a `not ...` parameter strategy. */
  def not(first: String, rest: String*): HxParams = Not(validateNames(first +: rest))

  /** Creates an allow-list parameter strategy. */
  def only(first: String, rest: String*): HxParams = Only(validateNames(first +: rest))

  /** Parses a rendered `hx-params` string back into a typed [[HxParams]]. */
  def parse(value: String): Either[String, HxParams] = {
    val trimmed = value.trim
    if (trimmed == "*") Right(All)
    else if (trimmed == "none") Right(None)
    else if (trimmed.startsWith("not ")) parseNames(trimmed.substring(4)).map(Not.apply)
    else parseNames(trimmed).map(Only.apply)
  }

  private def parseNames(value: String): Either[String, Seq[String]] = {
    val trimmed = value.trim
    if (trimmed.isEmpty) Left("HTMX params list must be non-empty")
    else {
      val names = trimmed.split(",", -1).toSeq.map(_.trim)
      if (names.exists(_.isEmpty)) Left("HTMX params list cannot contain empty names")
      else Right(names)
    }
  }

  private def validateNames(names: Seq[String]): Seq[String] =
    names.map(name => HtmxSupport.requireNonBlank(name, "HTMX param name"))

  implicit val toHtmxValue: ToHtmxValue[HxParams] = new ToHtmxValue[HxParams] {
    def toHtmxValue(value: HxParams): String = value.render
  }
}
