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

object SinkAggregationExample extends App {
  println("=== Sink Aggregation and Search ===\n")

  // 1. foldLeft — general accumulation
  println("1. Sink.foldLeft — general accumulation:")
  val sum = Stream(1, 2, 3, 4, 5).run(Sink.foldLeft(0)(_ + _))
  show("Stream(1..5).run(Sink.foldLeft(0)(_ + _))")(sum)

  println("\n   foldLeft with string concatenation:")
  val concat = Stream("a", "b", "c").run(Sink.foldLeft("")(_ + _))
  show("Stream(a, b, c).run(Sink.foldLeft(\"\")(_ + _))")(concat)

  // 2. sumInt — typed numeric sum
  println("\n2. Sink.sumInt — returns Long to avoid overflow:")
  val intSum = Stream(1, 2, 3, 4, 5).run(Sink.sumInt)
  show("Stream(1..5).run(Sink.sumInt)")(intSum)

  // 3. sumDouble — typed floating point sum
  println("\n3. Sink.sumDouble:")
  val doubleSum = Stream(1.5, 2.5, 3.0).run(Sink.sumDouble)
  show("Stream(1.5, 2.5, 3.0).run(Sink.sumDouble)")(doubleSum)

  // 4. exists — short-circuits on first match
  println("\n4. Sink.exists — short-circuits on first match:")
  val hasNegative = Stream(1, 2, -3, 4).run(Sink.exists[Int](_ < 0))
  show("Stream(1, 2, -3, 4).run(Sink.exists(_ < 0))")(hasNegative)

  val noNegative = Stream(1, 2, 3, 4).run(Sink.exists[Int](_ < 0))
  show("Stream(1, 2, 3, 4).run(Sink.exists(_ < 0))")(noNegative)

  // 5. forall — all elements must match
  println("\n5. Sink.forall — all elements must match:")
  val allPositive = Stream(1, 2, 3).run(Sink.forall[Int](_ > 0))
  show("Stream(1, 2, 3).run(Sink.forall(_ > 0))")(allPositive)

  val notAllPositive = Stream(1, -2, 3).run(Sink.forall[Int](_ > 0))
  show("Stream(1, -2, 3).run(Sink.forall(_ > 0))")(notAllPositive)

  // 6. find — first element matching predicate
  println("\n6. Sink.find — first matching element:")
  val firstEven = Stream(1, 3, 4, 6, 8).run(Sink.find[Int](_ % 2 == 0))
  show("Stream(1, 3, 4, 6, 8).run(Sink.find(_ % 2 == 0))")(firstEven)

  val noMatch = Stream(1, 3, 5, 7).run(Sink.find[Int](_ % 2 == 0))
  show("Stream(1, 3, 5, 7).run(Sink.find(_ % 2 == 0))")(noMatch)

  // 7. foreach — side effects
  println("\n7. Sink.foreach — apply side effects:")
  val items  = scala.collection.mutable.Buffer[String]()
  val result = Stream("x", "y", "z").run(Sink.foreach[String](s => items += s))
  show("Stream(x, y, z).run(Sink.foreach(items += _))")(result)
  show("items collected")(items.toList)

  // 8. Complex aggregation: combine foldLeft with map
  println("\n8. Complex aggregation — average via foldLeft + map:")
  val average = Sink
    .foldLeft[(Int, Int), Int]((0, 0)) { case ((sum, count), x) =>
      (sum + x, count + 1)
    }
    .map { case (sum, count) =>
      if (count == 0) 0.0 else sum.toDouble / count
    }

  val avg = Stream(10, 20, 30, 40).run(average)
  show("Stream(10, 20, 30, 40).run(averageSink)")(avg)
}
