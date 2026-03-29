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

package typeid

import zio.blocks.typeid._

object HttpStatus {
  val OK       = 200
  val NotFound = 404
}

object SingletonTypeTest extends App {
  val okTypeId       = TypeId.of[HttpStatus.OK.type]
  val notFoundTypeId = TypeId.of[HttpStatus.NotFound.type]

  println("OK TypeId name: " + okTypeId.name)
  println("NotFound TypeId name: " + notFoundTypeId.name)
  println("OK TypeId fullName: " + okTypeId.fullName)
  println("NotFound TypeId fullName: " + notFoundTypeId.fullName)
  println("Are they equal? " + (okTypeId == notFoundTypeId))

  // Try to dispatch on them
  def dispatch(typeId: TypeId[_]): String =
    if (typeId == okTypeId) "Handling OK"
    else if (typeId == notFoundTypeId) "Handling NotFound"
    else "Unknown"

  println("Dispatch OK: " + dispatch(okTypeId))
  println("Dispatch NotFound: " + dispatch(notFoundTypeId))
}
