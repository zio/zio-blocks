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

package zio.blocks.schema.json.opaque

import zio.blocks.schema.Schema
import zio.blocks.typeid.{Owner, TypeId, TypeRepr}

opaque type ExternalOpaqueInt = Int

object ExternalOpaqueInt {
  def apply(value: Int): ExternalOpaqueInt = value

  extension (value: ExternalOpaqueInt) def toInt: Int = value

  // Models an opaque wrapper whose TypeId representation does not directly
  // identify its primitive runtime representation.
  given TypeId[ExternalOpaqueInt] = TypeId.opaque(
    "ExternalOpaqueInt",
    Owner.Root,
    representation = TypeRepr.Applied(TypeRepr.Ref(TypeId.option), List(TypeRepr.Ref(TypeId.int)))
  )

  given Schema[ExternalOpaqueInt] = Schema.int.transform[ExternalOpaqueInt](ExternalOpaqueInt.apply, _.toInt)
}
