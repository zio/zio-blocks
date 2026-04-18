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
import scala.math.pow

object SinkScientificComputingExample extends App {
  println("=== Batching Doubles for Scientific Computing ===\n")

  // Simulate raw sensor measurements that need calibration
  println("Scenario: Streaming voltage sensor measurements with calibration curve\n")

  val measurementCount = 10
  val bufferCapacity   = measurementCount * 8 // 8 bytes per Double

  println(s"Processing $measurementCount measurements...\n")

  // Generate raw voltage readings (0.0 to 0.09)
  val rawVoltages = (0 until measurementCount).map(i => (i * 0.01).toDouble).toList

  println("Raw measurements (voltage):")
  rawVoltages.zipWithIndex.foreach { case (v, i) =>
    println(f"  [$i] $v%.4f V")
  }
  println()

  // Process and calibrate measurements
  val buffer = processAndBufferMeasurements(measurementCount, bufferCapacity)

  println("After calibration (applied quadratic correction: V' = V × (1 + 0.05×V²)):\n")

  // Read back and display calibrated values
  buffer.rewind()
  var index = 0
  while (buffer.hasRemaining) {
    val calibrated = buffer.getDouble()
    println(f"  [$index] $calibrated%.6f V")
    index += 1
  }

  println("\n=== Pattern Use Cases ===")
  println("This pattern is used in:")
  println("  • Scientific instrumentation (analog-to-digital conversion)")
  println("  • Machine learning pipelines (sensor data → training batches)")
  println("  • Signal processing (raw signals → preprocessed data → computation)")
  println("\nKey benefits:")
  println("  • Zero-copy batch processing with DirectByteBuffer")
  println("  • Efficient numerical stream transformation")
  println("  • Memory-friendly for large datasets")

  // Process and buffer measurements using NioSinks
  def processAndBufferMeasurements(
    measurementCount: Int,
    bufferCapacity: Int
  ): ByteBuffer = {
    val buffer = ByteBuffer.allocateDirect(bufferCapacity)

    // Stream of raw voltage measurements (need calibration)
    val voltages = Stream.range(0, measurementCount).map(i => (i * 0.01).toDouble)

    // Apply calibration curve: quadratic correction
    // This simulates real sensor calibration with nonlinear response
    val calibrated = voltages.map { raw =>
      val calibrationFactor = 1.0 + (0.05 * pow(raw, 2))
      raw * calibrationFactor
    }

    // Write calibrated values directly to ByteBuffer using typed sink
    // This is much faster than element-by-element byte writing
    calibrated.run(NioSinks.fromByteBufferDouble(buffer))
    buffer.flip()
    buffer
  }
}
