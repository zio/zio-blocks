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
import zio.test.Assertion._
import zio.test._

object TrackedMigrationBuilderSpec extends SchemaBaseSpec {

  private final case class PersonV1(name: String)
  private final case class PersonV2(name: String, age: Int)

  private implicit lazy val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
  private implicit lazy val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

  def spec: Spec[Any, Any] = suite("TrackedMigrationBuilder")(
    test("build after preserveField + addField") {
      val migration =
        TrackedMigrationBuilder[PersonV1, PersonV2]
          .preserveField["name"](_.name, _.name)
          .addField["age"](_.age, MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0))))
          .build
      assertTrue(migration(PersonV1("Ada")) == Right(PersonV2("Ada", 0)))
    },
    test("buildPartial allows incomplete field tracking") {
      val migration =
        TrackedMigrationBuilder[PersonV1, PersonV2]
          .addField["age"](_.age, MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0))))
          .buildPartial
      assertTrue(migration(PersonV1("Ada")) == Right(PersonV2("Ada", 0)))
    },
    test("build rejects when source or target remnants are non-empty") {
      typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        final case class PV1(name: String)
        final case class PV2(name: String, age: Int)
        implicit val s1: Schema[PV1] = Schema.derived[PV1]
        implicit val s2: Schema[PV2] = Schema.derived[PV2]
        TrackedMigrationBuilder[PV1, PV2]
          .addField["age"](_.age, MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0))))
          .build
      """).map(assert(_)(isLeft))
    }
  )
}
