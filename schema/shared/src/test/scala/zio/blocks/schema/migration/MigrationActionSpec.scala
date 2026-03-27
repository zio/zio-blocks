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

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.MigrationAction._
import zio.test._

object MigrationActionSpec extends ZIOSpecDefault {
  private val rootName = DynamicOptic.root.field("name")
  private val rootAge  = DynamicOptic.root.field("age")

  def spec: Spec[Any, Any] = suite("MigrationActionSpec")(
    test("reverse covers every migration action shape") {
      val actions = Vector[MigrationAction](
        AddField(rootAge, MigrationExpr.DefaultValue),
        DropField(rootAge, MigrationExpr.DefaultValue),
        Rename(rootName, "fullName"),
        TransformValue(rootName, MigrationExpr.Identity),
        Mandate(rootAge, MigrationExpr.DefaultValue),
        Optionalize(rootAge),
        Join(rootName, Vector(rootName, rootAge), MigrationExpr.Identity),
        Split(rootName, Vector(rootName, rootAge), MigrationExpr.Identity),
        ChangeType(rootAge, MigrationExpr.Identity),
        RenameCase(DynamicOptic.root, "Old", "New"),
        TransformCase(DynamicOptic.root, "CaseA", Vector(Rename(rootName, "fullName"))),
        TransformElements(DynamicOptic.root, MigrationExpr.Identity),
        TransformKeys(DynamicOptic.root, MigrationExpr.Identity),
        TransformValues(DynamicOptic.root, MigrationExpr.Identity),
        NestedMigration(rootName, DynamicMigration.identity)
      )

      val reversed = actions.map(_.reverse)

      assertTrue(
        reversed(0).isInstanceOf[DropField] &&
          reversed(1).isInstanceOf[AddField] &&
          reversed(2).isInstanceOf[Rename] &&
          reversed(3).isInstanceOf[TransformValue] &&
          reversed(4).isInstanceOf[Optionalize] &&
          reversed(5).isInstanceOf[Mandate] &&
          reversed(6).isInstanceOf[Split] &&
          reversed(7).isInstanceOf[Join] &&
          reversed(8).isInstanceOf[ChangeType] &&
          reversed(9) == RenameCase(DynamicOptic.root, "New", "Old") &&
          reversed(10).isInstanceOf[TransformCase] &&
          reversed(11).isInstanceOf[TransformElements] &&
          reversed(12).isInstanceOf[TransformKeys] &&
          reversed(13).isInstanceOf[TransformValues] &&
          reversed(14) == NestedMigration(rootName, DynamicMigration.identity.reverse)
      )
    }
  )
}
