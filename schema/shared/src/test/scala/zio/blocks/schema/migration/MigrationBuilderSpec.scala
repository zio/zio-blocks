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

import zio.blocks.schema.{DynamicOptic, Schema, SchemaBaseSpec, SchemaExpr}
import zio.blocks.schema.DynamicValue
import zio.test._

object MigrationBuilderSpec extends SchemaBaseSpec {

  private def defaultExpr[A](schema: Schema[A]): SchemaExpr.DefaultValue =
    SchemaExpr.DefaultValue(schema.getDefaultValue.map(schema.toDynamicValue).getOrElse(DynamicValue.Null))

  final case class PersonV1(name: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  final case class PersonV2(fullName: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }

  final case class PersonV3(displayName: String)
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived[PersonV3]
  }

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationBuilderSpec")(
      test("PersonV1 -> PersonV2 rename builds via MigrationBuilder") {
        val migration =
          Migration
            .newBuilder[PersonV1, PersonV2]
            .renameField(DynamicOptic.root.field("name"), to = "fullName")
            .buildPartial

        assertTrue(migration(PersonV1("Alice")) == Right(PersonV2("Alice")))
      },
      test("addField then dropField round-trip") {
        val ageSchema = Schema[Int].defaultValue(0)
        val expr      = defaultExpr(ageSchema)

        val migration =
          Migration
            .newBuilder[PersonV1, PersonV1]
            .addField(DynamicOptic.root.field("age"), expr)
            .dropField(DynamicOptic.root.field("age"), expr)
            .buildPartial

        val a = PersonV1("Alice")
        assertTrue(migration(a) == Right(a))
      },
      test("multiple renames chain correctly") {
        val migration =
          Migration
            .newBuilder[PersonV1, PersonV3]
            .renameField(DynamicOptic.root.field("name"), to = "fullName")
            .renameField(DynamicOptic.root.field("fullName"), to = "displayName")
            .buildPartial

        assertTrue(migration(PersonV1("Alice")) == Right(PersonV3("Alice")))
      },
      test("buildPartial builds without macro validation") {
        val builder =
          Migration
            .newBuilder[PersonV1, PersonV2]
            .renameField(DynamicOptic.root.field("name"), to = "fullName")

        val a = PersonV1("Alice")
        assertTrue(builder.buildPartial.apply(a) == Right(PersonV2("Alice")))
      }
    )
}
