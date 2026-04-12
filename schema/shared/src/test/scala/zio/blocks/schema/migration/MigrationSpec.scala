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

object MigrationSpec extends SchemaBaseSpec {

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

  final case class PersonV3(fullName: String, active: Boolean)
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived[PersonV3]
  }

  final case class PersonV4(fullName: String, active: Boolean, score: Int)
  object PersonV4 {
    implicit val schema: Schema[PersonV4] = Schema.derived[PersonV4]
  }

  final case class Empty()
  object Empty {
    implicit val schema: Schema[Empty] = Schema.derived[Empty]
  }

  final case class TwoFields(a: String, b: Int)
  object TwoFields {
    implicit val schema: Schema[TwoFields] = Schema.derived[TwoFields]
  }

  private val v1ToV2: Migration[PersonV1, PersonV2] =
    Migration(
      dynamicMigration = DynamicMigration(Vector(Rename(DynamicOptic.root.field("name"), to = "fullName"))),
      sourceSchema = Schema[PersonV1],
      targetSchema = Schema[PersonV2]
    )

  private val v2ToV3: Migration[PersonV2, PersonV3] = {
    val activeSchema = Schema[Boolean].defaultValue(false)
    Migration(
      dynamicMigration =
        DynamicMigration(Vector(AddField(DynamicOptic.root.field("active"), defaultExpr(activeSchema)))),
      sourceSchema = Schema[PersonV2],
      targetSchema = Schema[PersonV3]
    )
  }

  private val v3ToV4: Migration[PersonV3, PersonV4] = {
    val scoreSchema = Schema[Int].defaultValue(0)
    Migration(
      dynamicMigration = DynamicMigration(Vector(AddField(DynamicOptic.root.field("score"), defaultExpr(scoreSchema)))),
      sourceSchema = Schema[PersonV3],
      targetSchema = Schema[PersonV4]
    )
  }

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationSpec")(
      test("identity law: Migration.identity[Person].apply(p) == Right(p)") {
        val p = PersonV1("Alice")
        assertTrue(Migration.identity(Schema[PersonV1]).apply(p) == Right(p))
      },
      test("reverse law: if m.apply(a) == Right(b) then m.reverse.apply(b) == Right(a)") {
        val a = PersonV1("Alice")
        val m = v1ToV2

        assertTrue(m(a) == Right(PersonV2("Alice"))) &&
        assertTrue(m(a).flatMap(b => m.reverse.apply(b)) == Right(a))
      },
      test("andThen composes correctly") {
        val a  = PersonV1("Alice")
        val m1 = v1ToV2
        val m2 = m1.reverse

        val left  = m1.andThen(m2).apply(a)
        val right = m1.apply(a).flatMap(b => m2.apply(b))

        assertTrue(left == right)
      },
      test("compose 3 migrations with andThen") {
        val a = PersonV1("Alice")

        val m = v1ToV2.andThen(v2ToV3).andThen(v3ToV4)

        val left  = m.apply(a)
        val right = v1ToV2.apply(a).flatMap(v2ToV3.apply).flatMap(v3ToV4.apply)

        assertTrue(left == right) &&
        assertTrue(left == Right(PersonV4(fullName = "Alice", active = false, score = 0)))
      },
      test("reverse of composed migration") {
        val a = PersonV1("Alice")
        val m = v1ToV2.andThen(v2ToV3).andThen(v3ToV4)

        val roundTrip = m.apply(a).flatMap(b => m.reverse.apply(b))

        assertTrue(roundTrip == Right(a))
      },
      test("migration fails gracefully with Left on type mismatch") {
        val bad =
          Migration(
            dynamicMigration = DynamicMigration(
              Vector(
                Rename(DynamicOptic.root.field("name").field("first"), to = "given")
              )
            ),
            sourceSchema = Schema[PersonV1],
            targetSchema = Schema[PersonV2]
          )

        val out = bad.apply(PersonV1("Alice"))

        assertTrue(out.isLeft)
      },
      test("identity law with PersonV2") {
        val p = PersonV2("Alice")
        assertTrue(Migration.identity(Schema[PersonV2]).apply(p) == Right(p))
      },
      test("identity law with PersonV3") {
        val p = PersonV3("Alice", active = true)
        assertTrue(Migration.identity(Schema[PersonV3]).apply(p) == Right(p))
      },
      test("Migration.identity reverse is also identity") {
        val p  = PersonV2("Alice")
        val id = Migration.identity(Schema[PersonV2])
        assertTrue(id.reverse.apply(p) == Right(p))
      },
      test("compose then reverse equals identity") {
        val a = PersonV1("Alice")
        val m = v1ToV2.andThen(v1ToV2.reverse)
        assertTrue(m.apply(a) == Right(a))
      },
      test("three-way composition associativity") {
        val a     = PersonV1("Alice")
        val left  = (v1ToV2.andThen(v2ToV3)).andThen(v3ToV4).apply(a)
        val right = v1ToV2.andThen(v2ToV3.andThen(v3ToV4)).apply(a)
        assertTrue(left == right)
      },
      test("migration on empty case class") {
        val a  = Empty()
        val id = Migration.identity(Schema[Empty])
        assertTrue(id.apply(a) == Right(a))
      },
      test("migration error propagates from inner DynamicMigration") {
        val activeSchema = Schema[Boolean].defaultValue(false)
        val bad          =
          Migration(
            dynamicMigration =
              DynamicMigration(Vector(DropField(DynamicOptic.root.field("doesNotExist"), defaultExpr(activeSchema)))),
            sourceSchema = Schema[PersonV2],
            targetSchema = Schema[PersonV2]
          )

        assertTrue(
          bad(PersonV2("Alice")) == Left(FieldNotFound(DynamicOptic.root.field("doesNotExist"), "doesNotExist"))
        )
      },
      test("newBuilder produces same result as manual Migration construction") {
        val manual = v1ToV2
        val built  =
          Migration
            .newBuilder[PersonV1, PersonV2]
            .renameField(DynamicOptic.root.field("name"), to = "fullName")
            .buildPartial

        val a = PersonV1("Alice")
        assertTrue(manual.apply(a) == built.apply(a))
      },
      test("two independent migrations on different fields commute") {
        val mA =
          Migration(
            dynamicMigration = DynamicMigration(
              Vector(TransformValue(DynamicOptic.root.field("a"), SchemaExpr.Literal[Any, String]("x", Schema[String])))
            ),
            sourceSchema = Schema[TwoFields],
            targetSchema = Schema[TwoFields]
          )
        val mB =
          Migration(
            dynamicMigration = DynamicMigration(
              Vector(TransformValue(DynamicOptic.root.field("b"), SchemaExpr.Literal[Any, Int](1, Schema[Int])))
            ),
            sourceSchema = Schema[TwoFields],
            targetSchema = Schema[TwoFields]
          )

        val in    = TwoFields("orig", 0)
        val left  = mA.andThen(mB).apply(in)
        val right = mB.andThen(mA).apply(in)

        assertTrue(left == right) &&
        assertTrue(left == Right(TwoFields("x", 1)))
      },
      test("Schema round-trip: toDynamicValue then fromDynamicValue") {
        val p  = PersonV3("Alice", active = true)
        val dv = Schema[PersonV3].toDynamicValue(p)

        assertTrue(Schema[PersonV3].fromDynamicValue(dv) == Right(p))
      }
    )
}
