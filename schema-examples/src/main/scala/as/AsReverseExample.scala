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

package as

import zio.blocks.schema.{As, Into}
import util.ShowExpr.show

// As#reverse returns a new As[B, A] whose into and from are swapped.
// No data is copied — it is a zero-cost view over the original instance.
object AsReverseExample extends App {

  case class StorageId(value: Int)   // compact internal representation
  case class ExternalId(value: Long) // wide external API representation

  val toExternal: As[StorageId, ExternalId] = As.derived[StorageId, ExternalId]
  val toInternal: As[ExternalId, StorageId] = toExternal.reverse

  // Reversed into: ExternalId → StorageId (narrowing — validates at runtime)
  show(toInternal.into(ExternalId(42L)))
  show(toInternal.into(ExternalId(Long.MaxValue)))

  // Reversed from: StorageId → ExternalId (widening — always succeeds)
  show(toInternal.from(StorageId(99)))
}

// As.reverseInto synthesises Into[B, A] from any implicit As[A, B] in scope.
// Libraries that only require Into automatically gain the reverse direction.
object AsReverseIntoExample extends App {

  case class StorageId(value: Int)
  case class ExternalId(value: Long)

  implicit val idAs: As[StorageId, ExternalId] = As.derived[StorageId, ExternalId]

  import As.reverseInto

  // reverseInto materialises Into[ExternalId, StorageId] from the implicit As
  val toInternal: Into[ExternalId, StorageId] = reverseInto[StorageId, ExternalId]

  show(toInternal.into(ExternalId(42L)))
  show(toInternal.into(ExternalId(Long.MaxValue)))
}
