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

object SinkSentinelGuardExample extends App {
  println("=== Sentinel Collisions Are Rejected Loudly (Never Silently) ===\n")

  println("Context: the typed NIO sinks use a primitive sentinel (e.g. Long.MaxValue for")
  println("fromByteBufferLong) to detect end-of-stream, keeping the drain loop a single")
  println("primitive comparison per element — zero boxing, zero allocation. This is a")
  println("deliberate performance choice (see AGENTS.md, Sentinel performance policy).")
  println("If your stream contains the sentinel value itself, the sink does NOT silently")
  println("truncate: it detects the collision at zero hot-path cost (one out-of-band EOF")
  println("flag check after the loop exits) and throws IllegalArgumentException.\n")

  // Example 1: normal data drains at full speed
  println("Test 1: Stream without sentinel values drains completely")
  println("-" * 60)

  val safeData = List(100L, 200L, 300L, 400L, 500L)
  val buffer1  = ByteBuffer.allocate(safeData.length * 8)
  Stream.fromIterable(safeData).run(NioSinks.fromByteBufferLong(buffer1))
  buffer1.flip()

  var count1 = 0
  while (buffer1.hasRemaining) {
    println(f"  [$count1] ${buffer1.getLong()}")
    count1 += 1
  }
  println(f"\n✓ All ${count1} values written\n")

  // Example 2: a sentinel-valued element is rejected with a clear error
  println("Test 2: Stream containing Long.MaxValue is rejected, not truncated")
  println("-" * 60)

  val riskyData = List(100L, 200L, Long.MaxValue, 300L, 400L)
  println(
    f"Stream data: ${riskyData.map(v => if (v == Long.MaxValue) "Long.MaxValue" else v.toString).mkString(", ")}\n"
  )

  val buffer2 = ByteBuffer.allocate(riskyData.length * 8)
  try {
    Stream.fromIterable(riskyData).run(NioSinks.fromByteBufferLong(buffer2))
    println("✗ UNEXPECTED: drain completed without error")
  } catch {
    case e: IllegalArgumentException =>
      println(s"✓ Rejected loudly: ${e.getMessage}")
  }

  // Recommendations
  println("\n=== Recommendations ===")
  println("1. If your data might contain the sentinel value (Long.MaxValue for the Long")
  println("   sink, Double.MaxValue for the Double sink):")
  println("   → Use a generic sink (Sink.collectAll, Sink.foreach, Sink.foldLeft) — these")
  println("     use an out-of-band object sentinel and handle every value")
  println("2. Otherwise the typed sinks are maximally fast: a single primitive comparison")
  println("   per element, zero boxing, zero allocation")
  println("3. Either way, data is never silently dropped — a collision throws")
  println()
  println("Sentinels per typed sink:")
  println("   → fromByteBufferInt: sentinel = Long.MinValue (outside Int range — no collision possible)")
  println("   → fromByteBufferLong: sentinel = Long.MaxValue (collision throws)")
  println("   → fromByteBufferFloat: sentinel = Double.MaxValue (outside Float range — no collision possible)")
  println("   → fromByteBufferDouble: sentinel = Double.MaxValue (collision throws)")
}
