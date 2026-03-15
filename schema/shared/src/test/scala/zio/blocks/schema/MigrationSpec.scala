/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import zio.test._
import zio.test.Assertion._
import zio.blocks.chunk.Chunk

object MigrationSpec extends ZIOSpecDefault {
  def spec = suite("MigrationSpec")(
    test("Migration.identity should return the original value") {
      val schema    = Schema.int
      val migration = Migration(DynamicMigration.empty, schema, schema)
      assert(migration(123))(isRight(equalTo(123)))
    },
    test("Migration.newBuilder should build a simple migration") {
      val v0Schema  = Schema.string
      val v1Schema  = Schema.string
      val migration = Migration.newBuilder[String, String](v0Schema, v1Schema).build
      assert(migration("test"))(isRight(equalTo("test")))
    },
    test("MigrationAction.RenameField should rename a field in a record") {
      val optic    = DynamicOptic.root.field("oldName")
      val action   = MigrationAction.RenameField(optic, "newName")
      val input    = DynamicValue.Record(Chunk("oldName" -> DynamicValue.Primitive(PrimitiveValue.Int(123))))
      val expected = DynamicValue.Record(Chunk("newName" -> DynamicValue.Primitive(PrimitiveValue.Int(123))))
      assert(action(input))(isRight(equalTo(expected)))
    },
    test("MigrationAction.AddField should add a field with a literal default") {
      val optic   = DynamicOptic.root.field("newField")
      val default =
        SchemaExpr.Literal[DynamicValue, DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(0)), Schema.dynamic)
      val action = MigrationAction.AddField(optic, default)
      val input  = DynamicValue.Record(Chunk("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
      val result = action(input).toOption.get.asInstanceOf[DynamicValue.Record]
      assert(result.fields.find(_._1 == "newField").map(_._2))(
        isSome(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(0))))
      )
    },
    test("MigrationAction.DropField should remove a field") {
      val optic  = DynamicOptic.root.field("toDrop")
      val action = MigrationAction.DropField(
        optic,
        SchemaExpr.Literal[DynamicValue, DynamicValue](DynamicValue.Null, Schema.dynamic)
      )
      val input = DynamicValue.Record(
        Chunk(
          "toDrop" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "keep"   -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
      )
      val expected = DynamicValue.Record(Chunk("keep" -> DynamicValue.Primitive(PrimitiveValue.Int(2))))
      assert(action(input))(isRight(equalTo(expected)))
    }
  )
}
