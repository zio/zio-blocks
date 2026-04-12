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

import zio.blocks.schema.{Schema, SchemaBaseSpec, SchemaExpr}
import zio.blocks.schema.DynamicValue
import zio.test._

object MigrationMacroSpec extends SchemaBaseSpec {

  private def defaultExpr[A](schema: Schema[A]): SchemaExpr.DefaultValue =
    SchemaExpr.DefaultValue(schema.getDefaultValue.map(schema.toDynamicValue).getOrElse(DynamicValue.Null))

  final case class PersonV1(name: String)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  final case class PersonV2(fullName: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  final case class AddFieldV1(name: String)
  object AddFieldV1 {
    implicit val schema: Schema[AddFieldV1] = Schema.derived
  }
  final case class AddFieldV2(name: String, active: Boolean)
  object AddFieldV2 {
    implicit val schema: Schema[AddFieldV2] = Schema.derived
  }

  final case class DropFieldV1(name: String, active: Boolean)
  object DropFieldV1 {
    implicit val schema: Schema[DropFieldV1] = Schema.derived
  }
  final case class DropFieldV2(name: String)
  object DropFieldV2 {
    implicit val schema: Schema[DropFieldV2] = Schema.derived
  }

  final case class TransformV(name: String)
  object TransformV {
    implicit val schema: Schema[TransformV] = Schema.derived
  }

  final case class ChainV1(name: String)
  object ChainV1 {
    implicit val schema: Schema[ChainV1] = Schema.derived
  }
  final case class ChainV2(fullName: String, active: Boolean)
  object ChainV2 {
    implicit val schema: Schema[ChainV2] = Schema.derived
  }

  def spec: Spec[Any, Nothing] =
    suite("MigrationMacroSpec")(
      test("renameField selector macro builds a working migration") {
        val result =
          Migration
            .newBuilder[PersonV1, PersonV2]
            .renameField(_.name, _.fullName)
            .build
            .apply(PersonV1("Alice"))

        assertTrue(result == Right(PersonV2("Alice")))
      },
      test("addField macro expands correctly") {
        val activeSchema = Schema[Boolean].defaultValue(false)
        val migration =
          Migration
            .newBuilder[AddFieldV1, AddFieldV2]
            .addField(_.active, defaultExpr(activeSchema))
            .build

        assertTrue(migration(AddFieldV1("Alice")) == Right(AddFieldV2("Alice", active = false)))
      },
      test("dropField macro expands correctly") {
        val activeSchema = Schema[Boolean].defaultValue(false)
        val migration =
          Migration
            .newBuilder[DropFieldV1, DropFieldV2]
            .dropField(_.active, defaultExpr(activeSchema))
            .build

        assertTrue(migration(DropFieldV1("Alice", active = true)) == Right(DropFieldV2("Alice")))
      },
      test("transformField macro compiles (use SchemaExpr.Literal)") {
        val migration =
          Migration
            .newBuilder[TransformV, TransformV]
            .transformField(_.name, SchemaExpr.Literal[Any, String]("Bob", Schema[String]))
            .build

        assertTrue(migration(TransformV("Alice")) == Right(TransformV("Bob")))
      },
      test("chained macro calls preserve type-state for build") {
        // This MUST compile with .build (not .buildPartial).
        // If Actions type is widened after the first call, build will fail.
        val activeSchema = Schema[Boolean].defaultValue(false)
        val migration =
          Migration
            .newBuilder[ChainV1, ChainV2]
            .renameField(_.name, _.fullName)
            .addField(_.active, defaultExpr(activeSchema))
            .build

        assertTrue(migration(ChainV1("Alice")) == Right(ChainV2("Alice", active = false)))
      },
      test("issue example: structural type migration compiles") {
        type PersonV0 = { val firstName: String; val lastName: String }

        final case class PersonV0Nom(firstName: String, lastName: String)
        object PersonV0Nom {
          implicit val schema: Schema[PersonV0Nom] = Schema.derived
        }

        final case class Person(fullName: String, age: Int)
        object Person {
          implicit val schema: Schema[Person] = Schema.derived
        }

        // Use a nominal schema to produce a structural schema with the same shape.
        // This mirrors the current ZIO Schema 2 API (`Schema.derived[Nominal].structural`).
        implicit val v0Schema: Schema[PersonV0] =
          Schema[PersonV0Nom].structural.asInstanceOf[Schema[PersonV0]]

        val migration =
          Migration
            .newBuilder[PersonV0, Person]
            .addField(_.age, 0) // uses implicit conversion Int => SchemaExpr
            .buildPartial // full example needs Join for fullName (out of scope)

        assertTrue(migration != null)
      }
    )
}
