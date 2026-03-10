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

// Demonstrates migrating a product type across API versions.
// The macro handles: Int → Long widening, and a new Option field
// that defaults to None when absent from the source type.
object IntoSchemaEvolutionExample extends App {

  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long, email: Option[String])

  val migrate = Into.derived[PersonV1, PersonV2]

  // age widens from Int to Long; email defaults to None
  show(migrate.into(PersonV1("Alice", 30)))
  show(migrate.into(PersonV1("Bob", 25)))
}
