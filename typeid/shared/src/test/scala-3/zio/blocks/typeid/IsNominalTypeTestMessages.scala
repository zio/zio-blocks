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

package zio.blocks.typeid

// Whether a `typeCheck("def foo[A] = zio.blocks.typeid.IsNominalType[A]")` error is the expected one.
// On Scala 3 it is the compiler's failed-implicit-search text for `IsNominalType.apply`'s parameter,
// which hides the macro's own message and whose wording changes between compiler versions, so match
// only the type it could not find. (IsNominalTypeNegativeSpec checks the macro's message through
// `summon`.) An unrelated failure, such as "trait IsNominalTypeVersionSpecific is marked
// @experimental", does not name `IsNominalType[A]`.
object IsNominalTypeTestMessages {
  def isBareTypeParameterError(message: String): Boolean =
    message.contains("zio.blocks.typeid.IsNominalType[A]")
}
