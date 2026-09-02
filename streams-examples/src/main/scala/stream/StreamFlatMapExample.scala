package stream

import zio.blocks.streams.Stream
import zio.sbt.ExprEval.show

object StreamFlatMapExample extends App {
  println("=== Stream FlatMap and Nested Streams ===\n")

  // Basic flatMap
  println("1. Basic flatMap - expand each element into a stream:")
  val expanded = Stream(1, 2, 3).flatMap(x => Stream(x, x * 10))
  show(expanded.runCollect)

  // FlatMap with different stream sizes
  println("\n2. FlatMap with varying sizes:")
  val varySizes = Stream(1, 2, 3).flatMap(x => Stream.range(0, x))
  show(varySizes.runCollect)

  // FlatMap with string expansion
  println("\n3. Expanding into string streams:")
  val ids          = Stream("a", "b")
  val expanded_ids = ids.flatMap(id => Stream(s"${id}_1", s"${id}_2", s"${id}_3"))
  show(expanded_ids.runCollect)

  // FlattenAll for deeply nested streams
  println("\n4. Flattening nested streams with flattenAll:")
  val nested = Stream(
    Stream(1, 2),
    Stream(3, 4),
    Stream(5, 6)
  )
  val flat = Stream.flattenAll(nested)
  show(flat.runCollect)

  // Sequential processing guarantees
  println("\n5. Sequential processing (important for side effects and resources):")
  var order   = scala.collection.mutable.Buffer[String]()
  val tracked = Stream(1, 2, 3).flatMap { x =>
    order += s"expand($x)"
    Stream(x, x + 100).tapEach(y => order += s"emit($y)")
  }
  val _ = tracked.runCollect
  show(order.toList)

  // FlatMap with error recovery
  println("\n6. FlatMap can propagate errors:")
  sealed trait Error
  case object InvalidId extends Error

  val mayFail = Stream(1, 2, -1, 3).flatMap { x =>
    if (x < 0) Stream.fail(InvalidId)
    else Stream(x, x * 2)
  }
  show(mayFail.runCollect)
}
