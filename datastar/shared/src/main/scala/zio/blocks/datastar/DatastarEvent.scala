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
import zio.http.html.{CssSelector, Dom, Js}
import zio.blocks.maybe.Maybe
import zio.http.ServerSentEvent

/**
 * A Datastar SSE event that can be sent to the browser.
 *
 * Sealed trait with builder-style constructors in the companion object. Use
 * [[DatastarEvent.patchElements]], [[DatastarEvent.patchSignals]],
 * [[DatastarEvent.executeScript]], or [[DatastarEvent.removeElements]] to
 * create events, then call `renderSSE` to produce the SSE wire format. Builder
 * options map directly to emitted SSE fields such as `selector`, `mode`,
 * `useViewTransition`, `namespace`, `id`, and `retry`.
 *
 * {{{
 * val sse = DatastarEvent.patchSignals(count := 42).eventId("evt-1").renderSSE
 *
 * // event: datastar-patch-signals
 * // id: evt-1
 * // data: signals {"count":42}
 * }}}
 */
sealed trait DatastarEvent {

  def renderSSE: String
}

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

  final class PatchElementsBuilder private[DatastarEvent] (
    private val elements: Dom,
    private val selector: Maybe[CssSelector],
    private val mode: ElementPatchMode,
    private val useViewTransition: Boolean,
    private val namespace: Maybe[String],
    private val eventId: Maybe[String],
    private val retryMillis: Maybe[Long]
  ) {

    def selector(s: CssSelector): PatchElementsBuilder =
      new PatchElementsBuilder(elements, Maybe.present(s), mode, useViewTransition, namespace, eventId, retryMillis)

    def mode(m: ElementPatchMode): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, m, useViewTransition, namespace, eventId, retryMillis)

    def viewTransition: PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, true, namespace, eventId, retryMillis)

    def namespace(ns: String): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, useViewTransition, Maybe.present(ns), eventId, retryMillis)

    def eventId(id: String): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, useViewTransition, namespace, Maybe.present(id), retryMillis)

    def retry(millis: Long): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, useViewTransition, namespace, eventId, Maybe.present(millis))

    def renderSSE: String =
      PatchElements(elements, selector, mode, useViewTransition, namespace, eventId, retryMillis).renderSSE
  }

  final class PatchSignalsBuilder private[DatastarEvent] (
    private val signalsJson: String,
    private val emitOnlyIfMissing: Boolean,
    private val eventId: Maybe[String],
    private val retryMillis: Maybe[Long]
  ) {

    def onlyIfMissing: PatchSignalsBuilder =
      new PatchSignalsBuilder(signalsJson, true, eventId, retryMillis)

    def eventId(id: String): PatchSignalsBuilder =
      new PatchSignalsBuilder(signalsJson, emitOnlyIfMissing, Maybe.present(id), retryMillis)

    def retry(millis: Long): PatchSignalsBuilder =
      new PatchSignalsBuilder(signalsJson, emitOnlyIfMissing, eventId, Maybe.present(millis))

    def renderSSE: String =
      PatchSignals(signalsJson, emitOnlyIfMissing, eventId, retryMillis).renderSSE
  }

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

  def patchSignalsRaw(json: String): PatchSignalsBuilder =
    new PatchSignalsBuilder(json, false, Maybe.absent, Maybe.absent)

  final class RemoveElementsBuilder private[DatastarEvent] (
    private val inner: PatchElementsBuilder
  ) {
    def viewTransition: RemoveElementsBuilder        = new RemoveElementsBuilder(inner.viewTransition)
    def namespace(ns: String): RemoveElementsBuilder = new RemoveElementsBuilder(inner.namespace(ns))
    def eventId(id: String): RemoveElementsBuilder   = new RemoveElementsBuilder(inner.eventId(id))
    def retry(millis: Long): RemoveElementsBuilder   = new RemoveElementsBuilder(inner.retry(millis))
    def renderSSE: String                            = inner.renderSSE
  }

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
