package stream

import zio.blocks.streams.Stream
import util.ShowExpr.show

object StreamWindowingExample extends App {
  println("=== Stream Windowing and Stateful Transformations ===\n")

  // Grouped - fixed-size windows
  println("1. Grouping into fixed-size chunks:")
  val nums    = Stream(1, 2, 3, 4, 5, 6, 7)
  val grouped = nums.grouped(3)
  show("Stream(1..7).grouped(3).runCollect")(grouped.runCollect)

  // Grouped with incomplete last chunk
  println("\n2. Last chunk may be smaller:")
  val ungrouped = Stream(1, 2, 3, 4, 5).grouped(2)
  show("Stream(1..5).grouped(2).runCollect")(ungrouped.runCollect)

  // Sliding window
  println("\n3. Sliding window (default step = 1):")
  val sliding1 = Stream(1, 2, 3, 4, 5).sliding(3)
  show("Stream(1..5).sliding(3).runCollect")(sliding1.runCollect)

  // Sliding with custom step
  println("\n4. Sliding window with step > 1:")
  val sliding2 = Stream(1, 2, 3, 4, 5, 6, 7, 8).sliding(3, step = 2)
  show("Stream(1..8).sliding(3, step = 2).runCollect")(sliding2.runCollect)

  // Scan - running aggregate
  println("\n5. Scan for running sum (accumulator pattern):")
  val cumsum = Stream(1, 2, 3, 4, 5).scan(0)(_ + _)
  show("Stream(1..5).scan(0)(_ + _).runCollect")(cumsum.runCollect)

  // Scan for running product
  println("\n6. Scan for running product:")
  val cumprod = Stream(1, 2, 3, 4).scan(1)(_ * _)
  show("Stream(1..4).scan(1)(_ * _).runCollect")(cumprod.runCollect)

  // MapAccum - accumulator + transform
  println("\n7. MapAccum for indexed transformation:")
  val indexed = Stream("a", "b", "c").mapAccum(0)((idx, x) => (idx + 1, (idx, x)))
  show(
    """Stream("a", "b", "c")
      |  .mapAccum(0)((idx, x) => (idx+1, (idx, x)))
      |  .runCollect""".stripMargin
  )(indexed.runCollect)

  // MapAccum with state structure
  println("\n8. MapAccum with complex state:")
  case class Stats(count: Int, sum: Int, max: Int)

  val stats = Stream(5, 3, 8, 2, 9).mapAccum(Stats(0, 0, Int.MinValue)) { case (s, x) =>
    (
      Stats(s.count + 1, s.sum + x, math.max(s.max, x)),
      (x, Stats(s.count + 1, s.sum + x, math.max(s.max, x)))
    )
  }
  show(
    """Stream(5, 3, 8, 2, 9)
      |  .mapAccum(Stats)(compute new state)
      |  .runCollect""".stripMargin
  )(stats.runCollect)

  // Combining windowing with filtering
  println("\n9. Windowing + filtering (only windows with sum > 5):")
  val filtered = Stream(1, 2, 3, 4, 5, 6)
    .sliding(3, step = 1)
    .filter(chunk => chunk.foldLeft(0)(_ + _) > 5)
  show(
    """Stream(1..6)
      |  .sliding(3)
      |  .filter(sum > 5)
      |  .runCollect""".stripMargin
  )(filtered.runCollect)

  // Chaining scan with other operations
  println("\n10. Scan + filter for conditional processing:")
  val conditional = Stream(1, 1, 2, 1, 1, 3)
    .scan(0)(_ + _)
    .filter(_ >= 3) // emit when cumsum >= 3
  show(
    """Stream(1, 1, 2, 1, 1, 3)
      |  .scan(0)(_ + _)
      |  .filter(_ >= 3)
      |  .runCollect""".stripMargin
  )(conditional.runCollect)

  // Intersperse - useful with grouping
  println("\n11. Intersperse (insert separator between elements):")
  val separated = Stream(1, 2, 3).intersperse(0)
  show("Stream(1, 2, 3).intersperse(0).runCollect")(separated.runCollect)

  // Grouped + intersperse for row formatting
  println("\n12. Grouped + intersperse for CSV-like output:")
  val rows = Stream(1, 2, 3, 4, 5, 6)
    .grouped(2)
    .map(chunk => chunk.toList.mkString(","))
    .intersperse("\n")
  show(
    """Stream(1..6)
      |  .grouped(2)
      |  .map(chunk => mkString(","))
      |  .intersperse("\\n")
      |  .runCollect""".stripMargin
  )(rows.runCollect)
}
