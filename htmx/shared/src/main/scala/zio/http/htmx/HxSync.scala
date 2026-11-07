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
 * What `hx-sync` does with a new request while another request on the
 * synchronized element is in flight.
 */
sealed trait HxSyncStrategy extends Product with Serializable {

  /** The strategy as written after the `:` of `hx-sync`. */
  def render: String
}

/** The four [[HxSyncStrategy]] values and their parser. */
object HxSyncStrategy {

  /**
   * `abort`: drops the new request if one is in flight, and lets a later
   * request abort this one while it runs.
   */
  case object Abort extends HxSyncStrategy { def render: String = "abort" }

  /** `replace`: aborts the request in flight and sends the new one. */
  case object Replace extends HxSyncStrategy { def render: String = "replace" }

  /** `drop`: ignores the new request while one is in flight. */
  case object Drop extends HxSyncStrategy { def render: String = "drop" }

  /**
   * `queue`: queues the new request until the one in flight finishes. htmx
   * reads a bare `queue` as `queue last`; the `queue first` and `queue all`
   * forms are not available.
   */
  case object Queue extends HxSyncStrategy { def render: String = "queue" }

  /**
   * Parses a strategy name, after trimming it. The match is case-sensitive:
   * `ABORT` gives `Left("Unknown HTMX sync strategy: ABORT")`.
   */
  def parse(value: String): Either[String, HxSyncStrategy] = value.trim match {
    case "abort"   => Right(Abort)
    case "replace" => Right(Replace)
    case "drop"    => Right(Drop)
    case "queue"   => Right(Queue)
    case other     => Left("Unknown HTMX sync strategy: " + other)
  }
}

/**
 * An `hx-sync` value: the element whose requests are synchronized, and what to
 * do when a new request starts while another one on that element is in flight.
 *
 * It renders as `<target>:<strategy>`. htmx 2 splits the value at its first
 * `:`, so the target's selector must not contain a `:` of its own (as in
 * `li:first-child`).
 *
 * {{{
 * import zio.blocks.html._
 *
 * val email = input(
 *   name := "email",
 *   hxPost := "/signup/check-email",
 *   hxTrigger := HxTrigger.change,
 *   hxSync := HxSync(HxTarget.closest("form"), HxSyncStrategy.Abort)
 * ).render
 * // <input name="email" hx-post="/signup/check-email" hx-trigger="change" hx-sync="closest form:abort"/>
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/htmx/hx-sync/ HxSync]]
 */
final case class HxSync(target: HxTarget, strategy: HxSyncStrategy) {

  /** Renders the target, a `:` and the strategy, such as `this:drop`. */
  def render: String = target.render + ":" + strategy.render
}

/** The [[HxSync]] parser and its [[ToHtmxValue]] instance. */
object HxSync {

  /**
   * Parses `<target>:<strategy>`, after trimming it. The strategy is the suffix
   * after the last `:`, so `#a:b:drop` parses as the CSS target `#a:b` with
   * [[HxSyncStrategy.Drop]]. The strategy is required: `this` alone, which htmx
   * reads as `this:drop`, and `this:queue first` give
   * `Left("Invalid HTMX sync value: ...")`.
   */
  def parse(value: String): Either[String, HxSync] = {
    val trimmed = value.trim
    strategies.find { case (_, suffix) => trimmed.endsWith(suffix) } match {
      case Some((strategy, suffix)) if trimmed.length > suffix.length =>
        HxTarget.parse(trimmed.substring(0, trimmed.length - suffix.length)).map(HxSync(_, strategy))
      case _ => Left("Invalid HTMX sync value: " + value)
    }
  }

  private val strategies = Seq(
    HxSyncStrategy.Abort   -> ":abort",
    HxSyncStrategy.Replace -> ":replace",
    HxSyncStrategy.Drop    -> ":drop",
    HxSyncStrategy.Queue   -> ":queue"
  )

  /** Renders an [[HxSync]] with [[HxSync#render]]. */
  implicit val toHtmxValue: ToHtmxValue[HxSync] = new ToHtmxValue[HxSync] {
    def toHtmxValue(value: HxSync): String = value.render
  }
}
