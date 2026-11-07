//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using file SensorModel.scala PackedFormat.scala
// Run from this folder: scala-cli run Step10RoundTripExample.scala
// Or from schema-examples: sbt "runMain customwireformat.Step10RoundTripExample"

package customwireformat

import java.nio.ByteBuffer

/**
 * How to Write a Codec for a New Wire Format — Deriving Codecs and Checking the
 * Round Trip
 *
 * Derives the packed codec of Batch through PackedFormat, encodes the sample
 * batch, decodes it back and checks that every reading round-trips. The model
 * and the format come from the shared files.
 */
object Step10RoundTripExample {
  def main(args: Array[String]): Unit = {
    val batchCodec: PackedCodec[Batch] = Batch.schema.derive(PackedFormat)
    val batchBytes: ByteBuffer         = batchCodec.encodeToBuffer(Batch.sample)

    println(s"${PackedFormat.mimeType}: ${batchBytes.remaining} bytes")
    println(batchCodec.decode(batchBytes.duplicate()))

    def roundTrips[A](codec: PackedCodec[A], value: A): Boolean = {
      val bytes = codec.encodeToBuffer(value)
      codec.decode(bytes) == Right(value) && !bytes.hasRemaining
    }

    val readingCodec: PackedCodec[Reading] = Reading.schema.derive(PackedFormat)
    Batch.sample.readings.foreach { reading =>
      println(s"${reading.measurement} round-trips: ${roundTrips(readingCodec, reading)}")
    }
  }
}
