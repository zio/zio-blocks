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

import zio.blocks.schema._
import zio.test._

object MigrationSpec extends SchemaBaseSpec {
  final case class PersonV1(name: String, age: Int)
  final case class PersonV2(fullName: String, age: Int)

  implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
  implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

  def spec: Spec[Any, Any] = suite("MigrationSpec")(
    test("simple typed migration rename field") {
      val dynamic = DynamicMigration(
        Vector(MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"))
      )
      val mig = Migration[PersonV1, PersonV2](dynamic, personV1Schema, personV2Schema)
      assertTrue(mig(PersonV1("Ann", 10)) == Right(PersonV2("Ann", 10)))
    },
    test("composition via ++") {
      val m1 = Migration[PersonV1, PersonV2](
        DynamicMigration(Vector(MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"))),
        personV1Schema,
        personV2Schema
      )
      val m2 = Migration.identity[PersonV2](personV2Schema)
      assertTrue((m1 ++ m2)(PersonV1("A", 1)) == Right(PersonV2("A", 1)))
    }
  )
}
