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

import zio.blocks.chunk.Chunk
import zio.blocks.html._
import zio.blocks.maybe.Maybe
import zio.http.ServerSentEvent

/**
 * A Datastar server-sent event: a message that patches elements or signals in
 * the browser page. Build one in a server endpoint that streams
 * `text/event-stream`, and write the text that `renderSSE` returns to the
 * response body.
 *
 * The companion's constructors, [[DatastarEvent.patchElements]],
 * [[DatastarEvent.patchSignals]], [[DatastarEvent.patchSignalsRaw]],
 * [[DatastarEvent.executeScript]] and [[DatastarEvent.removeElements]], return
 * builders, not `DatastarEvent` values. Each builder option adds one field to
 * the event (`selector`, `mode`, `useViewTransition`, `namespace`, `id`,
 * `retry`, ...), and the builder's `renderSSE` writes the event in the SSE wire
 * format, ending with the blank line that closes it, so the strings of several
 * events can be concatenated into one stream.
 *
 * {{{
 * import zio.blocks.html._
 *
 * val count = Signal[Int]("count")
 *
 * DatastarEvent.patchSignals(count := 42).eventId("evt-1").renderSSE
 * // "event: datastar-patch-signals\nid: evt-1\ndata: signals {\"count\":42}\n\n"
 *
 * DatastarEvent.patchElements(li("Widget")).selector(CssSelector.id("results")).mode(ElementPatchMode.Append).renderSSE
 * // "event: datastar-patch-elements\ndata: selector #results\ndata: mode append\ndata: elements <li>Widget</li>\n\n"
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/datastar/sse/ Server-Sent Events]]
 */
sealed trait DatastarEvent {

  /**
   * Renders this event in the SSE wire format, ending with a blank line.
   *
   * @throws java.lang.IllegalArgumentException
   *   if the event id contains CR or LF, or the retry delay is negative
   */
  def renderSSE: String
}

/**
 * Constructors for Datastar events. Each returns a builder whose options set
 * the event's fields and whose `renderSSE` renders it.
 */
object DatastarEvent {

  private final case class PatchElements(
    elements: Dom,
    selector: Maybe[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    namespace: Maybe[String],
    eventId: Maybe[String],
    retryMillis: Maybe[Long]
  ) extends DatastarEvent {

    def renderSSE: String = {
      val sb = new java.lang.StringBuilder(256)
      selector.toOption.foreach(s => sb.append("selector ").append(s.render).append('\n'))
      if (mode != ElementPatchMode.Outer) sb.append("mode ").append(mode.render).append('\n')
      if (useViewTransition) sb.append("useViewTransition true\n")
      namespace.toOption.foreach(ns => sb.append("namespace ").append(ns).append('\n'))
      sb.append("elements ").append(elements.renderMinified)
      val data        = sb.toString
      val sse         = ServerSentEvent(data, EventType.PatchElements.render)
      val withEventId = eventId.toOption.fold(sse)(sse.id)
      retryMillis.toOption.fold(withEventId)(withEventId.retry).render
    }
  }

  private final case class PatchSignals(
    signalsJson: String,
    onlyIfMissing: Boolean,
    eventId: Maybe[String],
    retryMillis: Maybe[Long]
  ) extends DatastarEvent {

    def renderSSE: String = {
      val sb = new java.lang.StringBuilder(128)
      if (onlyIfMissing) sb.append("onlyIfMissing true\n")
      sb.append("signals ").append(signalsJson)
      val data        = sb.toString
      val sse         = ServerSentEvent(data, EventType.PatchSignals.render)
      val withEventId = eventId.toOption.fold(sse)(sse.id)
      retryMillis.toOption.fold(withEventId)(withEventId.retry).render
    }
  }

  /**
   * Builds a `datastar-patch-elements` event, which inserts, replaces or
   * removes elements in the page. Each option returns a new builder; calling an
   * option again replaces its earlier value. `renderSSE` renders the event.
   */
  final class PatchElementsBuilder private[DatastarEvent] (
    private val elements: Dom,
    private val selector: Maybe[CssSelector],
    private val mode: ElementPatchMode,
    private val useViewTransition: Boolean,
    private val namespace: Maybe[String],
    private val eventId: Maybe[String],
    private val retryMillis: Maybe[Long]
  ) {

    /**
     * Sets the CSS selector of the target element and adds a `selector` line.
     * Without a selector, Datastar matches each patched element to the page
     * element with the same `id`. The selector is written as `s.render` returns
     * it, so it must not contain a line break; use selectors from your own
     * code.
     */
    def selector(s: CssSelector): PatchElementsBuilder =
      new PatchElementsBuilder(elements, Maybe.present(s), mode, useViewTransition, namespace, eventId, retryMillis)

    /**
     * Sets where the elements go relative to the target and adds a `mode` line.
     * `ElementPatchMode.Outer` is the default and writes no `mode` line.
     */
    def mode(m: ElementPatchMode): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, m, useViewTransition, namespace, eventId, retryMillis)

    /**
     * Adds `useViewTransition true`, so Datastar applies the patch inside a
     * view transition where the browser supports the View Transition API.
     */
    def viewTransition: PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, true, namespace, eventId, retryMillis)

    /**
     * Adds a `namespace` line, `svg` or `mathml` in the Datastar protocol, for
     * elements that belong to that XML namespace. The value is written as
     * given, so it must not contain a line break; use values from your own
     * code.
     */
    def namespace(ns: String): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, useViewTransition, Maybe.present(ns), eventId, retryMillis)

    /**
     * Sets the SSE `id:` field, which a reconnecting browser sends back in the
     * `Last-Event-ID` header. `renderSSE` throws if the id contains CR or LF.
     */
    def eventId(id: String): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, useViewTransition, namespace, Maybe.present(id), retryMillis)

    /**
     * Sets the SSE `retry:` field, the delay in milliseconds before the browser
     * reconnects. `renderSSE` throws if it is negative.
     */
    def retry(millis: Long): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, useViewTransition, namespace, eventId, Maybe.present(millis))

    /**
     * Renders the event: the `event:`, `id:` and `retry:` lines, then one
     * `data:` line each for the selector, mode, view transition and namespace
     * that are set, then `data: elements <html>` with the minified HTML, then a
     * blank line.
     *
     * When the HTML contains a line break, the text after it goes on bare
     * `data:` lines without the `elements` key, and Datastar drops those lines;
     * this is a known bug.
     *
     * @throws java.lang.IllegalArgumentException
     *   if the event id contains CR or LF, or the retry delay is negative
     */
    def renderSSE: String =
      PatchElements(elements, selector, mode, useViewTransition, namespace, eventId, retryMillis).renderSSE
  }

  /**
   * Builds a `datastar-patch-signals` event, which sets signal values in the
   * browser. Each option returns a new builder. `renderSSE` renders the event.
   */
  final class PatchSignalsBuilder private[DatastarEvent] (
    private val signalsJson: String,
    private val emitOnlyIfMissing: Boolean,
    private val eventId: Maybe[String],
    private val retryMillis: Maybe[Long]
  ) {

    /**
     * Adds `onlyIfMissing true`, so Datastar sets only the signals that the
     * page does not have yet, which supplies defaults without overwriting the
     * browser's values.
     */
    def onlyIfMissing: PatchSignalsBuilder =
      new PatchSignalsBuilder(signalsJson, true, eventId, retryMillis)

    /**
     * Sets the SSE `id:` field, which a reconnecting browser sends back in the
     * `Last-Event-ID` header. `renderSSE` throws if the id contains CR or LF.
     */
    def eventId(id: String): PatchSignalsBuilder =
      new PatchSignalsBuilder(signalsJson, emitOnlyIfMissing, Maybe.present(id), retryMillis)

    /**
     * Sets the SSE `retry:` field, the delay in milliseconds before the browser
     * reconnects. `renderSSE` throws if it is negative.
     */
    def retry(millis: Long): PatchSignalsBuilder =
      new PatchSignalsBuilder(signalsJson, emitOnlyIfMissing, eventId, Maybe.present(millis))

    /**
     * Renders the event: the `event:`, `id:` and `retry:` lines, then
     * `data: onlyIfMissing true` when set, then `data: signals <json>`, then a
     * blank line.
     *
     * @throws java.lang.IllegalArgumentException
     *   if the event id contains CR or LF, or the retry delay is negative
     */
    def renderSSE: String =
      PatchSignals(signalsJson, emitOnlyIfMissing, eventId, retryMillis).renderSSE
  }

  /**
   * Starts a `datastar-patch-elements` event that sends `elements` to the page.
   * With no selector, Datastar morphs each top-level patched element into the
   * page element with the same `id`.
   *
   * The HTML is rendered minified on one `data: elements` line. A line break
   * inside it (from a text node such as `pre("a\nb")`) splits it, and Datastar
   * drops the text after the break; this is a known bug.
   *
   * {{{
   * import zio.blocks.html._
   *
   * DatastarEvent.patchElements(div(id := "cart", "3 items")).renderSSE
   * // "event: datastar-patch-elements\ndata: elements <div id=\"cart\">3 items</div>\n\n"
   * }}}
   */
  def patchElements(elements: Dom): PatchElementsBuilder =
    new PatchElementsBuilder(
      elements,
      Maybe.absent,
      ElementPatchMode.Outer,
      false,
      Maybe.absent,
      Maybe.absent,
      Maybe.absent
    )

  /**
   * Starts a `datastar-patch-signals` event that sets the given signals. The
   * updates become one JSON object, in argument order, whose values are the
   * JSON that each update's schema produced.
   *
   * {{{
   * import zio.blocks.html._
   *
   * val price    = Signal[Double]("price")
   * val quantity = Signal[Int]("quantity")
   *
   * DatastarEvent.patchSignals(price := 19.99, quantity := 3).renderSSE
   * // "event: datastar-patch-signals\ndata: signals {\"price\":19.99,\"quantity\":3}\n\n"
   * }}}
   */
  def patchSignals(first: SignalUpdate[_], rest: SignalUpdate[_]*): PatchSignalsBuilder = {
    val sb = new java.lang.StringBuilder(64)
    sb.append('{')
    appendJsonString(sb, first.name)
    sb.append(':').append(first.serialized)
    var i = 0
    while (i < rest.length) {
      sb.append(',')
      val u = rest(i)
      appendJsonString(sb, u.name)
      sb.append(':').append(u.serialized)
      i += 1
    }
    sb.append('}')
    new PatchSignalsBuilder(sb.toString, false, Maybe.absent, Maybe.absent)
  }

  /**
   * Starts a `datastar-patch-signals` event whose `signals` value is `json`,
   * written as given and not validated. Malformed JSON gives an event that
   * Datastar discards. When `json` contains a line break, the lines after the
   * first go out as bare `data:` lines that Datastar drops; this is a known
   * bug, so pass single-line JSON.
   */
  def patchSignalsRaw(json: String): PatchSignalsBuilder =
    new PatchSignalsBuilder(json, false, Maybe.absent, Maybe.absent)

  /**
   * Builds a `datastar-patch-elements` event in `remove` mode, which removes
   * the elements that a selector matches. It offers only the options that apply
   * to a removal; each returns a new builder.
   */
  final class RemoveElementsBuilder private[DatastarEvent] (
    private val inner: PatchElementsBuilder
  ) {

    /** Adds `useViewTransition true` to the removal. */
    def viewTransition: RemoveElementsBuilder = new RemoveElementsBuilder(inner.viewTransition)

    /**
     * Adds a `namespace` line. The value is written as given, so it must not
     * contain a line break.
     */
    def namespace(ns: String): RemoveElementsBuilder = new RemoveElementsBuilder(inner.namespace(ns))

    /**
     * Sets the SSE `id:` field; `renderSSE` throws if it contains CR or LF.
     */
    def eventId(id: String): RemoveElementsBuilder = new RemoveElementsBuilder(inner.eventId(id))

    /**
     * Sets the SSE `retry:` field in milliseconds; `renderSSE` throws if it is
     * negative.
     */
    def retry(millis: Long): RemoveElementsBuilder = new RemoveElementsBuilder(inner.retry(millis))

    /**
     * Renders the event: the `event:`, `id:` and `retry:` lines, then
     * `data: selector ...`, `data: mode remove`, the view transition and
     * namespace lines that are set, an empty `data: elements ` line and a blank
     * line.
     *
     * @throws java.lang.IllegalArgumentException
     *   if the event id contains CR or LF, or the retry delay is negative
     */
    def renderSSE: String = inner.renderSSE
  }

  /**
   * Starts an event that removes the elements matching `selector`. The selector
   * is written as `selector.render` returns it, so it must not contain a line
   * break; use selectors from your own code.
   *
   * {{{
   * import zio.blocks.html._
   *
   * DatastarEvent.removeElements(CssSelector.id("banner")).renderSSE
   * // "event: datastar-patch-elements\ndata: selector #banner\ndata: mode remove\ndata: elements \n\n"
   * }}}
   */
  def removeElements(selector: CssSelector): RemoveElementsBuilder =
    new RemoveElementsBuilder(
      new PatchElementsBuilder(
        Dom.Empty,
        Maybe.present(selector),
        ElementPatchMode.Remove,
        false,
        Maybe.absent,
        Maybe.absent,
        Maybe.absent
      )
    )

  /**
   * Starts an event that runs `code` in the browser. It is an element patch
   * that appends `<script data-effect="el.remove()">code</script>` to `body`,
   * so the script removes itself after it runs. The returned builder accepts
   * every element-patch option; `selector` and `mode` replace the `body` and
   * `append` targeting.
   *
   * {{{
   * import zio.blocks.html._
   *
   * DatastarEvent.executeScript(js"console.log('done')").renderSSE
   * // "event: datastar-patch-elements\ndata: selector body\ndata: mode append\ndata: elements <script data-effect=\"el.remove()\">console.log('done')</script>\n\n"
   * }}}
   */
  def executeScript(code: Js): PatchElementsBuilder = {
    val scriptElement = Dom.Element.Script(
      Chunk(Dom.Attribute.KeyValue("data-effect", Dom.AttributeValue.StringValue("el.remove()"))),
      Chunk(Dom.Text(code.value))
    )
    new PatchElementsBuilder(
      scriptElement,
      Maybe.present(CssSelector.element("body")),
      ElementPatchMode.Append,
      false,
      Maybe.absent,
      Maybe.absent,
      Maybe.absent
    )
  }

  private def appendJsonString(sb: java.lang.StringBuilder, s: String): Unit =
    DatastarStringEscape.appendQuotedString(sb, s)
}
