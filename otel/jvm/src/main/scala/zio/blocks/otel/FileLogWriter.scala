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

package zio.blocks.otel

import java.io.{FileOutputStream, IOException}
import java.nio.{ByteBuffer, CharBuffer}
import java.nio.channels.FileChannel
import java.nio.charset.{CharsetEncoder, CoderResult, StandardCharsets}
import java.nio.file.{Files, Path, Paths}

/**
 * Writes log output to a file using FileChannel + ByteBuffer for maximum
 * throughput. JVM only.
 *
 * Uses:
 *   - FileChannel for direct syscalls
 *   - ThreadLocal ByteBuffer (heap) for reuse across writes
 *   - ASCII fast path for most log lines
 *   - ThreadLocal CharsetEncoder for non-ASCII content
 *
 * Usage: val writer = FileLogWriter("logs/app.log") val emitter = new
 * FormattedLogEmitter(TextLogFormatter, writer)
 *
 * // Or with JSON: val jsonWriter = FileLogWriter(Paths.get("logs/app.json"),
 * bufferSize = 16384) val emitter = new FormattedLogEmitter(JsonLogFormatter,
 * jsonWriter)
 */
final class FileLogWriter private (
  private val channel: FileChannel,
  private val bufferSize: Int
) extends LogWriter {

  // ThreadLocal encoder + buffer — no allocation per write
  private val threadState: ThreadLocal[FileLogWriter.WriterState] =
    new ThreadLocal[FileLogWriter.WriterState] {
      override def initialValue(): FileLogWriter.WriterState =
        new FileLogWriter.WriterState(bufferSize)
    }

  override def write(content: CharSequence): Unit = {
    val state   = threadState.get()
    val buf     = state.buffer
    val encoder = state.encoder

    buf.clear()

    // Fast path: ASCII content (most log lines are ASCII)
    val len = content.length
    if (len <= buf.capacity - 1) { // -1 for newline
      var i        = 0
      var allAscii = true
      while (i < len && allAscii) {
        val c = content.charAt(i)
        if (c < 128) {
          buf.put(c.toByte)
        } else {
          allAscii = false
        }
        i += 1
      }

      if (allAscii) {
        // All ASCII — fast path complete
        buf.put('\n'.toByte)
        buf.flip()
        try {
          while (buf.hasRemaining) channel.write(buf)
        } catch {
          case e: IOException =>
            System.err.println("[zio-blocks-otel] file write error: " + e.getMessage)
        }
        return
      }

      // Had non-ASCII — fall back to encoder
      buf.clear()
    }

    // Slow path: use CharsetEncoder for non-ASCII or content > buffer
    val charBuf = CharBuffer.wrap(content)
    encoder.reset()
    try {
      var result = encoder.encode(charBuf, buf, true)
      while (result == CoderResult.OVERFLOW) {
        buf.flip()
        while (buf.hasRemaining) channel.write(buf)
        buf.clear()
        result = encoder.encode(charBuf, buf, true)
      }
      encoder.flush(buf)
      buf.put('\n'.toByte)
      buf.flip()
      while (buf.hasRemaining) channel.write(buf)
    } catch {
      case e: IOException =>
        System.err.println("[zio-blocks-otel] file write error: " + e.getMessage)
    }
  }

  override def flush(): Unit =
    try channel.force(false)
    catch { case e: IOException => System.err.println("[zio-blocks-otel] flush error: " + e.getMessage) }

  override def close(): Unit =
    try channel.close()
    catch { case e: IOException => System.err.println("[zio-blocks-otel] close error: " + e.getMessage) }
}

object FileLogWriter {

  private[otel] class WriterState(bufferSize: Int) {
    val buffer: ByteBuffer      = ByteBuffer.allocate(bufferSize) // heap buffer — faster for small writes
    val encoder: CharsetEncoder = StandardCharsets.UTF_8.newEncoder()
  }

  /**
   * Create a file writer with default settings (append mode, 8KB buffer).
   */
  def apply(path: String): FileLogWriter = apply(Paths.get(path))

  def apply(
    path: Path,
    append: Boolean = true,
    bufferSize: Int = 8192
  ): FileLogWriter = {
    // Create parent directories if they don't exist
    val parent = path.getParent
    if (parent != null) Files.createDirectories(parent)
    val fos     = new FileOutputStream(path.toFile, append)
    val channel = fos.getChannel
    new FileLogWriter(channel, bufferSize)
  }
}
