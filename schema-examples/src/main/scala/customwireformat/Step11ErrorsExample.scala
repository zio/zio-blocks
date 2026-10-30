//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using file SensorModel.scala PackedFormat.scala
// Run from this folder: scala-cli run Step11ErrorsExample.scala
// Or from schema-examples: sbt "runMain customwireformat.Step11ErrorsExample"

package customwireformat

import java.nio.ByteBuffer
import java.util.UUID
import scala.util.Try
import zio.blocks.schema._

final case class FirmwareUpdate(device: DeviceId, release: UUID)

object FirmwareUpdate {
  implicit val schema: Schema[FirmwareUpdate] = Schema.derived
}

/**
 * How to Write a Codec for a New Wire Format — Reporting Errors with Paths
 *
 * Decodes a cut-off batch, a batch with an invalid device id and a reading with
 * a corrupted case index, and prints each error with its path. Then derives a
 * codec for a type with a UUID field, which the format rejects.
 */
object Step11ErrorsExample {
  def main(args: Array[String]): Unit = {
    // Setup from section 10: the codec of Batch
    val batchCodec: PackedCodec[Batch] = Batch.schema.derive(PackedFormat)

    val truncated: ByteBuffer = batchCodec.encodeToBuffer(Batch.sample)
    truncated.limit(70)
    println(batchCodec.decode(truncated))

    val longIdBatch =
      Batch.sample.copy(readings = List(Batch.sample.readings.head.copy(device = DeviceId("north-wing-probe-07"))))
    println(batchCodec.decode(batchCodec.encodeToBuffer(longIdBatch)))

    val readingCodec: PackedCodec[Reading] = Reading.schema.derive(PackedFormat)
    val corrupted: ByteBuffer              = readingCodec.encodeToBuffer(Batch.sample.readings.head)
    corrupted.putInt(20, 7)
    println(readingCodec.decode(corrupted))

    println(Try(FirmwareUpdate.schema.derive(PackedFormat)))
  }
}
