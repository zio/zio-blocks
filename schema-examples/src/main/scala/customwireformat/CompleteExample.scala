//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using file SensorModel.scala PackedFormat.scala
// Run from this folder: scala-cli run CompleteExample.scala
// Or from schema-examples: sbt "runMain customwireformat.CompleteExample"

package customwireformat

import java.nio.ByteBuffer
import java.time.Instant
import zio.blocks.schema.derive.Deriver

/**
 * How to Write a Codec for a New Wire Format — Complete Example
 *
 * A gateway's batch goes through the packed format: derive the codecs, encode
 * the batch, decode it back, reject a cut-off message with a path, round-trip
 * the recursive zone tree and send timestamps as epoch milliseconds.
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {
    val batchCodec: PackedCodec[Batch] = Batch.schema.derive(PackedFormat)
    val batchBytes: ByteBuffer         = batchCodec.encodeToBuffer(Batch.sample)
    println(s"${PackedFormat.mimeType}: ${batchBytes.remaining} bytes")
    println(batchCodec.decode(batchBytes.duplicate()))

    val truncated = batchBytes.duplicate()
    truncated.limit(70)
    println(batchCodec.decode(truncated))

    val site = Zone(
      "north-site",
      Vector(DeviceId("gw-north")),
      List(
        Zone(
          "floor-1",
          Vector(DeviceId("t-17"), DeviceId("h-02")),
          List(Zone("cold-room", Vector(DeviceId("t-18")), Nil))
        ),
        Zone("floor-2", Vector(DeviceId("d-01")), Nil)
      )
    )
    val zoneCodec: PackedCodec[Zone] = Zone.schema.derive(PackedFormat)
    println(s"zone tree round-trips: ${zoneCodec.decode(zoneCodec.encodeToBuffer(site)) == Right(site)}")

    val epochMillis: PackedCodec[Instant] = new PackedCodec[Instant] {
      def write(value: Instant, out: ByteBuffer): Unit = out.putLong(value.toEpochMilli)
      def read(in: ByteBuffer): Instant                = Instant.ofEpochMilli(in.getLong())
    }
    val millisDeriver: Deriver[PackedCodec]   = PackedFormat.deriver.withInstance[Instant](epochMillis)
    val compactBatchCodec: PackedCodec[Batch] = Batch.schema.derive(millisDeriver)
    println(s"epoch millis: ${compactBatchCodec.encodeToBuffer(Batch.sample).remaining} bytes")
  }
}
