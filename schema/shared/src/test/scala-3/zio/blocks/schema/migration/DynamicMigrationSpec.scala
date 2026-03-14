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
import zio.test._

/** Verifies the pure interpreter: Macro S => A extraction → Builder → DynamicMigration → apply(DynamicValue). */
object DynamicMigrationSpec extends SchemaBaseSpec {

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(name: String, age: Int, email: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    suite("interpreter")(
      test("PersonV1 to PersonV2: addField adds email with default") {
        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(
            (p: PersonV2) => p.email,
            DynamicSchemaExpr.Literal(
              DynamicValue.Primitive(PrimitiveValue.String(""))
            )
          )
          .buildPartial

        val v1       = PersonV1("Alice", 30)
        val dynamicIn = PersonV1.schema.toDynamicValue(v1)
        val result   = migration.dynamicMigration.apply(dynamicIn)

        assertTrue(result.isRight)
        val dynamicOut = result.toOption.get
        val fields     = dynamicOut.asInstanceOf[DynamicValue.Record].fields
        val names     = fields.map(_._1)
        assertTrue(names.toSeq == Seq("name", "age", "email"))
        val emailVal = fields.find(_._1 == "email").map(_._2)
        assertTrue(
          emailVal.contains(
            DynamicValue.Primitive(PrimitiveValue.String(""))
          )
        )
      },
      test("PersonV1 to PersonV2: full round-trip via typed Migration.apply") {
        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(
            (p: PersonV2) => p.email,
            DynamicSchemaExpr.Literal(
              DynamicValue.Primitive(PrimitiveValue.String("noreply@example.com"))
            )
          )
          .buildPartial

        val v1    = PersonV1("Bob", 25)
        val typed = migration.apply(v1)
        assertTrue(typed.isRight)
        val v2 = typed.toOption.get
        assertTrue(v2.name == "Bob" && v2.age == 25 && v2.email == "noreply@example.com")
      },
      test("renameField renames name to fullName") {
        case class Old(name: String, age: Int)
        object Old { implicit val schema: Schema[Old] = Schema.derived }
        case class New(fullName: String, age: Int)
        object New { implicit val schema: Schema[New] = Schema.derived }

        val migration = Migration
          .newBuilder[Old, New]
          .renameField((o: Old) => o.name, (n: New) => n.fullName)
          .buildPartial

        val dynamicIn = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Carol")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(28))
          )
        )
        val result = migration.dynamicMigration.apply(dynamicIn)
        assertTrue(result.isRight)
        val out = result.toOption.get.asInstanceOf[DynamicValue.Record]
        assertTrue(out.fields.exists(_._1 == "fullName"))
        assertTrue(!out.fields.exists(_._1 == "name"))
        val fullNameVal = out.fields.find(_._1 == "fullName").map(_._2)
        assertTrue(
          fullNameVal.contains(
            DynamicValue.Primitive(PrimitiveValue.String("Carol"))
          )
        )
      },
      test("path failure returns Left with path information") {
        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(
            (p: PersonV2) => p.email,
            DynamicSchemaExpr.Literal(
              DynamicValue.Primitive(PrimitiveValue.String(""))
            )
          )
          .buildPartial

        val notARecord = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result    = migration.dynamicMigration.apply(notARecord)
        assertTrue(result.isLeft)
        assertTrue(result.left.toOption.get.contains("Expected Record"))
      }
    )
  )
}
