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

package sink

import zio.blocks.streams.*
import zio.blocks.streams.NioSinks
import java.nio.ByteBuffer

object SinkSentinelLimitationExample extends App {
  println("=== Demonstrating Sentinel Value Limitation ===\n")

  println("Context: fromByteBufferLong uses Long.MaxValue as a sentinel to detect end-of-stream.")
  println("This means streams containing Long.MaxValue will silently truncate.\n")

  // Example: Stream with Long.MaxValue in the middle
  println("Test 1: Stream containing Long.MaxValue in the middle")
  println("-" * 60)

  val testData1 = List(
    100L,
    200L,
    Long.MaxValue, // ← This is the sentinel value
    300L,          // ← This will be silently dropped
    400L           // ← This will also be silently dropped
  )

  println(
    f"Stream data: ${testData1.map(v => if (v == Long.MaxValue) s"Long.MaxValue" else v.toString).mkString(", ")}"
  )
  println()

  val buffer1 = ByteBuffer.allocate(testData1.length * 8)
  Stream.fromIterable(testData1).run(NioSinks.fromByteBufferLong(buffer1))
  buffer1.rewind()

  println("Data written to buffer:")
  var count1 = 0
  while (buffer1.hasRemaining && count1 < testData1.length) {
    val value = buffer1.getLong()
    println(f"  [$count1] $value")
    count1 += 1
  }

  println()
  println(f"⚠️  Expected 5 values, but only wrote ${count1} to buffer!")
  println(f"✗ Data loss: Elements at indices 2, 3, 4 were silently dropped\n")

  // Recommendations
  println("\n=== Recommendations ===")
  println("1. If your data might contain Long.MaxValue:")
  println("   → Use Sink.create with manual buffering instead")
  println("   → Or use a different sink (Sink.foreach, Sink.foldLeft)")
  println()
  println("2. If you're certain your data excludes the sentinel:")
  println("   → fromByteBufferLong is fine and has zero boxing overhead")
  println()
  println("3. Check the sentinel values for each typed sink:")
  println("   → fromByteBufferInt: sentinel = Long.MinValue")
  println("   → fromByteBufferLong: sentinel = Long.MaxValue")
  println("   → fromByteBufferFloat/Double: sentinel = Double.MaxValue")
}
