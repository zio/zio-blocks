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

object StreamBasicUsageExample extends App {
  println("=== Stream Basic Usage ===\n")

  // Construction from values
  println("1. Creating a stream from values:")
  val nums = Stream(1, 2, 3, 4, 5)
  show(nums.runCollect)

  // Map transformation
  println("\n2. Transforming with map:")
  val doubled = Stream(1, 2, 3).map(_ * 2)
  show(doubled.runCollect)

  // Filter operation
  println("\n3. Filtering elements:")
  val evens = Stream(1, 2, 3, 4, 5, 6).filter(_ % 2 == 0)
  show(evens.runCollect)

  // Chaining operations
  println("\n4. Chaining multiple operations:")
  val result = Stream(1, 2, 3, 4, 5)
    .map(_ * 2)
    .filter(_ > 4)
    .runCollect
  show(result)

  // Count operation
  println("\n5. Counting elements:")
  val count = Stream(1, 2, 3, 4, 5).count
  show(count)

  // Take operation (short-circuiting)
  println("\n6. Taking first n elements (short-circuits):")
  val first3 = Stream.range(0, 1000).take(3).runCollect
  show(first3)

  // Drop operation
  println("\n7. Dropping first n elements:")
  val afterDrop = Stream(1, 2, 3, 4, 5).drop(2).runCollect
  show(afterDrop)

  // Empty stream
  println("\n8. Working with empty streams:")
  val empty = Stream.empty.runCollect
  show(empty)

  // Concatenation
  println("\n9. Concatenating streams:")
  val combined = (Stream(1, 2) ++ Stream(3, 4)).runCollect
  show(combined)
}
