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

package zio.http

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.maybe.Maybe

/**
 * One Server-Sent Event: a payload of type `A` and the optional `event`, `id`
 * and `retry` fields. [[render]] writes it in the `text/event-stream` wire
 * format, turning the payload into `data:` lines with an [[SseDataEncoder]];
 * `String` and `Chunk[String]` payloads have one built in. The event and id may
 * not contain a line break and the retry may not be negative; the methods that
 * set them throw `IllegalArgumentException` otherwise.
 *
 * {{{
 * val event = ServerSentEvent("order 42 shipped", "order").id("17").retry(5000)
 *
 * event.render
 * // "event: order\nid: 17\nretry: 5000\ndata: order 42 shipped\n\n"
 * }}}
 *
 * To serve events, write each rendered event to a response body whose content
 * type is `text/event-stream`. http-model has no constant for that content
 * type, a known bug, so build it with
 * `ContentType(MediaType("text", "event-stream"))`.
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/server-sent-event/ ServerSentEvent]]
 */
final class ServerSentEvent[+A] private (
  val data: A,
  val eventType: Maybe[String],
  val eventId: Maybe[String],
  val retryMillis: Maybe[Long]
) {

  /**
   * Returns a copy with the `event:` field set, the event name a browser
   * `EventSource` dispatches on.
   *
   * {{{
   * ServerSentEvent("cart updated").event("cart").render // "event: cart\ndata: cart updated\n\n"
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if `event` contains CR or LF
   * @see
   *   [[https://zioblocks.com/reference/http-model/server-sent-event/#setting-metadata Setting Metadata]]
   */
  def event(event: String): ServerSentEvent[A] =
    ServerSentEvent.create(
      data,
      Maybe.present(ServerSentEvent.validateSingleLineField(event, "event")),
      eventId,
      retryMillis
    )

  /** Returns a copy without the `event:` field. */
  def clearEvent: ServerSentEvent[A] =
    ServerSentEvent.create(data, Maybe.absent, eventId, retryMillis)

  /**
   * Returns a copy with the `id:` field set, which a reconnecting browser sends
   * back in the `Last-Event-ID` header. There is no method that removes it.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `id` contains CR or LF
   */
  def id(id: String): ServerSentEvent[A] =
    ServerSentEvent.create(
      data,
      eventType,
      Maybe.present(ServerSentEvent.validateSingleLineField(id, "id")),
      retryMillis
    )

  /**
   * Returns a copy with the `retry:` field set: how many milliseconds the
   * browser waits before reconnecting.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `millis` is negative
   */
  def retry(millis: Long): ServerSentEvent[A] =
    ServerSentEvent.create(data, eventType, eventId, Maybe.present(millis))

  /** Returns a copy without the `retry:` field. */
  def clearRetry: ServerSentEvent[A] =
    ServerSentEvent.create(data, eventType, eventId, Maybe.absent)

  /**
   * Renders the event in the SSE wire format: the `event:`, `id:` and `retry:`
   * lines that are set, in that order, then one `data: ` line per line the
   * encoder returns, then a blank line that ends the event. Lines end with
   * `\n`.
   *
   * At least one `data:` line is always written, even when the encoder returns
   * no lines, so an event with an empty payload reaches the browser as a
   * message with empty data; this is a known bug, and there is no way to write
   * a comment line or a field-only frame such as a heartbeat. The encoder's
   * lines are written as they are, so a custom encoder must not return a line
   * that contains a line break.
   *
   * {{{
   * ServerSentEvent("line one\nline two").render // "data: line one\ndata: line two\n\n"
   * ServerSentEvent("").render                   // "data: \n\n"
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/http-model/server-sent-event/#rendering Rendering]]
   */
  def render(implicit encoder: SseDataEncoder[A]): String = {
    val sb = new java.lang.StringBuilder(256)
    renderTo(sb)
    sb.toString
  }

  private def renderTo(sb: java.lang.StringBuilder)(implicit encoder: SseDataEncoder[A]): Unit = {
    eventType.fold(())(eventValue => sb.append("event: ").append(eventValue).append('\n'))
    eventId.fold(())(idValue => sb.append("id: ").append(idValue).append('\n'))
    retryMillis.fold(())(millis => sb.append("retry: ").append(millis).append('\n'))

    val lines = encoder.lines(data)
    if (lines.isEmpty) sb.append("data: \n")
    else {
      val iterator = lines.iterator
      while (iterator.hasNext) {
        sb.append("data: ").append(iterator.next()).append('\n')
      }
    }
    sb.append('\n')
  }

  /**
   * Two events are equal when their payloads are equal by `==` and their event,
   * id and retry fields are equal. Events that render the same text with
   * payloads of different types, such as `"a"` and `Chunk("a")`, are not equal.
   */
  override def equals(other: Any): Boolean =
    other match {
      case that: ServerSentEvent[?] =>
        data == that.data && eventType == that.eventType && eventId == that.eventId && retryMillis == that.retryMillis
      case _ => false
    }

  /** A hash consistent with `equals`. */
  override def hashCode(): Int = {
    var result = data.##
    result = 31 * result + eventType.##
    result = 31 * result + eventId.##
    result = 31 * result + retryMillis.##
    result
  }

  /**
   * A description for logs, such as
   * `ServerSentEvent(data=order shipped, event=null, id=9, retry=null)`, not
   * the wire format; use [[render]] for that.
   */
  override def toString: String =
    s"ServerSentEvent(data=$data, event=${eventType.fold("null")(identity)}, id=${eventId.fold("null")(identity)}, retry=${retryMillis.fold("null")(_.toString)})"
}

/**
 * Creates [[ServerSentEvent]]s.
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/server-sent-event/#construction Construction]]
 */
object ServerSentEvent {

  /**
   * Creates an event with the payload `data` and no `event`, `id` or `retry`
   * field.
   *
   * {{{
   * ServerSentEvent("tick").render // "data: tick\n\n"
   * }}}
   */
  def apply[A](data: A): ServerSentEvent[A] =
    create(data, Maybe.absent, Maybe.absent, Maybe.absent)

  /**
   * Creates an event with the payload `data` and the `event:` field set to
   * `event`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `event` contains CR or LF
   */
  def apply[A](data: A, event: String): ServerSentEvent[A] =
    create(data, Maybe.present(validateSingleLineField(event, "event")), Maybe.absent, Maybe.absent)

  /**
   * Creates an event with the payload `data` and whichever of `event`, `id` and
   * `retry` are `Some`.
   *
   * {{{
   * ServerSentEvent.fromOptions("stock low", id = Some("7"), retry = Some(1000L)).render
   * // "id: 7\nretry: 1000\ndata: stock low\n\n"
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if `event` or `id` contains CR or LF, or `retry` is negative
   */
  def fromOptions[A](
    data: A,
    event: Option[String] = None,
    id: Option[String] = None,
    retry: Option[Long] = None
  ): ServerSentEvent[A] =
    create(
      data,
      Maybe.fromOption(event.map(validateSingleLineField(_, "event"))),
      Maybe.fromOption(id.map(validateSingleLineField(_, "id"))),
      Maybe.fromOption(retry)
    )

  /** Creates an event, checking only that `retry` is not negative. */
  private[http] def create[A](
    data: A,
    event: Maybe[String],
    id: Maybe[String],
    retry: Maybe[Long]
  ): ServerSentEvent[A] = {
    retry.fold(())(validateRetry)
    new ServerSentEvent(data, event, id, retry)
  }

  /** Returns `value`, or throws if it contains CR or LF. */
  private[http] def validateSingleLineField(value: String, fieldName: String): String = {
    if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
      throw new IllegalArgumentException(s"SSE $fieldName must not contain CR or LF characters")
    }
    value
  }

  private def validateRetry(millis: Long): Unit =
    if (millis < 0) {
      throw new IllegalArgumentException("SSE retry must be non-negative")
    }
}

/**
 * A type class that turns an SSE payload into the lines of its `data:` field;
 * [[ServerSentEvent#render]] writes one `data:` line per returned line.
 * Instances for `String` and `Chunk[String]` are built in. For your own type,
 * return lines without line breaks, or reuse [[SseDataEncoder.string]], which
 * splits them.
 *
 * {{{
 * import zio.blocks.chunk.Chunk
 *
 * final case class Progress(done: Int, total: Int)
 *
 * implicit val progressEncoder: SseDataEncoder[Progress] = new SseDataEncoder[Progress] {
 *   def lines(value: Progress): Chunk[String] = Chunk("done=" + value.done, "total=" + value.total)
 * }
 *
 * ServerSentEvent(Progress(3, 10), "progress").render
 * // "event: progress\ndata: done=3\ndata: total=10\n\n"
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/server-sent-event/#ssedataencoder SseDataEncoder]]
 */
trait SseDataEncoder[-A] {

  /**
   * Returns the `data:` lines for `value`. Each line is written as it is after
   * `data: `, so it must not contain CR or LF.
   */
  def lines(value: A): Chunk[String]
}

/** The built-in [[SseDataEncoder]] instances. */
object SseDataEncoder {

  /** Returns the implicit encoder for `A`. */
  def apply[A](implicit encoder: SseDataEncoder[A]): SseDataEncoder[A] = encoder

  /**
   * Splits a string at each `\n`, `\r` or `\r\n` into lines. A string without a
   * line break is one line, the empty string included, and a trailing line
   * break gives an empty last line.
   */
  implicit val string: SseDataEncoder[String] =
    new SseDataEncoder[String] {
      def lines(value: String): Chunk[String] = splitLines(value)
    }

  /**
   * Splits each element as [[string]] does and concatenates the lines. An empty
   * `Chunk` gives one empty line.
   */
  implicit val stringChunk: SseDataEncoder[Chunk[String]] =
    new SseDataEncoder[Chunk[String]] {
      def lines(value: Chunk[String]): Chunk[String] =
        if (value.isEmpty) Chunk.single("")
        else {
          val builder  = ChunkBuilder.make[String]()
          val iterator = value.iterator
          while (iterator.hasNext) {
            val split = splitLines(iterator.next())
            val lines = split.iterator
            while (lines.hasNext) {
              builder.addOne(lines.next())
            }
          }
          builder.result()
        }
    }

  /** Splits at `\n`, `\r` and `\r\n`, always returning at least one line. */
  private def splitLines(value: String): Chunk[String] = {
    val builder = ChunkBuilder.make[String]()
    var start   = 0
    var index   = 0
    val length  = value.length
    while (index < length) {
      val c = value.charAt(index)
      if (c == '\n' || c == '\r') {
        builder.addOne(value.substring(start, index))
        if (c == '\r' && index + 1 < length && value.charAt(index + 1) == '\n') {
          index += 1
        }
        start = index + 1
      }
      index += 1
    }
    builder.addOne(value.substring(start, length))
    builder.result()
  }
}
