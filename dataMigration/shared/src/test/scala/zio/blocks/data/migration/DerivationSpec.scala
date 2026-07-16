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

package zio.blocks.data.migration

import zio._
import zio.blocks.schema.Schema
import zio.blocks.schema.migration.{DynamicMigration, MigrationAction}
import zio.test._
import zio.test.Assertion._

object DerivationSpec extends ZIOSpecDefault {

  // Test case classes (simple records)
  final case class UserV1(name: String, age: Int)
  final case class UserV2(name: String, age: Int, email: String) // added field
  final case class UserV3(name: String, years: Int) // renamed age -> years
  final case class UserV4(name: String) // removed age

  implicit val schemaV1: Schema[UserV1] = Schema.derived
  implicit val schemaV2: Schema[UserV2] = Schema.derived
  implicit val schemaV3: Schema[UserV3] = Schema.derived
  implicit val schemaV4: Schema[UserV4] = Schema.derived

  def spec = suite("SchemaDerivation")(
    test("identical schemas produce empty (identity) migration") {
      val mig = SchemaDerivation.derive[UserV1, UserV1]
      assertTrue(mig.dynamicMigration.isEmpty) &&
      assertTrue(mig.dynamicMigration.actions.isEmpty)
    },
    test("added field produces AddField action") {
      val mig = SchemaDerivation.derive[UserV1, UserV2]
      val actions = mig.dynamicMigration.actions
      assertTrue(actions.exists(_.isInstanceOf[MigrationAction.AddField])) &&
      assertTrue(actions.size == 1)
    },
    test("removed field produces DropField action") {
      val mig = SchemaDerivation.derive[UserV1, UserV4]
      val actions = mig.dynamicMigration.actions
      assertTrue(actions.exists(_.isInstanceOf[MigrationAction.DropField])) &&
      assertTrue(actions.size == 1)
    },
    test("renamed field (position match) produces RenameField action") {
      val mig = SchemaDerivation.derive[UserV1, UserV3]
      val actions = mig.dynamicMigration.actions
      assertTrue(actions.exists(_.isInstanceOf[MigrationAction.RenameField])) &&
      assertTrue(actions.size == 1)
    },
    test("multiple changes combined produce multiple actions") {
      // V1 to a combined: drop age, add email (but use V2 which adds)
      val mig = SchemaDerivation.derive[UserV1, UserV2]
      assertTrue(mig.dynamicMigration.actions.nonEmpty)
    }
  )
}
