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

package zio.blocks.telemetry

/**
 * Sends formatted log text to a destination, one line per `write` call.
 * `log.writer` pairs one with a [[LogFormatter]] to add an output; implement
 * `write` to send lines somewhere the built-in [[StdoutWriter]],
 * [[StderrWriter]] and [[NoopWriter]] don't.
 *
 * {{{
 * // scala-3-only
 * import scala.collection.mutable.ListBuffer
 *
 * final class MemoryWriter extends LogWriter {
 *   val lines = ListBuffer.empty[String]
 *   def write(content: CharSequence): Unit = lines += content.toString
 * }
 *
 * val memory = new MemoryWriter
 * log.install(LoggerProvider.builder.build().get("checkout"))
 * log.writer(JsonLogFormatter, memory)
 *
 * log.warn("stock low", "sku" -> "sku-42")
 *
 * memory.lines.size // 1
 * memory.lines.head.contains("\"severityText\":\"WARN\"") // true
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/logging/log-writer/ LogWriter]]
 */
trait LogWriter {

  /**
   * Writes one formatted record. Each implementation adds the line separator
   * itself, if its destination needs one.
   */
  def write(content: CharSequence): Unit

  /** Pushes buffered text to the destination; does nothing by default. */
  def flush(): Unit = ()

  /**
   * Releases the destination; does nothing by default. `log.clearWriters` and
   * `log.removeAll` call it.
   */
  def close(): Unit = ()
}

/** Writes each line, followed by a line separator, to `System.out`. */
object StdoutWriter extends LogWriter {
  override def write(content: CharSequence): Unit = System.out.println(content)
}

/** Writes each line, followed by a line separator, to `System.err`. */
object StderrWriter extends LogWriter {
  override def write(content: CharSequence): Unit = System.err.println(content)
}

/** Discards every line; use it to measure the cost of formatting alone. */
object NoopWriter extends LogWriter {
  override def write(content: CharSequence): Unit = ()
}
