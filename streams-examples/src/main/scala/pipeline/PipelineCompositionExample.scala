package pipeline

import zio.blocks.streams.*
import util.ShowExpr.show

object PipelineCompositionExample extends App {
  println("=== Pipeline Composition ===\n")

  // 1. Basic andThen composition
  println("1. Composing filter + map with andThen:")
  val filterPositive = Pipeline.filter[Int](_ > 0)
  val double         = Pipeline.map[Int, Int](_ * 2)
  val composed       = filterPositive.andThen(double)

  show("Stream(-3, -1, 0, 2, 5).via(filter(_ > 0).andThen(map(_ * 2))).runCollect")(
    Stream(-3, -1, 0, 2, 5).via(composed).runCollect
  )

  // 2. Multi-stage pipeline
  println("\n2. Multi-stage pipeline (filter → map → take):")
  val multiStage = Pipeline
    .filter[Int](_ % 2 == 0)
    .andThen(Pipeline.map[Int, Int](_ * 10))
    .andThen(Pipeline.take(3))

  show("Stream(1..10).via(filter(even).andThen(map(*10)).andThen(take(3))).runCollect")(
    Stream.range(1, 11).via(multiStage).runCollect
  )

  // 3. Reusing the same pipeline across different streams
  println("\n3. Reusing the same pipeline across different streams:")
  val normalize = Pipeline.filter[Int](_ >= 0).andThen(Pipeline.map[Int, Double](_.toDouble / 100.0))

  val dataset1 = Stream(150, -20, 75, 200, -10)
  val dataset2 = Stream(50, 100, -5, 300)

  show("dataset1.via(normalize).runCollect")(dataset1.via(normalize).runCollect)
  show("dataset2.via(normalize).runCollect")(dataset2.via(normalize).runCollect)

  // 4. Category law: left identity
  println("\n4. Category law — left identity (identity andThen p == p):")
  val pipe = Pipeline.map[Int, Int](_ + 1)
  val data = Stream(1, 2, 3)

  val withIdentityL   = data.via(Pipeline.identity[Int].andThen(pipe)).runCollect
  val withoutIdentity = data.via(pipe).runCollect
  show("identity.andThen(p).result")(withIdentityL)
  show("p.result (should match)")(withoutIdentity)

  // 5. Category law: right identity
  println("\n5. Category law — right identity (p andThen identity == p):")
  val withIdentityR = data.via(pipe.andThen(Pipeline.identity[Int])).runCollect
  show("p.andThen(identity).result")(withIdentityR)
  show("p.result (should match)")(withoutIdentity)

  // 6. Category law: associativity
  println("\n6. Category law — associativity ((p andThen q) andThen r == p andThen (q andThen r)):")
  val p = Pipeline.filter[Int](_ > 0)
  val q = Pipeline.map[Int, Int](_ * 3)
  val r = Pipeline.take[Int](2)

  val leftGrouped  = (p.andThen(q)).andThen(r)
  val rightGrouped = p.andThen(q.andThen(r))

  val source = Stream(-1, 2, -3, 4, 5, 6)
  show("(p andThen q) andThen r")(source.via(leftGrouped).runCollect)
  show("p andThen (q andThen r) (should match)")(source.via(rightGrouped).runCollect)

  // 7. Building pipelines conditionally
  println("\n7. Building pipelines conditionally:")
  def buildPipeline(limit: Option[Int], onlyPositive: Boolean): Pipeline[Int, Int] = {
    var pipe: Pipeline[Int, Int] = Pipeline.identity[Int]
    if (onlyPositive) pipe = pipe.andThen(Pipeline.filter(_ > 0))
    limit.foreach(n => pipe = pipe.andThen(Pipeline.take(n.toLong)))
    pipe
  }

  val conditionalPipe = buildPipeline(limit = Some(3), onlyPositive = true)
  show("buildPipeline(limit=3, onlyPositive=true) on Stream(-1, 2, -3, 4, 5, 6)")(
    Stream(-1, 2, -3, 4, 5, 6).via(conditionalPipe).runCollect
  )

  val noPipe = buildPipeline(limit = None, onlyPositive = false)
  show("buildPipeline(limit=None, onlyPositive=false) on Stream(-1, 2, -3)")(
    Stream(-1, 2, -3).via(noPipe).runCollect
  )
}
