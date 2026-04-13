package stream

import zio.blocks.streams.Stream
import util.ShowExpr.show

object StreamBasicUsageExample extends App {
  println("=== Stream Basic Usage ===\n")

  // Construction from values
  println("1. Creating a stream from values:")
  val nums = Stream(1, 2, 3, 4, 5)
  show("Stream(1, 2, 3, 4, 5).runCollect")(nums.runCollect)

  // Map transformation
  println("\n2. Transforming with map:")
  val doubled = Stream(1, 2, 3).map(_ * 2)
  show("Stream(1, 2, 3).map(_ * 2).runCollect")(doubled.runCollect)

  // Filter operation
  println("\n3. Filtering elements:")
  val evens = Stream(1, 2, 3, 4, 5, 6).filter(_ % 2 == 0)
  show("Stream(1..6).filter(_ % 2 == 0).runCollect")(evens.runCollect)

  // Chaining operations
  println("\n4. Chaining multiple operations:")
  val result = Stream(1, 2, 3, 4, 5)
    .map(_ * 2)
    .filter(_ > 4)
    .runCollect
  show("Stream(1..5).map(_ * 2).filter(_ > 4).runCollect")(result)

  // Count operation
  println("\n5. Counting elements:")
  val count = Stream(1, 2, 3, 4, 5).count
  show("Stream(1..5).count")(count)

  // Take operation (short-circuiting)
  println("\n6. Taking first n elements (short-circuits):")
  val first3 = Stream.range(0, 1000).take(3).runCollect
  show("Stream.range(0, 1000).take(3).runCollect")(first3)

  // Drop operation
  println("\n7. Dropping first n elements:")
  val afterDrop = Stream(1, 2, 3, 4, 5).drop(2).runCollect
  show("Stream(1..5).drop(2).runCollect")(afterDrop)

  // Empty stream
  println("\n8. Working with empty streams:")
  val empty = Stream.empty.runCollect
  show("Stream.empty.runCollect")(empty)

  // Concatenation
  println("\n9. Concatenating streams:")
  val combined = (Stream(1, 2) ++ Stream(3, 4)).runCollect
  show("(Stream(1, 2) ++ Stream(3, 4)).runCollect")(combined)
}
