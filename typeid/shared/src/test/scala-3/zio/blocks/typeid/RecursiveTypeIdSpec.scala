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

import zio.test._

object RecursiveTypeIdSpec extends ZIOSpecDefault {

  // Note: Direct recursive type aliases like `type RecursiveAlias = (Int, Option[RecursiveAlias])`
  // are illegal in Scala 3.5+. Use case classes for recursive types instead.
  case class RecursiveNode(value: Int, next: Option[RecursiveNode])

  case class RecursiveCase(value: Int, next: Option[RecursiveCase])

  def spec = suite("RecursiveTypeId")(
    test("derives TypeId for recursive case class") {
      val typeId = TypeId.of[RecursiveCase]
      assertTrue(
        typeId.fullName.contains("RecursiveCase"),
        typeId.isCaseClass
      )
    },
    test("recursive case class TypeId is consistent") {
      val typeId1 = TypeId.of[RecursiveCase]
      val typeId2 = TypeId.of[RecursiveCase]
      assertTrue(typeId1 == typeId2)
    },
    test("recursive node derives TypeId") {
      val typeId = TypeId.of[RecursiveNode]
      assertTrue(
        typeId.fullName.contains("RecursiveNode"),
        typeId.isCaseClass
      )
    }
  )
}
