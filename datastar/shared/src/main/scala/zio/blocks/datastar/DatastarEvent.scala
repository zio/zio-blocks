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

import zio.blocks.chunk.Chunk
import zio.blocks.html.{CssSelector, Dom, Js}

sealed trait DatastarEvent {

  def renderSSE: String
}

object DatastarEvent {

  private final case class PatchElements(
    elements: Dom,
    selector: Option[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    namespace: Option[String],
    eventId: Option[String],
    retryMillis: Option[Long]
  ) extends DatastarEvent {

    def renderSSE: String = {
      val sb = new java.lang.StringBuilder(256)
      selector.foreach(s => sb.append("selector ").append(s.render).append('\n'))
      if (mode != ElementPatchMode.Outer) sb.append("mode ").append(mode.render).append('\n')
      if (useViewTransition) sb.append("useViewTransition true\n")
      namespace.foreach(ns => sb.append("namespace ").append(ns).append('\n'))
      sb.append("elements ").append(elements.renderMinified)
      val data = sb.toString
      ServerSentEvent(data, EventType.PatchElements.render)
        .pipe(sse => eventId.fold(sse)(sse.withId))
        .pipe(sse => retryMillis.fold(sse)(sse.withRetry))
        .render
    }
  }

  private final case class PatchSignals(
    signalsJson: String,
    onlyIfMissing: Boolean,
    eventId: Option[String],
    retryMillis: Option[Long]
  ) extends DatastarEvent {

    def renderSSE: String = {
      val sb = new java.lang.StringBuilder(128)
      if (onlyIfMissing) sb.append("onlyIfMissing true\n")
      sb.append("signals ").append(signalsJson)
      val data = sb.toString
      ServerSentEvent(data, EventType.PatchSignals.render)
        .pipe(sse => eventId.fold(sse)(sse.withId))
        .pipe(sse => retryMillis.fold(sse)(sse.withRetry))
        .render
    }
  }

  final class PatchElementsBuilder private[DatastarEvent] (
    private val elements: Dom,
    private val selector: Option[CssSelector],
    private val mode: ElementPatchMode,
    private val useViewTransition: Boolean,
    private val namespace: Option[String],
    private val eventId: Option[String],
    private val retryMillis: Option[Long]
  ) {

    def withSelector(s: CssSelector): PatchElementsBuilder =
      new PatchElementsBuilder(elements, Some(s), mode, useViewTransition, namespace, eventId, retryMillis)

    def withMode(m: ElementPatchMode): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, m, useViewTransition, namespace, eventId, retryMillis)

    def withViewTransition: PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, true, namespace, eventId, retryMillis)

    def withNamespace(ns: String): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, useViewTransition, Some(ns), eventId, retryMillis)

    def withEventId(id: String): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, useViewTransition, namespace, Some(id), retryMillis)

    def withRetry(millis: Long): PatchElementsBuilder =
      new PatchElementsBuilder(elements, selector, mode, useViewTransition, namespace, eventId, Some(millis))

    def renderSSE: String =
      PatchElements(elements, selector, mode, useViewTransition, namespace, eventId, retryMillis).renderSSE
  }

  final class PatchSignalsBuilder private[DatastarEvent] (
    private val signalsJson: String,
    private val onlyIfMissing: Boolean,
    private val eventId: Option[String],
    private val retryMillis: Option[Long]
  ) {

    def withOnlyIfMissing: PatchSignalsBuilder =
      new PatchSignalsBuilder(signalsJson, true, eventId, retryMillis)

    def withEventId(id: String): PatchSignalsBuilder =
      new PatchSignalsBuilder(signalsJson, onlyIfMissing, Some(id), retryMillis)

    def withRetry(millis: Long): PatchSignalsBuilder =
      new PatchSignalsBuilder(signalsJson, onlyIfMissing, eventId, Some(millis))

    def renderSSE: String =
      PatchSignals(signalsJson, onlyIfMissing, eventId, retryMillis).renderSSE
  }

  def patchElements(elements: Dom): PatchElementsBuilder =
    new PatchElementsBuilder(elements, None, ElementPatchMode.Outer, false, None, None, None)

  def patchSignals(updates: SignalUpdate[_]*): PatchSignalsBuilder = {
    val sb = new java.lang.StringBuilder(64)
    sb.append('{')
    var i = 0
    while (i < updates.length) {
      if (i > 0) sb.append(',')
      val u = updates(i)
      sb.append('"').append(u.name).append('"').append(':').append(u.serialized)
      i += 1
    }
    sb.append('}')
    new PatchSignalsBuilder(sb.toString, false, None, None)
  }

  def patchSignalsRaw(json: String): PatchSignalsBuilder =
    new PatchSignalsBuilder(json, false, None, None)

  def removeElements(selector: CssSelector): PatchElementsBuilder =
    new PatchElementsBuilder(Dom.Empty, Some(selector), ElementPatchMode.Remove, false, None, None, None)

  def executeScript(code: Js): PatchElementsBuilder = {
    val scriptElement = Dom.Element.Script(
      Chunk(Dom.Attribute.KeyValue("data-effect", Dom.AttributeValue.StringValue("el.remove()"))),
      Chunk(Dom.Text(code.value))
    )
    new PatchElementsBuilder(
      scriptElement,
      Some(CssSelector.element("body")),
      ElementPatchMode.Append,
      false,
      None,
      None,
      None
    )
  }

  private implicit class PipeOps[A](private val self: A) extends AnyVal {
    def pipe[B](f: A => B): B = f(self)
  }
}
