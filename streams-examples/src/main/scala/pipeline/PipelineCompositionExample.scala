/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  show(Stream(-3, -1, 0, 2, 5).via(composed).runCollect
  )

  // 2. Multi-stage pipeline
  println("\n2. Multi-stage pipeline (filter → map → take):")
  val multiStage = Pipeline
    .filter[Int](_ % 2 == 0)
    .andThen(Pipeline.map[Int, Int](_ * 10))
    .andThen(Pipeline.take(3))

  show(Stream.range(1, 11).via(multiStage).runCollect
  )

  // 3. Reusing the same pipeline across different streams
  println("\n3. Reusing the same pipeline across different streams:")
  val normalize = Pipeline.filter[Int](_ >= 0).andThen(Pipeline.map[Int, Double](_.toDouble / 100.0))

  val dataset1 = Stream(150, -20, 75, 200, -10)
  val dataset2 = Stream(50, 100, -5, 300)

  show(dataset1.via(normalize).runCollect)
  show(dataset2.via(normalize).runCollect)

  // 4. Category law: left identity
  println("\n4. Category law — left identity (identity andThen p == p):")
  val pipe = Pipeline.map[Int, Int](_ + 1)
  val data = Stream(1, 2, 3)

  val withIdentityL   = data.via(Pipeline.identity[Int].andThen(pipe)).runCollect
  val withoutIdentity = data.via(pipe).runCollect
  show(withIdentityL)
  show(withoutIdentity)

  // 5. Category law: right identity
  println("\n5. Category law — right identity (p andThen identity == p):")
  val withIdentityR = data.via(pipe.andThen(Pipeline.identity[Int])).runCollect
  show(withIdentityR)
  show(withoutIdentity)

  // 6. Category law: associativity
  println("\n6. Category law — associativity ((p andThen q) andThen r == p andThen (q andThen r)):")
  val p = Pipeline.filter[Int](_ > 0)
  val q = Pipeline.map[Int, Int](_ * 3)
  val r = Pipeline.take[Int](2)

  val leftGrouped  = (p.andThen(q)).andThen(r)
  val rightGrouped = p.andThen(q.andThen(r))

  val source = Stream(-1, 2, -3, 4, 5, 6)
  show(source.via(leftGrouped).runCollect)
  show(source.via(rightGrouped).runCollect)

  // 7. Building pipelines conditionally
  println("\n7. Building pipelines conditionally:")
  def buildPipeline(limit: Option[Int], onlyPositive: Boolean): Pipeline[Int, Int] = {
    var pipe: Pipeline[Int, Int] = Pipeline.identity[Int]
    if (onlyPositive) pipe = pipe.andThen(Pipeline.filter(_ > 0))
    limit.foreach(n => pipe = pipe.andThen(Pipeline.take(n.toLong)))
    pipe
  }

  val conditionalPipe = buildPipeline(limit = Some(3), onlyPositive = true)
  show(Stream(-1, 2, -3, 4, 5, 6).via(conditionalPipe).runCollect
  )

  val noPipe = buildPipeline(limit = None, onlyPositive = false)
  show(Stream(-1, 2, -3).via(noPipe).runCollect
  )
}
