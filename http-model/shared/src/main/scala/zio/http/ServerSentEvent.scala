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
final case class ServerSentEvent[+A] private (
  data: A,
  event: Maybe[String],
  id: Maybe[String],
  retry: Maybe[Long]
) {

  /** Sets the event field. */
  def withEvent(event: String): ServerSentEvent[A] =
    copy(event = Maybe.present(ServerSentEvent.validateSingleLineField(event, "event")))

  /** Removes the event field. */
  def withoutEvent: ServerSentEvent[A] =
    copy(event = Maybe.absent)

  /** Sets the event id. */
  def withId(id: String): ServerSentEvent[A] =
    copy(id = Maybe.present(ServerSentEvent.validateSingleLineField(id, "id")))

  /** Sets the retry interval in milliseconds. */
  def withRetry(millis: Long): ServerSentEvent[A] =
    copy(retry = Maybe.present(millis))

  /** Removes the retry field. */
  def withoutRetry: ServerSentEvent[A] =
    copy(retry = Maybe.absent)

  /** Renders this event to SSE wire format. */
  def render(using encoder: SseDataEncoder[A]): String = {
    val sb = new java.lang.StringBuilder(256)
    renderTo(sb)
    sb.toString
  }

  private def renderTo(sb: java.lang.StringBuilder)(using encoder: SseDataEncoder[A]): Unit = {
    event.fold(())(eventValue => sb.append("event: ").append(eventValue).append('\n'))
    id.fold(())(idValue => sb.append("id: ").append(idValue).append('\n'))
    retry.fold(())(millis => sb.append("retry: ").append(millis).append('\n'))

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
}

object ServerSentEvent {

  /** Creates a new Server-Sent Event without an explicit `event` field. */
  def apply[A](data: A): ServerSentEvent[A] =
    new ServerSentEvent(data, Maybe.absent, Maybe.absent, Maybe.absent)

  /** Creates a new Server-Sent Event with an explicit `event` field. */
  def apply[A](data: A, event: String): ServerSentEvent[A] =
    new ServerSentEvent(data, Maybe.present(validateSingleLineField(event, "event")), Maybe.absent, Maybe.absent)

  /** Creates a new Server-Sent Event from optional metadata fields. */
  def fromOptions[A](
    data: A,
    event: Option[String] = None,
    id: Option[String] = None,
    retry: Option[Long] = None
  ): ServerSentEvent[A] =
    new ServerSentEvent(
      data,
      Maybe.fromOption(event.map(validateSingleLineField(_, "event"))),
      Maybe.fromOption(id.map(validateSingleLineField(_, "id"))),
      Maybe.fromOption(retry)
    )

  private[http] def validateSingleLineField(value: String, fieldName: String): String = {
    if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
      throw new IllegalArgumentException(s"SSE $fieldName must not contain CR or LF characters")
    }
    value
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
        if (value.isEmpty) Chunk.single("") else value
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
