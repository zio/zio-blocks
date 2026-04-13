package pipeline

import zio.blocks.streams.*
import util.ShowExpr.show

object PipelineBasicUsageExample extends App {
  println("=== Pipeline Basic Usage ===\n")

  // 1. Pipeline.map — transform each element
  println("1. Pipeline.map — transform each element:")
  val doubler = Pipeline.map[Int, Int](_ * 2)
  show("Stream(1, 2, 3).via(Pipeline.map(_ * 2)).runCollect")(
    Stream(1, 2, 3).via(doubler).runCollect
  )

  // 2. Pipeline.map — cross-type transformation
  println("\n2. Pipeline.map — type-changing transformation:")
  val intToString = Pipeline.map[Int, String](n => s"item-$n")
  show("Stream(1, 2, 3).via(Pipeline.map(n => s\"item-$n\")).runCollect")(
    Stream(1, 2, 3).via(intToString).runCollect
  )

  // 3. Pipeline.filter — keep elements matching a predicate
  println("\n3. Pipeline.filter — keep matching elements:")
  val positives = Pipeline.filter[Int](_ > 0)
  show("Stream(-2, -1, 0, 1, 2).via(Pipeline.filter(_ > 0)).runCollect")(
    Stream(-2, -1, 0, 1, 2).via(positives).runCollect
  )

  // 4. Pipeline.collect — partial function (filter + map)
  println("\n4. Pipeline.collect — partial function transformation:")
  val extractInts = Pipeline.collect[Any, Int] { case n: Int => n * 10 }
  show("Stream(1, \"a\", 2, \"b\").via(Pipeline.collect { case n: Int => n * 10 }).runCollect")(
    Stream(1, "a", 2, "b").via(extractInts).runCollect
  )

  // 5. Pipeline.take — first n elements
  println("\n5. Pipeline.take — first n elements (short-circuits):")
  val firstThree = Pipeline.take[Int](3)
  show("Stream.range(0, 1000).via(Pipeline.take(3)).runCollect")(
    Stream.range(0, 1000).via(firstThree).runCollect
  )

  // 6. Pipeline.drop — skip first n elements
  println("\n6. Pipeline.drop — skip first n elements:")
  val skipTwo = Pipeline.drop[String](2)
  show("Stream(\"header\", \"subheader\", \"data1\", \"data2\").via(Pipeline.drop(2)).runCollect")(
    Stream("header", "subheader", "data1", "data2").via(skipTwo).runCollect
  )

  // 7. Pipeline.identity — pass-through (no-op)
  println("\n7. Pipeline.identity — pass-through (neutral element):")
  val noOp = Pipeline.identity[Int]
  show("Stream(1, 2, 3).via(Pipeline.identity).runCollect")(
    Stream(1, 2, 3).via(noOp).runCollect
  )

  // 8. Combining drop and take to get a range
  println("\n8. Combining drop and take for pagination:")
  val page2 = Pipeline.drop[Int](3).andThen(Pipeline.take(3))
  show("Stream.range(0, 10).via(drop(3).andThen(take(3))).runCollect")(
    Stream.range(0, 10).via(page2).runCollect
  )
}
