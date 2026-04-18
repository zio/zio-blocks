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

package stream

import zio.blocks.streams.Stream
import util.ShowExpr.show

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
    Stream(x, x + 100).tapEach(_ => order += s"emit($x)")
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
