package sink

import zio.blocks.streams.*
import zio.blocks.chunk.Chunk
import util.ShowExpr.show

object SinkBasicUsageExample extends App {
  println("=== Sink Basic Usage ===\n")

  val data = Stream(1, 2, 3, 4, 5)

  // 1. Sink.drain — discard all elements
  println("1. Sink.drain — discard all elements:")
  show("Stream(1..5).run(Sink.drain)")(data.run(Sink.drain))

  // 2. Sink.count — count elements
  println("\n2. Sink.count — count elements:")
  show("Stream(1..5).run(Sink.count)")(data.run(Sink.count))

  // 3. Sink.collectAll — collect into Chunk
  println("\n3. Sink.collectAll — collect into Chunk:")
  show("Stream(1..5).run(Sink.collectAll)")(data.run(Sink.collectAll[Int]))

  // 4. Sink.head — first element
  println("\n4. Sink.head — first element:")
  show("Stream(1..5).run(Sink.head)")(data.run(Sink.head[Int]))

  println("\n   Sink.head on empty stream:")
  show("Stream.empty.run(Sink.head)")(Stream.empty.run(Sink.head[Int]))

  // 5. Sink.last — last element
  println("\n5. Sink.last — last element:")
  show("Stream(1..5).run(Sink.last)")(data.run(Sink.last[Int]))

  // 6. Sink.take — first n elements
  println("\n6. Sink.take — first n elements:")
  show("Stream(1..5).run(Sink.take(3))")(data.run(Sink.take(3)))

  // 7. take on a large stream (short-circuits)
  println("\n7. Sink.take short-circuits (only reads 3 of 1000):")
  show("Stream.range(0, 1000).run(Sink.take(3))")(Stream.range(0, 1000).run(Sink.take(3)))

  // 8. Combining stream operations with sinks
  println("\n8. Combining stream operations with explicit sinks:")
  val result = Stream(1, 2, 3, 4, 5)
    .filter(_ % 2 == 0)
    .run(Sink.collectAll[Int])
  show("Stream(1..5).filter(even).run(Sink.collectAll)")(result)

  // 9. Equivalence with convenience methods
  println("\n9. Stream convenience methods delegate to sinks:")
  show("stream.runCollect == stream.run(Sink.collectAll)")(
    data.runCollect == data.run(Sink.collectAll[Int])
  )
  show("stream.count == stream.run(Sink.count)")(
    data.count == data.run(Sink.count)
  )
}
