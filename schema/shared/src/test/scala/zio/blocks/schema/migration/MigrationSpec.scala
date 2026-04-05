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

object MigrationSpec extends SchemaBaseSpec {

  private final case class PersonV1(name: String)

  private final case class PersonV2(name: String, age: Int)

  private implicit lazy val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
  private implicit lazy val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

  def spec: Spec[Any, Any] = suite("Migration")(
    test("identity round-trip") {
      implicit val schema: Schema[PersonV2] = personV2Schema
      val migration                         = Migration.identity[PersonV2]
      val person                            = PersonV2("Ada", 40)
      assertTrue(migration(person) == Right(person))
    },
    test("addField with literal default") {
      val migration =
        MigrationBuilder[PersonV1, PersonV2]
          .addField(_.age, MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0))))
          .buildPartial
      val result = migration(PersonV1("Ada"))
      assertTrue(result == Right(PersonV2("Ada", 0)))
    },
    test("dynamicMigration schema round-trip via DynamicValue") {
      import MigrationCodecs._
      val dm =
        DynamicMigration(
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0)))
          )
        )
      val s  = implicitly[Schema[DynamicMigration]]
      val dv = s.toDynamicValue(dm)
      assertTrue(s.fromDynamicValue(dv) == Right(dm))
    },
    test("reverse is involutive on actions") {
      val action = MigrationAction.AddField(
        DynamicOptic.root.field("x"),
        MigrationExpr.Literal(DynamicValue.Null)
      )
      assertTrue(action.reverse.reverse == action)
    },
    test("composition associativity structure") {
      val empty = DynamicMigration.empty
      val a     = DynamicMigration(MigrationAction.RenameCase(DynamicOptic.root, "A", "B"))
      assertTrue(((empty ++ a) ++ empty).actions == (empty ++ (a ++ empty)).actions)
    }
  )
}
