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
import zio.test.Assertion._
import zio.blocks.schema.DynamicValue
import zio.blocks.schema.PrimitiveValue
import zio.blocks.schema.DynamicOptic

object DynamicMigrationSpec extends ZIOSpecDefault {

  def spec = suite("DynamicMigrationSpec")(
    test("Interpreter correctly adds a field to a DynamicValue.Record") {
      val initialValue = DynamicValue.Record("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
      
      val optic = DynamicOptic.root.field("new_field")
      val migration = DynamicMigration.AddField(optic, DynamicValue.Primitive(PrimitiveValue.String("hello")))
      
      val result = Interpreter.run(migration, initialValue)
      
      val expectedRecord = DynamicValue.Record(
        "existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
        "new_field" -> DynamicValue.Primitive(PrimitiveValue.String("hello"))
      )

      assert(result)(isRight(equalTo(expectedRecord)))
    },

    test("Interpreter correctly renames a field in a DynamicValue.Record") {
      // Ensure we preserve order and content
      val initialValue = DynamicValue.Record(
        "old_name" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
      )
      
      val optic = DynamicOptic.root.field("old_name")
      val migration = DynamicMigration.RenameField(optic, "new_name")
      
      val result = Interpreter.run(migration, initialValue)
      
      val expectedRecord = DynamicValue.Record(
        "new_name" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
      )

      assert(result)(isRight(equalTo(expectedRecord)))
    },

    test("Interpreter correctly renames a Enum Variant case via RenameCase AST") {
      val initialValue = DynamicValue.Variant("OldTag", DynamicValue.Primitive(PrimitiveValue.Int(100)))
      
      val optic = DynamicOptic.root.caseOf("OldTag")
      val migration = DynamicMigration.RenameCase(optic, "NewSecureTag")
      
      val result = Interpreter.run(migration, initialValue)
      
      val expectedVariant = DynamicValue.Variant("NewSecureTag", DynamicValue.Primitive(PrimitiveValue.Int(100)))

      assert(result)(isRight(equalTo(expectedVariant)))
    },

    test("Interpreter correctly evaluates Identity Migration") {
      val initialValue = DynamicValue.Record("existing" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
      val result = Interpreter.run(DynamicMigration.Identity, initialValue)
      
      assert(result)(isRight(equalTo(initialValue)))
    }
  )
}
