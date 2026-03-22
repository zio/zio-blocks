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

package zio.blocks.schema

import zio.blocks.schema.fixtures.PlayerId
import zio.blocks.typeid.TypeId
import zio.test._

// This spec intentionally does NOT define custom newTypeSchema/subTypeSchema givens,
// so Schema.derived relies on the Schema macro's built-in neotype handling.
object NeotypeTypeIdConsistencySpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("NeotypeTypeIdConsistencySpec")(
    test("Schema-reflected TypeId for List[neotype Subtype] equals directly derived TypeId") {
      case class RoundEnded(results: List[PlayerId])
      val schema       = Schema.derived[RoundEnded]
      val fieldTypeId  = schema.reflect.asRecord.get.fields(0).value.typeId
      val directTypeId = TypeId.derived[List[PlayerId]]
      assertTrue(
        fieldTypeId == directTypeId,
        fieldTypeId.hashCode == directTypeId.hashCode
      )
    }
  )
}
