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

/**
 * A Server-Sent Event (SSE) data type for rendering SSE wire format.
 *
 * Represents a single SSE message with event type, optional id, optional retry
 * interval, and data payload. The `render` method produces the complete SSE
 * wire format suitable for transmission over HTTP.
 *
 * @param data
 *   the event data payload
 * @param eventType
 *   the event type identifier
 * @param id
 *   optional event id for client-side tracking
 * @param retryMillis
 *   optional retry interval in milliseconds
 */
final case class ServerSentEvent private (
  data: String,
  eventType: String,
  id: Option[String],
  retryMillis: Option[Long]
) {

  /**
   * Sets the event id.
   *
   * @param id
   *   the event id
   * @return
   *   a new ServerSentEvent with the id set
   */
  def withId(id: String): ServerSentEvent =
    copy(id = Some(id))

  /**
   * Sets the retry interval in milliseconds.
   *
   * @param millis
   *   the retry interval in milliseconds
   * @return
   *   a new ServerSentEvent with the retry interval set
   */
  def withRetry(millis: Long): ServerSentEvent =
    copy(retryMillis = Some(millis))

  /**
   * Renders the ServerSentEvent to SSE wire format.
   *
   * The format is:
   *   - `event: {eventType}\n`
   *   - `id: {id}\n` (only if id is Some)
   *   - `retry: {millis}\n` (only if retryMillis is Some)
   *   - For data: split on `\n`, each line gets `data: ` prefix
   *   - Trailing `\n` to end the event (blank line separator)
   *
   * @return
   *   the SSE wire format string
   */
  def render: String = {
    val sb = new java.lang.StringBuilder(256)
    renderTo(sb)
    sb.toString
  }

  private def renderTo(sb: java.lang.StringBuilder): Unit = {
    sb.append("event: ").append(eventType).append('\n')
    id.foreach(idValue => sb.append("id: ").append(idValue).append('\n'))
    retryMillis.foreach(millis => sb.append("retry: ").append(millis).append('\n'))

    var start  = 0
    var index  = 0
    val length = data.length
    while (index < length) {
      if (data.charAt(index) == '\n') {
        sb.append("data: ").append(data, start, index).append('\n')
        start = index + 1
      }
      index += 1
    }
    sb.append("data: ").append(data, start, length).append('\n')
    sb.append('\n')
  }
}

object ServerSentEvent {

  /**
   * Creates a new ServerSentEvent with the given data and event type.
   *
   * @param data
   *   the event data payload
   * @param eventType
   *   the event type identifier
   * @return
   *   a new ServerSentEvent
   */
  def apply(data: String, eventType: String): ServerSentEvent =
    new ServerSentEvent(data, eventType, None, None)
}
