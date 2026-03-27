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

object MigrationLawSpec extends SchemaBaseSpec {
  final case class Person(name: String, age: Int)
  implicit val personSchema: Schema[Person] = Schema.derived[Person]

  def spec: Spec[Any, Any] = suite("MigrationLawSpec")(
    test("identity law") {
      val m = Migration.identity(personSchema)
      val p = Person("Ann", 10)
      assertTrue(m(p) == Right(p))
    },
    test("associativity for dynamic migration composition") {
      val p  = DynamicValue.Record(Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("x"))))
      val m1 = DynamicMigration(Vector(AddField(DynamicOptic.root.field("a"), MigrationExpr.Literal(DynamicValue.Null))))
      val m2 = DynamicMigration(Vector(AddField(DynamicOptic.root.field("b"), MigrationExpr.Literal(DynamicValue.Null))))
      val m3 = DynamicMigration(Vector(AddField(DynamicOptic.root.field("c"), MigrationExpr.Literal(DynamicValue.Null))))
      assertTrue(((m1 ++ m2) ++ m3)(p) == (m1 ++ (m2 ++ m3))(p))
    },
    test("reverse reverse preserves action shape") {
      val m = DynamicMigration(Vector(Rename(DynamicOptic.root.field("name"), "fullName")))
      assertTrue(m.reverse.reverse.actions.length == m.actions.length)
    }
  )
}
