package processbinarydata

import zio.blocks.streams._
import zio.blocks.streams.io.Writer

import java.nio.channels.WritableByteChannel
import java.nio.{ByteBuffer, ByteOrder}

/**
 * How to Process Binary Data Without Boxing — Capture Pushed Values With
 * Writers
 *
 * Collects samples that a driver pushes one at a time into two `ByteBuffer`s
 * through `NioWriters.fromByteBufferLong` and
 * `NioWriters.fromByteBufferDouble`, and writes each full block to its channel.
 * The recorder does not own the channels; the caller closes them.
 */
final class Recorder(times: WritableByteChannel, samples: WritableByteChannel, blockSize: Int) {
  private val timeBlock                 = ByteBuffer.allocate(blockSize * 8)
  private val sampleBlock               = ByteBuffer.allocate(blockSize * 8).order(ByteOrder.LITTLE_ENDIAN)
  private var timeOut: Writer[Long]     = NioWriters.fromByteBufferLong(timeBlock)
  private var sampleOut: Writer[Double] = NioWriters.fromByteBufferDouble(sampleBlock)

  def onSample(millis: Long, g: Double): Unit = {
    if (!timeOut.writeLong(millis)) { flush(); timeOut.writeLong(millis) }
    if (!sampleOut.writeDouble(g)) { flush(); sampleOut.writeDouble(g) }
  }

  // A ByteBuffer writer closes itself once its buffer is full, so each block gets a new one.
  def flush(): Unit = {
    drain(timeBlock, times)
    drain(sampleBlock, samples)
    timeOut = NioWriters.fromByteBufferLong(timeBlock)
    sampleOut = NioWriters.fromByteBufferDouble(sampleBlock)
  }

  private def drain(block: ByteBuffer, ch: WritableByteChannel): Unit = {
    block.flip()
    while (block.hasRemaining) ch.write(block)
    block.clear()
  }
}
