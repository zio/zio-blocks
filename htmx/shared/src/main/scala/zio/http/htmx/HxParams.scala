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

package zio.http.htmx

/**
 * An `hx-params` value: which parameters htmx sends with a request.
 *
 * [[HxParams.All]] sends every parameter, [[HxParams.None]] sends none,
 * [[HxParams.only]] sends only the named ones and [[HxParams.not]] sends all
 * but the named ones. The factory methods trim each name and reject blank ones.
 *
 * {{{
 * import zio.blocks.html._
 *
 * val checkout = form(
 *   hxPost := "/checkout",
 *   hxParams := HxParams.not("coupon_draft", "debug")
 * )(button("Pay")).render
 * // <form hx-post="/checkout" hx-params="not coupon_draft,debug"><button>Pay</button></form>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/hx-params/ HxParams]]
 */
sealed trait HxParams extends Product with Serializable {

  /** Renders this value as the literal `hx-params` attribute value. */
  def render: String
}

/** The [[HxParams]] values, factories and parser. */
object HxParams {

  /** `*`: sends every parameter. */
  case object All extends HxParams {

    /** Renders `*`. */
    def render: String = "*"
  }

  /** `none`: sends no parameters. */
  case object None extends HxParams {

    /** Renders `none`. */
    def render: String = "none"
  }

  /**
   * `not a,b`: sends every parameter except the listed ones. The constructor
   * does not check the names (`Not(Nil)` renders `"not "`); [[HxParams.not]]
   * does.
   */
  final case class Not(params: Seq[String]) extends HxParams {

    /** Renders `not ` followed by the names joined with commas. */
    def render: String = "not " + params.mkString(",")
  }

  /**
   * `a,b`: sends only the listed parameters. The constructor does not check the
   * names (`Only(Nil)` renders an empty string); [[HxParams.only]] does.
   */
  final case class Only(params: Seq[String]) extends HxParams {

    /** Renders the names joined with commas. */
    def render: String = params.mkString(",")
  }

  /**
   * Creates a [[Not]] value that leaves out the named parameters. Each name is
   * trimmed.
   *
   * @throws java.lang.IllegalArgumentException
   *   if a name is blank
   */
  def not(first: String, rest: String*): HxParams = Not(validateNames(first +: rest))

  /**
   * Creates an [[Only]] value that sends just the named parameters. Each name
   * is trimmed.
   *
   * @throws java.lang.IllegalArgumentException
   *   if a name is blank
   */
  def only(first: String, rest: String*): HxParams = Only(validateNames(first +: rest))

  /**
   * Parses an `hx-params` string, after trimming it: `*`, `none`, `not `
   * followed by a list, or a list. A list is comma-separated names, each
   * trimmed. The keywords are case-sensitive, so `NONE` and a bare `not` parse
   * as an [[Only]] list of that one name. An empty list gives
   * `Left("HTMX params list must be non-empty")` and an empty name gives
   * `Left("HTMX params list cannot contain empty names")`.
   */
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

  /** Renders an [[HxParams]] with [[HxParams#render]]. */
  implicit val toHtmxValue: ToHtmxValue[HxParams] = new ToHtmxValue[HxParams] {
    def toHtmxValue(value: HxParams): String = value.render
  }
}
