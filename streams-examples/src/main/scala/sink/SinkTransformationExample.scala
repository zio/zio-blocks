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

package sink

import zio.blocks.streams.*
import util.ShowExpr.show

object SinkTransformationExample extends App {
  println("=== Sink Transformations and Composition ===\n")

  // 1. contramap — pre-process input
  println("1. Sink.contramap — pre-process input elements:")
  val stringLengthSum: Sink[Nothing, String, Long] =
    Sink.sumInt.contramap[String](_.length)

  show("Stream(hello, world).run(Sink.sumInt.contramap(_.length))")(
    Stream("hello", "world").run(stringLengthSum)
  )

  // 2. contramap — change element type
  println("\n2. contramap to convert types:")
  val parseInts: Sink[Nothing, String, Long] =
    Sink.sumInt.contramap[String](_.toInt)

  show("Stream(\"10\", \"20\", \"30\").run(Sink.sumInt.contramap(_.toInt))")(
    Stream("10", "20", "30").run(parseInts)
  )

  // 3. map — transform result
  println("\n3. Sink.map — transform the result:")
  val countFormatted: Sink[Nothing, Any, String] =
    Sink.count.map(n => s"Processed $n elements")

  show("Stream(1, 2, 3).run(Sink.count.map(n => s\"Processed $n\"))")(
    Stream(1, 2, 3).run(countFormatted)
  )

  // 4. Chaining contramap + map
  println("\n4. Chaining contramap + map:")
  val pipeline = Sink.sumInt
    .contramap[String](_.length)
    .map(total => s"Total chars: $total")

  show("Stream(hi, hello).run(sumInt.contramap(_.length).map(format))")(
    Stream("hi", "hello").run(pipeline)
  )

  // 5. mapError — transform error channel
  println("\n5. Sink.mapError — transform errors:")

  sealed trait AppError
  case class ParseError(msg: String) extends AppError

  val failingSink = Sink.fail("raw error").mapError[AppError](msg => ParseError(msg))
  show("Stream(1).run(Sink.fail(\"raw\").mapError(ParseError(_)))")(
    Stream(1).run(failingSink)
  )

  // 6. fail — immediately fail
  println("\n6. Sink.fail — immediate failure:")
  show("Stream(1, 2, 3).run(Sink.fail(\"not ready\"))")(
    Stream(1, 2, 3).run(Sink.fail("not ready"))
  )

  // 7. Pipeline.andThenSink integration
  println("\n7. Pipeline.andThenSink — pipeline pre-processes before sink:")
  val cleanAndCollect =
    Pipeline
      .map[String, String](_.trim.toLowerCase)
      .andThenSink(Sink.collectAll[String])

  show("Stream(\"  Hello \", \" WORLD \").run(cleanAndCollect)")(
    Stream("  Hello ", " WORLD  ").run(cleanAndCollect)
  )

  // 8. Equivalence: via + run == andThenSink + run
  println("\n8. Equivalence law: via + run == andThenSink + run:")
  val pipe   = Pipeline.filter[Int](_ > 2).andThen(Pipeline.map[Int, Int](_ * 10))
  val source = Stream(1, 2, 3, 4, 5)

  val viaResult  = source.via(pipe).run(Sink.collectAll[Int])
  val sinkResult = source.run(pipe.andThenSink(Sink.collectAll[Int]))
  show("stream.via(pipe).run(sink)")(viaResult)
  show("stream.run(pipe.andThenSink(sink)) (matches)")(sinkResult)

  // 9. Composing multiple transformations into a reusable sink
  println("\n9. Reusable composed sink:")
  case class Metric(name: String, value: Double)

  val metricSumSink: Sink[Nothing, Metric, Double] =
    Sink.foldLeft(0.0)((acc, m: Metric) => acc + m.value)

  val metrics = Stream(
    Metric("cpu", 45.0),
    Metric("cpu", 67.0),
    Metric("cpu", 23.0)
  )
  show("Sum of metric values")(metrics.run(metricSumSink))

  val metricAvgSink: Sink[Nothing, Metric, Double] =
    Sink
      .foldLeft[(Double, Int), Metric]((0.0, 0)) { case ((sum, count), m) =>
        (sum + m.value, count + 1)
      }
      .map { case (sum, count) => if (count == 0) 0.0 else sum / count }

  show("Average of metric values")(metrics.run(metricAvgSink))
}
