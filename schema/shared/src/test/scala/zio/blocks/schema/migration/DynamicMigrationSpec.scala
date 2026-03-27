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

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {
  private def str(v: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(v))
  private def int(v: Int): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Int(v))

  def spec: Spec[Any, Any] = suite("DynamicMigrationSpec")(
    test("AddField adds field with default") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val mig    = DynamicMigration(Vector(AddField(DynamicOptic.root.field("age"), MigrationExpr.Literal(int(10)))))
      assertTrue(mig(source).contains(DynamicValue.Record(Chunk("name" -> str("Ann"), "age" -> int(10)))))
    },
    test("DropField removes field") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann"), "age" -> int(10)))
      val mig    = DynamicMigration(Vector(DropField(DynamicOptic.root.field("age"), MigrationExpr.DefaultValue)))
      assertTrue(mig(source).contains(DynamicValue.Record(Chunk("name" -> str("Ann")))))
    },
    test("Rename renames field in root record") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val mig    = DynamicMigration(Vector(Rename(DynamicOptic.root.field("name"), "fullName")))
      assertTrue(mig(source).contains(DynamicValue.Record(Chunk("fullName" -> str("Ann")))))
    },
    test("TransformElements maps over sequence") {
      val source = DynamicValue.Sequence(Chunk(str("a"), str("b")))
      val mig = DynamicMigration(
        Vector(TransformElements(DynamicOptic.root, MigrationExpr.Concat(Vector(MigrationExpr.Identity), "!")))
      )
      assertTrue(mig(source).isRight)
    },
    test("missing path yields Left with path info") {
      val source = DynamicValue.Record(Chunk("name" -> str("Ann")))
      val mig    = DynamicMigration(Vector(DropField(DynamicOptic.root.field("age"), MigrationExpr.DefaultValue)))
      assertTrue(mig(source).isLeft)
    }
  )
}
