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
import zio.blocks.schema.migration.fixtures.CompositeFixture._
import zio.test._

object MigrationBuilderSpec extends SchemaBaseSpec {

  private val addFieldDefault: SchemaExpr[TargetPerson, String] =
    SchemaExpr.DefaultValue(DynamicOptic.root.field("firstName"), SchemaRepr.Primitive("string"))

  private val dropFieldDefault: SchemaExpr[SourcePerson, String] =
    SchemaExpr.DefaultValue(DynamicOptic.root.field("lastName"), SchemaRepr.Primitive("string"))

  private val transformLiteral: SchemaExpr[SourcePerson, String] =
    SchemaExpr.Literal[SourcePerson, String]("updated", Schema[String])

  private val converterLiteral: SchemaExpr[SourcePerson, String] =
    SchemaExpr.Literal[SourcePerson, String]("42", Schema[String])

  private val mandateDefault: SchemaExpr[SourcePerson, String] =
    SchemaExpr.Literal[SourcePerson, String]("fallback", Schema[String])

  private val elementLiteral: SchemaExpr[SourcePerson, Int] =
    SchemaExpr.Literal[SourcePerson, Int](99, Schema[Int])

  private val keyLiteral: SchemaExpr[SourcePerson, String] =
    SchemaExpr.Literal[SourcePerson, String]("renamed", Schema[String])

  private val valueLiteral: SchemaExpr[SourcePerson, Int] =
    SchemaExpr.Literal[SourcePerson, Int](0, Schema[Int])

  private val joinCombiner: SchemaExpr[SourcePerson, String] =
    SchemaExpr.Literal[SourcePerson, String]("joined", Schema[String])

  private val splitter: SchemaExpr[SourcePerson, String] =
    SchemaExpr.Literal[SourcePerson, String]("split", Schema[String])

  private val caseLiteral: SchemaExpr[Engineer, Int] =
    SchemaExpr.Literal[Engineer, Int](5, Schema[Int])

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilderSpec")(
    test("Migration.builder captures schemas and starts empty") {
      val builder = Migration.builder[SourcePerson, TargetPerson]

      assertTrue(builder.sourceSchema == SourcePerson.schema) &&
      assertTrue(builder.targetSchema == TargetPerson.schema) &&
      assertTrue(builder.actions.isEmpty)
    },
    test("addField stores AddField with target record path and field name") {
      val builder = Migration.builder[SourcePerson, TargetPerson]
        .addField(_.middleName, addFieldDefault)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.AddField(DynamicOptic.root, "middleName", addFieldDefault)
        )
      )
    },
    test("dropField stores DropField with source record path and field name") {
      val builder = Migration.builder[SourcePerson, TargetPerson]
        .dropField(_.lastName, dropFieldDefault)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.DropField(DynamicOptic.root, "lastName", dropFieldDefault)
        )
      )
    },
    test("renameField stores Rename with the source path and target field name") {
      val builder = Migration.builder[SourcePerson, TargetPerson]
        .renameField(_.lastName, _.familyName)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.Rename(DynamicOptic.root.field("lastName"), "familyName")
        )
      )
    },
    test("transformField stores TransformValue on the shared selector path") {
      val builder = Migration.builder[SourcePerson, SourcePerson]
        .transformField(_.firstName, _.firstName, transformLiteral)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.TransformValue(DynamicOptic.root.field("firstName"), transformLiteral)
        )
      )
    },
    test("changeFieldType stores ChangeType on the shared selector path") {
      val builder = Migration.builder[SourcePerson, AgeStringPerson]
        .changeFieldType(_.age, _.age, converterLiteral)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.ChangeType(DynamicOptic.root.field("age"), converterLiteral)
        )
      )
    },
    test("mandateField stores Mandate on the shared selector path") {
      val builder = Migration.builder[SourcePerson, TargetPerson]
        .mandateField(_.nickname, _.nickname, mandateDefault)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.Mandate(DynamicOptic.root.field("nickname"), mandateDefault)
        )
      )
    },
    test("optionalizeField stores Optionalize with the source schema repr") {
      val builder = Migration.builder[SourcePerson, OptionalNamePerson]
        .optionalizeField(_.firstName, _.firstName)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.Optionalize(DynamicOptic.root.field("firstName"), SchemaRepr.Primitive("string"))
        )
      )
    },
    test("renameCase stores RenameCase at the case path") {
      val builder = Migration.builder[Role, Role]
        .renameCase("Engineer", "LeadEngineer")

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.RenameCase(DynamicOptic.root.caseOf("Engineer"), "Engineer", "LeadEngineer")
        )
      )
    },
    test("transformCase stores nested actions under the selected case") {
      val builder = Migration.builder[Role, Role]
        .transformCase[Engineer, Engineer](_.transformField(_.level, _.level, caseLiteral))

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.TransformCase(
            DynamicOptic.root.caseOf("Engineer"),
            Chunk.single(MigrationAction.TransformValue(DynamicOptic.root.field("level"), caseLiteral))
          )
        )
      )
    },
    test("transformElements stores TransformElements at the collection path") {
      val builder = Migration.builder[SourcePerson, SourcePerson]
        .transformElements(_.scores, elementLiteral)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.TransformElements(DynamicOptic.root.field("scores"), elementLiteral)
        )
      )
    },
    test("transformKeys stores TransformKeys at the map path") {
      val builder = Migration.builder[SourcePerson, SourcePerson]
        .transformKeys(_.labels, keyLiteral)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.TransformKeys(DynamicOptic.root.field("labels"), keyLiteral)
        )
      )
    },
    test("transformValues stores TransformValues at the map path") {
      val builder = Migration.builder[SourcePerson, SourcePerson]
        .transformValues(_.labels, valueLiteral)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.TransformValues(DynamicOptic.root.field("labels"), valueLiteral)
        )
      )
    },
    test("join stores target path and source paths in declared order") {
      val builder = Migration.builder[SourcePerson, TargetPerson]
        .join(_.fullName, Seq(_.firstName, _.lastName), joinCombiner)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.Join(
            DynamicOptic.root.field("fullName"),
            Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
            joinCombiner
          )
        )
      )
    },
    test("split stores source path and target paths in declared order") {
      val builder = Migration.builder[SourcePerson, TargetPerson]
        .split(_.fullName, Seq(_.firstName, _.familyName), splitter)

      assertTrue(
        builder.actions == Chunk.single(
          MigrationAction.Split(
            DynamicOptic.root.field("fullName"),
            Chunk(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("familyName")),
            splitter
          )
        )
      )
    },
    test("build and buildPartial stay equivalent for the covered builder surface") {
      val builder = Migration.builder[SourcePerson, TargetPerson]
        .renameField(_.lastName, _.familyName)
        .addField(_.middleName, addFieldDefault)

      assertTrue(builder.build == builder.buildPartial)
    }
  )
}
