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
      sb.append("event: ").append(EventType.PatchElements.render).append('\n')
      appendId(sb, eventId)
      appendRetry(sb, retryMillis)
      selector.toOption.foreach(s => appendDataLines(sb, "selector ", s.render))
      if (mode != ElementPatchMode.Outer) appendDataLines(sb, "mode ", mode.render)
      if (useViewTransition) appendData(sb, "useViewTransition true")
      namespace.toOption.foreach(ns => appendDataLines(sb, "namespace ", ns))
      appendDataLines(sb, "elements ", elements.renderMinified)
      sb.append('\n')
      sb.toString
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
      sb.append("event: ").append(EventType.PatchSignals.render).append('\n')
      appendId(sb, eventId)
      appendRetry(sb, retryMillis)
      if (onlyIfMissing) appendData(sb, "onlyIfMissing true")
      appendDataLines(sb, "signals ", signalsJson)
      sb.append('\n')
      sb.toString
    }
  }

  /**
   * Appends the SSE `id:` header, validating exactly like the SSE envelope
   * (`id` must not contain CR or LF).
   */
  private def appendId(sb: java.lang.StringBuilder, eventId: Maybe[String]): Unit =
    eventId.toOption.foreach { id =>
      if (id.indexOf('\n') >= 0 || id.indexOf('\r') >= 0)
        throw new IllegalArgumentException("SSE id must not contain CR or LF characters")
      sb.append("id: ").append(id).append('\n')
    }

  /**
   * Appends the SSE `retry:` header, validating exactly like the SSE envelope
   * (retry must be non-negative).
   */
  private def appendRetry(sb: java.lang.StringBuilder, retryMillis: Maybe[Long]): Unit =
    retryMillis.toOption.foreach { millis =>
      if (millis < 0)
        throw new IllegalArgumentException("SSE retry must be non-negative")
      sb.append("retry: ").append(millis).append('\n')
    }

  /** Appends a single `data:` line (the value is a protocol constant). */
  private def appendData(sb: java.lang.StringBuilder, line: String): Unit =
    sb.append("data: ").append(line).append('\n')

  /**
   * Appends `prefix + value` split into one `data:` line per line break,
   * mirroring the SSE encoder's `\r\n`/`\r`/`\n` splitting so multi-line
   * payloads (e.g. rendered elements) frame identically to the envelope.
   */
  private def appendDataLines(sb: java.lang.StringBuilder, prefix: String, value: String): Unit = {
    var start  = 0
    var index  = 0
    val length = value.length
    var first  = true
    while (index < length) {
      val c = value.charAt(index)
      if (c == '\n' || c == '\r') {
        appendDataLine(sb, prefix, first, value.substring(start, index))
        if (c == '\r' && index + 1 < length && value.charAt(index + 1) == '\n')
          index += 1
        start = index + 1
        first = false
      }
      index += 1
    }
    appendDataLine(sb, prefix, first, value.substring(start, length))
  }

  private def appendDataLine(
    sb: java.lang.StringBuilder,
    prefix: String,
    first: Boolean,
    segment: String
  ): Unit = {
    sb.append("data: ")
    if (first) sb.append(prefix)
    sb.append(segment).append('\n')
  }

  final case class PatchElementsBuilder private[DatastarEvent] (
    elements: Dom,
    selector: Maybe[CssSelector],
    mode: ElementPatchMode,
    useViewTransition: Boolean,
    namespace: Maybe[String],
    eventId: Maybe[String],
    retryMillis: Maybe[Long]
  ) {

    def selector(s: CssSelector): PatchElementsBuilder =
      copy(selector = Maybe.present(s))

    def mode(m: ElementPatchMode): PatchElementsBuilder =
      copy(mode = m)

    def viewTransition: PatchElementsBuilder =
      copy(useViewTransition = true)

    def namespace(ns: String): PatchElementsBuilder =
      copy(namespace = Maybe.present(ns))

    def eventId(id: String): PatchElementsBuilder =
      copy(eventId = Maybe.present(id))

    def retry(millis: Long): PatchElementsBuilder =
      copy(retryMillis = Maybe.present(millis))

    def renderSSE: String =
      PatchElements(elements, selector, mode, useViewTransition, namespace, eventId, retryMillis).renderSSE
  }

  final case class PatchSignalsBuilder private[DatastarEvent] (
    signalsJson: String,
    emitOnlyIfMissing: Boolean,
    eventId: Maybe[String],
    retryMillis: Maybe[Long]
  ) {

    def onlyIfMissing: PatchSignalsBuilder =
      copy(emitOnlyIfMissing = true)

    def eventId(id: String): PatchSignalsBuilder =
      copy(eventId = Maybe.present(id))

    def retry(millis: Long): PatchSignalsBuilder =
      copy(retryMillis = Maybe.present(millis))

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

  final case class RemoveElementsBuilder private[DatastarEvent] (
    inner: PatchElementsBuilder
  ) {
    def viewTransition: RemoveElementsBuilder        = copy(inner = inner.viewTransition)
    def namespace(ns: String): RemoveElementsBuilder = copy(inner = inner.namespace(ns))
    def eventId(id: String): RemoveElementsBuilder   = copy(inner = inner.eventId(id))
    def retry(millis: Long): RemoveElementsBuilder   = copy(inner = inner.retry(millis))
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
