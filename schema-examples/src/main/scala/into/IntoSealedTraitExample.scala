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

// Demonstrates migrating a sealed trait (coproduct) across versions.
// Cases are matched by name; field types are coerced automatically.
// The target may introduce new cases — source cases need not cover them all.
object IntoSealedTraitExample extends App {

  sealed trait EventV1
  object EventV1 {
    case class Created(id: String, count: Int) extends EventV1
    case class Deleted(id: String)             extends EventV1
  }

  sealed trait EventV2
  object EventV2 {
    case class Created(id: String, count: Long) extends EventV2 // Int → Long
    case class Deleted(id: String)              extends EventV2
    case class Archived(id: String)             extends EventV2 // new in V2
  }

  val migrate = Into.derived[EventV1, EventV2]

  // Created: count field widens from Int to Long
  show(migrate.into(EventV1.Created("e1", 42)))

  // Deleted: no field changes
  show(migrate.into(EventV1.Deleted("e2")))
}
