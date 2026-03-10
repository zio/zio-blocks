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

package into

import zio.blocks.schema.Into
import util.ShowExpr.show

// Demonstrates automatic collection reshaping.
// Into composes through List, Vector, Set, Map, and Option,
// applying element-level type coercion at each step.
object IntoCollectionsExample extends App {

  // List → Vector with Int → Long element widening
  show(Into[List[Int], Vector[Long]].into(List(1, 2, 3)))

  // List → Set removes duplicates
  show(Into[List[Int], Set[Long]].into(List(1, 2, 2, 3)))

  // Map with key and value coercion
  show(Into[Map[String, Int], Map[String, Long]].into(Map("a" -> 1, "b" -> 2)))

  // Option element coercion — Some and None both handled
  show(Into[Option[Int], Option[Long]].into(Some(42)))
  show(Into[Option[Int], Option[Long]].into(None))

  // Case class with a collection field — the field conversion is derived automatically
  case class OrderV1(id: String, quantities: List[Int])
  case class OrderV2(id: String, quantities: Vector[Long])

  show(Into.derived[OrderV1, OrderV2].into(OrderV1("order-1", List(5, 10, 3))))
}
