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

package writer

import zio.blocks.streams.io.Writer
import java.io.{ByteArrayOutputStream, StringWriter}

/**
 * Demonstrates I/O integration with Writer via fromOutputStream and fromWriter.
 * Shows how to write bytes to streams and characters to writers using the
 * Writer interface.
 */
object WriterIOAdapterExample extends App {

  println("=== Writer.fromOutputStream ===")
  val byteStream = new ByteArrayOutputStream()
  val byteWriter = Writer.fromOutputStream(byteStream)

  byteWriter.write(72.toByte)  // 'H'
  byteWriter.write(105.toByte) // 'i'
  byteWriter.write(33.toByte)  // '!'
  byteWriter.close()

  println(s"Output: ${byteStream.toString("UTF-8")}")

  println("\n=== writeBytes: bulk byte write ===")
  val byteStream2 = new ByteArrayOutputStream()
  val byteWriter2 = Writer.fromOutputStream(byteStream2)

  val message      = "Hello".getBytes("UTF-8")
  val bytesWritten = byteWriter2.writeBytes(message, 0, message.length)
  byteWriter2.close()

  println(s"Bytes written: $bytesWritten")
  println(s"Output: ${byteStream2.toString("UTF-8")}")

  println("\n=== Writer.fromWriter ===")
  val charStream = new StringWriter()
  val charWriter = Writer.fromWriter(charStream)

  charWriter.write('H')
  charWriter.write('e')
  charWriter.write('l')
  charWriter.write('l')
  charWriter.write('o')
  charWriter.close()

  println(s"Output: ${charStream.toString}")

  println("\n=== writeChar: individual character writes ===")
  val charStream2 = new StringWriter()
  val charWriter2 = Writer.fromWriter(charStream2)

  val greeting = "Hi!"
  for (c <- greeting) {
    val result = charWriter2.writeChar(c)
    println(s"Write '$c': $result")
  }
  charWriter2.close()

  println(s"Output: ${charStream2.toString}")

  println("\n=== Specialized numeric writes ===")
  val charStream3 = new StringWriter()
  val charWriter3 = Writer.fromWriter(charStream3)

  // Note: These specialized methods require the writer to be typed to accept them
  // For a demo, we'll just show the interface exists
  println("Specialized write methods available:")
  println("  - writeInt(value: Int)")
  println("  - writeLong(value: Long)")
  println("  - writeFloat(value: Float)")
  println("  - writeDouble(value: Double)")
  println("  - writeBoolean(value: Boolean)")
  println("  - writeShort(value: Short)")

  charWriter3.close()

  println("\n=== Error handling in I/O ===")
  val closedStream = new ByteArrayOutputStream()
  closedStream.close()
  val failingWriter = Writer.fromOutputStream(closedStream)

  val writeResult = failingWriter.write(65.toByte) // 'A'
  println(s"Write to closed stream: $writeResult")
  println(s"Writer is closed: ${failingWriter.isClosed}")
}
