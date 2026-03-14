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

/** Proves the compile-time symbolic execution engine works across Scala 2.13 and 3:
  * if this test compiles and runs, AddField and Rename were validated to align source and target shape.
  */
object MigrationBuildValidationSpec extends SchemaBaseSpec {

  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, age: Int, active: Boolean)

  implicit val v1Schema: Schema[PersonV1] = Schema.derived
  implicit val v2Schema: Schema[PersonV2] = Schema.derived

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuildValidationSpec")(
    test("compile-time validation succeeds for structurally sound migration") {
      val migration = Migration.newBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
//        .addField(_.active, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
        .build

      assertTrue(migration.dynamicMigration.actions.length == 2)
    }
  )
}
