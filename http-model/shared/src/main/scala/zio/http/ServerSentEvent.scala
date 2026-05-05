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
 * A Server-Sent Event (SSE) envelope carrying typed data.
 *
 * The payload `A` is rendered into one or more `data:` lines via an
 * [[SseDataEncoder]]. The remaining fields (`event`, `id`, `retry`) are SSE
 * metadata owned by the envelope itself.
 */
final class ServerSentEvent[+A] private (
  val data: A,
  val eventType: Maybe[String],
  val eventId: Maybe[String],
  val retryMillis: Maybe[Long]
) {

  /** Sets the event field. */
  def event(event: String): ServerSentEvent[A] =
    ServerSentEvent.create(
      data,
      Maybe.present(ServerSentEvent.validateSingleLineField(event, "event")),
      eventId,
      retryMillis
    )

  /** Removes the event field. */
  def clearEvent: ServerSentEvent[A] =
    ServerSentEvent.create(data, Maybe.absent, eventId, retryMillis)

  /** Sets the event id. */
  def id(id: String): ServerSentEvent[A] =
    ServerSentEvent.create(
      data,
      eventType,
      Maybe.present(ServerSentEvent.validateSingleLineField(id, "id")),
      retryMillis
    )

  /** Sets the retry interval in milliseconds. */
  def retry(millis: Long): ServerSentEvent[A] =
    ServerSentEvent.create(data, eventType, eventId, Maybe.present(millis))

  /** Removes the retry field. */
  def clearRetry: ServerSentEvent[A] =
    ServerSentEvent.create(data, eventType, eventId, Maybe.absent)

  /** Renders this event to SSE wire format. */
  def render(using encoder: SseDataEncoder[A]): String = {
    val sb = new java.lang.StringBuilder(256)
    renderTo(sb)
    sb.toString
  }

  private def renderTo(sb: java.lang.StringBuilder)(using encoder: SseDataEncoder[A]): Unit = {
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

  override def equals(other: Any): Boolean =
    other match {
      case that: ServerSentEvent[?] =>
        data == that.data && eventType == that.eventType && eventId == that.eventId && retryMillis == that.retryMillis
      case _ => false
    }

  override def hashCode(): Int = {
    var result = data.##
    result = 31 * result + eventType.##
    result = 31 * result + eventId.##
    result = 31 * result + retryMillis.##
    result
  }

  override def toString: String =
    s"ServerSentEvent(data=$data, event=$eventType, id=$eventId, retry=$retryMillis)"
}

object ServerSentEvent {

  /** Creates a new Server-Sent Event without an explicit `event` field. */
  def apply[A](data: A): ServerSentEvent[A] =
    create(data, Maybe.absent, Maybe.absent, Maybe.absent)

  /** Creates a new Server-Sent Event with an explicit `event` field. */
  def apply[A](data: A, event: String): ServerSentEvent[A] =
    create(data, Maybe.present(validateSingleLineField(event, "event")), Maybe.absent, Maybe.absent)

  /** Creates a new Server-Sent Event from optional metadata fields. */
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

  private[http] def create[A](
    data: A,
    event: Maybe[String],
    id: Maybe[String],
    retry: Maybe[Long]
  ): ServerSentEvent[A] = {
    retry.fold(())(validateRetry)
    new ServerSentEvent(data, event, id, retry)
  }

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

/** Encodes typed SSE payloads into one or more `data:` lines. */
trait SseDataEncoder[-A] {
  def lines(value: A): Chunk[String]
}

object SseDataEncoder {

  def apply[A](implicit encoder: SseDataEncoder[A]): SseDataEncoder[A] = encoder

  implicit val string: SseDataEncoder[String] =
    new SseDataEncoder[String] {
      def lines(value: String): Chunk[String] = splitLines(value)
    }

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
