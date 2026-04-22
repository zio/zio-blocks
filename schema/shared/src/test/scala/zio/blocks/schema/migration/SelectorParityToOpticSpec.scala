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
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.migration.fixtures.CompositeFixture._
import zio.test._

object SelectorParityToOpticSpec extends SchemaBaseSpec {
  import Company._

  // Keep `Binding` in scope so the Scala 2.13 `wrapped[...]` macro expansion resolves.
  private val bindingMarker: Option[Binding[Nothing, Nothing]] = None
  locally(bindingMarker)

  private val stringLiteral: SchemaExpr[Company, String] =
    SchemaExpr.Literal[Company, String]("value", Schema[String])

  private val intLiteral: SchemaExpr[Company, Int] =
    SchemaExpr.Literal[Company, Int](1, Schema[Int])

  def spec: Spec[TestEnvironment, Any] = suite("SelectorParityToOpticSpec")(
    test("parity: field selectors match CompanionOptics") {
      val expected = Company.optic(_.featured.name).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(_.featured.name, _.featured.name, stringLiteral)
        .actions
        .last
        .at

      assertTrue(expected == actual)
    },
    test("parity: case selectors match CompanionOptics") {
      val expected = Company.optic(_.role.when[Engineer].level).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(_.role.when[Engineer].level, _.role.when[Engineer].level, intLiteral)
        .actions
        .last
        .at

      assertTrue(expected == actual)
    },
    test("parity: at(index) selectors match CompanionOptics") {
      val expected = Company.optic(_.teams.at(0).name).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(_.teams.at(0).name, _.teams.at(0).name, stringLiteral)
        .actions
        .last
        .at

      assertTrue(expected == actual)
    },
    test("parity: atKey selectors match CompanionOptics") {
      val expected = Company.optic(_.directory.atKey("core").name).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(_.directory.atKey("core").name, _.directory.atKey("core").name, stringLiteral)
        .actions
        .last
        .at

      assertTrue(expected == actual)
    },
    test("parity: atIndices selectors match CompanionOptics") {
      val expected = Company.optic(_.teams.atIndices(0, 1).lead.firstName).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(_.teams.atIndices(0, 1).lead.firstName, _.teams.atIndices(0, 1).lead.firstName, stringLiteral)
        .actions
        .last
        .at

      assertTrue(expected == actual)
    },
    test("parity: atKeys selectors match CompanionOptics") {
      val expected = Company.optic(_.directory.atKeys("core", "ops").lead.firstName).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(
          _.directory.atKeys("core", "ops").lead.firstName,
          _.directory.atKeys("core", "ops").lead.firstName,
          stringLiteral
        )
        .actions
        .last
        .at

      assertTrue(expected == actual)
    },
    test("parity: each selectors match CompanionOptics") {
      val expected = Company.optic(_.teams.each.lead.firstName).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(_.teams.each.lead.firstName, _.teams.each.lead.firstName, stringLiteral)
        .actions
        .last
        .at

      assertTrue(expected == actual)
    },
    test("parity: eachKey selectors match CompanionOptics") {
      val expected = Company.optic(_.labels.eachKey).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(_.labels.eachKey, _.labels.eachKey, stringLiteral)
        .actions
        .last
        .at

      assertTrue(expected == actual)
    },
    test("parity: eachValue selectors match CompanionOptics") {
      val expected = Company.optic(_.labels.eachValue).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(_.labels.eachValue, _.labels.eachValue, intLiteral)
        .actions
        .last
        .at

      assertTrue(expected == actual)
    },
    test("parity: wrapped selectors match CompanionOptics") {
      val expected = Company.optic(_.id.wrapped[String]).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(_.id.wrapped[String], _.id.wrapped[String], stringLiteral)
        .actions
        .last
        .at

      assertTrue(expected == actual)
    },
    test("parity: search selectors match CompanionOptics across version-specific fixtures") {
      val expected = Company.optic(_.teams.searchFor[SearchPerson]).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(
          _.teams.searchFor[SearchPerson],
          _.teams.searchFor[SearchPerson],
          SchemaExpr.Optic(Company.optic(_.teams.searchFor[SearchPerson]))
        )
        .actions
        .last
        .at

      assertTrue(expected == actual)
    },
    test("parity: deep composite selectors match CompanionOptics") {
      val expected = Company.optic(_.teams.atIndices(0, 1).members.at(0).skills.atKeys("scala", "zio").score).toDynamic
      val actual   = Migration
        .builder[Company, Company]
        .transformField(
          _.teams.atIndices(0, 1).members.at(0).skills.atKeys("scala", "zio").score,
          _.teams.atIndices(0, 1).members.at(0).skills.atKeys("scala", "zio").score,
          intLiteral
        )
        .actions
        .last
        .at

      assertTrue(expected == actual)
    }
  )
}
