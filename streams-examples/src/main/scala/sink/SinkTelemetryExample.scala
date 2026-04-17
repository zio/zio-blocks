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
import java.io.RandomAccessFile
import scala.util.Using

object SinkTelemetryExample extends App {
  println("=== Streaming Telemetry to File Channel ===\n")

  // Simulated sensor readings (timestamp, temperature)
  case class SensorReading(timestamp: Long, temperature: Double) {
    override def toString: String = f"[$timestamp] $temperature%.2f°C"
  }

  // Generate mock sensor data
  val sensorReadings = List(
    SensorReading(1000L, 22.5),
    SensorReading(1100L, 23.1),
    SensorReading(1200L, 22.8),
    SensorReading(1300L, 23.4),
    SensorReading(1400L, 24.0)
  )

  println("Sensor readings to write:")
  sensorReadings.foreach(r => println(s"  $r"))
  println()

  // Write telemetry to file using NioSinks.fromChannel
  val filePath = "/tmp/telemetry.bin"

  println(s"Writing to $filePath...")
  Using(new RandomAccessFile(filePath, "rw")) { file =>
    val channel = file.getChannel
    channel.truncate(0) // Clear file

    // Convert sensor readings to bytes: 8 bytes timestamp + 8 bytes temperature
    val byteStream = Stream.fromIterable(sensorReadings).flatMap { reading =>
      val buf = java.nio.ByteBuffer.allocate(16)
      buf.putLong(reading.timestamp)
      buf.putDouble(reading.temperature)
      buf.flip()
      Stream.fromChunk(zio.blocks.chunk.Chunk.fromArray(buf.array()))
    }

    // Drain all bytes to file with buffering (8KB chunks)
    byteStream.run(NioSinks.fromChannel(channel, bufSize = 8192))

    println(s"✓ Wrote ${file.length()} bytes to disk")
  }.get

  // Read back and verify
  println("\nVerifying written data:")
  Using(new RandomAccessFile(filePath, "r")) { file =>
    val buf = java.nio.ByteBuffer.allocate((8 + 8) * sensorReadings.length)
    file.getChannel.read(buf)
    buf.rewind()

    var count = 0
    while (buf.remaining() >= 16) {
      val timestamp   = buf.getLong()
      val temperature = buf.getDouble()
      println(f"  [$timestamp] $temperature%.2f°C")
      count += 1
    }
    println(s"✓ Read back $count sensor readings")
  }.get

  println("\n=== Pattern Use Cases ===")
  println("This pattern is used in:")
  println("  • IoT telemetry platforms (time-series databases)")
  println("  • High-throughput logging systems")
  println("  • Sensor data aggregation pipelines")
  println("\nKey benefits:")
  println("  • Automatic buffering (8KB chunks)")
  println("  • Zero-copy writes to NIO channels")
  println("  • Backpressure handling built-in")
}
