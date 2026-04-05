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
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

/**
 * zio-blocks#519: structural (old) vs nominal (new) record shapes using
 * [[Schema#structural]] (JVM-only: [[zio.blocks.schema.ToStructural]]).
 */
object MigrationStructuralSpec extends SchemaBaseSpec {

  def spec: Spec[Any, Any] = suite("Migration structural")(
    test("structural source schema: addField with literal default") {
      final case class PersonV1(name: String)
      final case class PersonV2(name: String, age: Int)

      given Schema[PersonV1] = Schema.derived[PersonV1]
      given Schema[PersonV2] = Schema.derived[PersonV2]

      val sourceSchema = Schema.derived[PersonV1].structural

      val migration =
        MigrationBuilder(using sourceSchema, summon[Schema[PersonV2]])
          .addField(_.age, MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0))))
          .buildPartial

      val v1     = PersonV1("Ada")
      val dv     = summon[Schema[PersonV1]].toDynamicValue(v1)
      val result = migration.dynamicMigration.apply(dv, sourceSchema, summon[Schema[PersonV2]])
      assertTrue(
        result.flatMap(summon[Schema[PersonV2]].fromDynamicValue) == Right(PersonV2("Ada", 0))
      )
    }
  )
}
