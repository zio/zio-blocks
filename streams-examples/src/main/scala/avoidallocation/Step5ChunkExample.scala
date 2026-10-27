//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-chunk:0.0.56
//> using options -experimental
//> using file Allocation.scala
//> using file TickData.scala
// Run from this folder:      scala-cli run Step5ChunkExample.scala
// Or from streams-examples:  sbt "runMain avoidallocation.Step5ChunkExample"

package avoidallocation

import avoidallocation.TickData._
import zio.blocks.chunk.{Chunk, ChunkBuilder}

/**
 * How to Avoid Allocation on Hot Paths — Read and Build Chunks Without Boxing
 *
 * Wraps the price array instead of copying it, sums a chunk with `Chunk#long`
 * instead of `foldLeft`, and builds a chunk with a sized `ChunkBuilder.Long`.
 */
object Step5ChunkExample {

  val priceChunk = Chunk.fromArray(prices)

  def totalMicros(chunk: Chunk[Long]): Long = {
    var sum = 0L
    var i   = 0
    while (i < chunk.length) {
      sum += chunk.long(i)
      i += 1
    }
    sum
  }

  def pricesAbove(limitMicros: Long): Chunk[Long] = {
    val builder = new ChunkBuilder.Long
    builder.sizeHint(prices.length)
    var i = 0
    while (i < prices.length) {
      if (prices(i) > limitMicros) builder.addOne(prices(i))
      i += 1
    }
    builder.result()
  }

  def main(args: Array[String]): Unit = {
    val copyBytes = Allocation.bytesPerRun(Chunk.from(prices))
    val wrapBytes = Allocation.bytesPerRun(Chunk.fromArray(prices))

    println(s"Chunk.from:      $copyBytes bytes per batch")
    println(s"Chunk.fromArray: $wrapBytes bytes per batch")

    println(s"foldLeft total:    ${priceChunk.foldLeft(0L)(_ + _)}")
    println(s"totalMicros total: ${totalMicros(priceChunk)}")
    val foldBytes = Allocation.bytesPerRun(priceChunk.foldLeft(0L)(_ + _))
    val loopBytes = Allocation.bytesPerRun(totalMicros(priceChunk))
    println(s"foldLeft:    ${foldBytes.toDouble / tickCount} bytes per price")
    println(s"totalMicros: ${loopBytes.toDouble / tickCount} bytes per price")

    println(s"prices above 188000000: ${pricesAbove(188000000L).length}")
    val genericBytes = Allocation.bytesPerRun {
      val builder = Chunk.newBuilder[Long]
      var i       = 0
      while (i < prices.length) { builder.addOne(prices(i)); i += 1 }
      builder.result()
    }
    val longBytes = Allocation.bytesPerRun(pricesAbove(0L))
    println(s"Chunk.newBuilder[Long]:   ${genericBytes.toDouble / tickCount} bytes per price")
    println(s"sized ChunkBuilder.Long:  ${longBytes.toDouble / tickCount} bytes per price")
  }
}
