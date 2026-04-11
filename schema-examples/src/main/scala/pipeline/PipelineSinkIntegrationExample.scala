package pipeline

import zio.blocks.streams.*
import util.ShowExpr.show

object PipelineSinkIntegrationExample extends App {
  println("=== Pipeline ↔ Sink Integration ===\n")

  // 1. Basic andThenSink usage
  println("1. Basic andThenSink — pre-process before collecting:")
  val doubler        = Pipeline.map[Int, Int](_ * 2)
  val collectDoubled = doubler.andThenSink(Sink.collectAll[Int])

  show("Stream(1, 2, 3).run(Pipeline.map(_ * 2).andThenSink(Sink.collectAll))")(
    Stream(1, 2, 3).run(collectDoubled)
  )

  // 2. Equivalence law: via + run == run + andThenSink
  println("\n2. Equivalence law: stream.via(p).run(sink) == stream.run(p.andThenSink(sink)):")
  val pipe   = Pipeline.filter[Int](_ > 2).andThen(Pipeline.map[Int, Int](_ * 10))
  val source = Stream(1, 2, 3, 4, 5)
  val sink   = Sink.collectAll[Int]

  val viaResult         = source.via(pipe).run(sink)
  val andThenSinkResult = source.run(pipe.andThenSink(sink))
  show("stream.via(pipe).run(sink)")(viaResult)
  show("stream.run(pipe.andThenSink(sink)) (should match)")(andThenSinkResult)

  // 3. Reusable pre-processing sink
  println("\n3. Reusable pre-processing sink:")
  val cleanString    = Pipeline.map[String, String](_.trim.toLowerCase)
  val collectCleaned = cleanString.andThenSink(Sink.collectAll[String])
  val countCleaned   = cleanString.andThenSink(Sink.count)

  val rawData = Stream("  Hello ", " WORLD  ", "  Scala  ")

  show("rawData.run(collectCleaned)")(rawData.run(collectCleaned))
  show("rawData.run(countCleaned)")(rawData.run(countCleaned))

  // 4. andThenSink with foldLeft
  println("\n4. Pipeline + foldLeft sink:")
  val sumPositives = Pipeline
    .filter[Int](_ > 0)
    .andThenSink(Sink.foldLeft(0)((acc, x) => acc + x))

  show("Stream(-5, 3, -2, 7, 1).run(filter(_ > 0).andThenSink(foldLeft(0)(_ + _)))")(
    Stream(-5, 3, -2, 7, 1).run(sumPositives)
  )

  // 5. andThenSink with head/find
  println("\n5. Pipeline + head/find sinks:")
  val firstEven = Pipeline.filter[Int](_ % 2 == 0).andThenSink(Sink.head[Int])

  show("Stream(1, 3, 4, 6).run(filter(even).andThenSink(head))")(
    Stream(1, 3, 4, 6).run(firstEven)
  )

  // 6. Multiple pipelines, same sink
  println("\n6. Multiple pipelines applied to the same sink:")
  val baseSink = Sink.collectAll[Int]

  val evens = Pipeline.filter[Int](_ % 2 == 0).andThenSink(baseSink)
  val odds  = Pipeline.filter[Int](_ % 2 != 0).andThenSink(baseSink)

  val nums = Stream(1, 2, 3, 4, 5, 6)
  show("nums.run(evens sink)")(nums.run(evens))
  show("nums.run(odds sink)")(nums.run(odds))

  // 7. Complex pipeline applied to sink
  println("\n7. Multi-stage pipeline applied to sink:")
  val processingPipe = Pipeline
    .filter[Int](_ > 0)
    .andThen(Pipeline.map[Int, Int](_ * 2))
    .andThen(Pipeline.take(3))

  val processedSum = processingPipe.andThenSink(Sink.foldLeft(0)(_ + _))

  show("Stream(-1, 5, 3, 8, 2, 9).run(filter+map+take → foldLeft)")(
    Stream(-1, 5, 3, 8, 2, 9).run(processedSum)
  )
}
