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

package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.chunk.Chunk

object MigrationSpec extends ZIOSpecDefault {

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int, active: Boolean)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  def spec = suite("MigrationSpec")(
    test("apply dynamic actions to add, rename, and drop fields") {
      val v1    = PersonV1("Alice", 30)
      val dynV1 = PersonV1.schema.toDynamicValue(v1)

      val migration =
        MigrationBuilder[PersonV1, PersonV2, PersonV1](PersonV1.schema, PersonV2.schema, DynamicMigration.empty)
          .rename(p".name", "fullName")
          .addField(p".active", SchemaExpr.Literal(true, Schema[Boolean]))
          .buildPartial

      val result = migration(v1)

      assertTrue(result == Right(PersonV2("Alice", 30, true)))
    }
  )

}
