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

package zio.blocks.schema.migration.fixtures

import zio.blocks.schema._
import zio.blocks.typeid.TypeId

object CompositeFixture {

  type SearchPerson = {
    val firstName: String
    val lastName: String
  }

  private final case class SearchPersonRow(firstName: String, lastName: String)

  implicit val searchPersonSchema: Schema[SearchPerson] =
    Schema.derived[SearchPersonRow].asInstanceOf[Schema[SearchPerson]]

  sealed trait Role
  object Role {
    implicit val schema: Schema[Role] = Schema.derived
  }

  final case class Engineer(level: Int) extends Role
  object Engineer {
    implicit val schema: Schema[Engineer] = Schema.derived
  }

  final case class Manager(teamSize: Int) extends Role
  object Manager {
    implicit val schema: Schema[Manager] = Schema.derived
  }

  final case class CompanyId(value: String) extends AnyVal
  object CompanyId {
    implicit lazy val typeId: TypeId[CompanyId] = TypeId.of[CompanyId]
    implicit lazy val schema: Schema[CompanyId] =
      Schema[String].transform[CompanyId](value => new CompanyId(value), _.value)
  }

  final case class Skill(score: Int)
  object Skill extends CompanionOptics[Skill] {
    implicit val schema: Schema[Skill] = Schema.derived
  }

  final case class Person(firstName: String, lastName: String, skills: Map[String, Skill])
  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived
  }

  final case class Team(name: String, lead: Person, members: Vector[Person])
  object Team extends CompanionOptics[Team] {
    implicit val schema: Schema[Team] = Schema.derived
  }

  final case class Company(
    id: CompanyId,
    role: Role,
    teams: Vector[Team],
    directory: Map[String, Team],
    labels: Map[String, Int],
    featured: Team
  )
  object Company extends CompanionOptics[Company] {
    implicit val schema: Schema[Company] = Schema.derived
  }

  final case class SourcePerson(
    firstName: String,
    lastName: String,
    nickname: Option[String],
    age: Int,
    scores: Vector[Int],
    labels: Map[String, Int],
    role: Role,
    fullName: String
  )
  object SourcePerson {
    implicit val schema: Schema[SourcePerson] = Schema.derived
  }

  final case class TargetPerson(
    firstName: String,
    familyName: String,
    nickname: String,
    age: Int,
    scores: Vector[Int],
    labels: Map[String, Int],
    role: Role,
    middleName: String,
    fullName: String
  )
  object TargetPerson {
    implicit val schema: Schema[TargetPerson] = Schema.derived
  }

  final case class AgeStringPerson(
    firstName: String,
    lastName: String,
    nickname: Option[String],
    age: String,
    scores: Vector[Int],
    labels: Map[String, Int],
    role: Role,
    fullName: String
  )
  object AgeStringPerson {
    implicit val schema: Schema[AgeStringPerson] = Schema.derived
  }

  final case class OptionalNamePerson(
    firstName: Option[String],
    lastName: String,
    nickname: Option[String],
    age: Int,
    scores: Vector[Int],
    labels: Map[String, Int],
    role: Role,
    fullName: String
  )
  object OptionalNamePerson {
    implicit val schema: Schema[OptionalNamePerson] = Schema.derived
  }
}
