//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using file SensorModel.scala PackedFormat.scala
// Run from this folder: scala-cli run Step13OverrideExample.scala
// Or from schema-examples: sbt "runMain customwireformat.Step13OverrideExample"

package customwireformat

import java.nio.ByteBuffer
import java.time.Instant
import zio.blocks.schema.derive.Deriver

/**
 * How to Write a Codec for a New Wire Format — Overriding One Type with
 * withInstance
 *
 * Replaces the packed codec of Instant with an epoch-milliseconds codec through
 * Deriver#withInstance, and compares the encoded size of one reading.
 */
object Step13OverrideExample {
  def main(args: Array[String]): Unit = {
    val epochMillis: PackedCodec[Instant] = new PackedCodec[Instant] {
      def write(value: Instant, out: ByteBuffer): Unit = out.putLong(value.toEpochMilli)
      def read(in: ByteBuffer): Instant                = Instant.ofEpochMilli(in.getLong())
    }
    val millisDeriver: Deriver[PackedCodec]       = PackedFormat.deriver.withInstance[Instant](epochMillis)
    val compactReadingCodec: PackedCodec[Reading] = Reading.schema.derive(millisDeriver)

    val firstReading = Batch.sample.readings.head
    println(s"default: ${Reading.schema.derive(PackedFormat).encodeToBuffer(firstReading).remaining} bytes")
    println(s"epoch millis: ${compactReadingCodec.encodeToBuffer(firstReading).remaining} bytes")
    println(compactReadingCodec.decode(compactReadingCodec.encodeToBuffer(firstReading)))
  }
}
