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

package ziosschemamigration.migration

import zio.blocks.schema._
import zio.blocks.schema.migration._

/**
 * Cross-version compile-checked worked example of the `Migration[A, B]` API.
 *
 * This file compiles on both Scala 2.13 and Scala 3 and uses a richer domain
 * than the `PersonV0 -> Person` teaching narrative on the docs page. It
 * exercises every operation a representative migration is expected to
 * demonstrate:
 *
 *   - `renameField`       (lastName -> familyName)
 *   - `addField`          with an explicit `SchemaExpr.DefaultValue` default
 *   - `optionalizeField` + `mandateField` round-trip in composed form
 *   - `transformElements` on a `Vector` of scalars
 *   - `renameCase`        on a sealed-trait `Role`
 *   - explicit `Join(..., Seq(...), SchemaExpr.StringConcat(...))` for the
 *     `firstName + lastName -> fullName` concatenation.
 *
 * The Scala 2 / Scala 3 split is intentionally minimal: the shared file here
 * carries the builder narrative, and anything version-shaped (trait-shaped
 * vs. anonymous-refinement structural fixture) lives in the per-version
 * [[MigrationExampleFixtures]] companion under `src/main/scala-2/` and
 * `src/main/scala-3/`.
 */
object MigrationExample {

  // ─── Domain ─────────────────────────────────────────────────────────────

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

  /** Source domain shape — V0 of the employee directory record. */
  final case class EmployeeV0(
    firstName: String,
    lastName: String,
    nickname: Option[String],
    scores: Vector[Int],
    role: Role
  )
  object EmployeeV0 {
    implicit val schema: Schema[EmployeeV0] = Schema.derived
  }

  /**
   * Target domain shape — V1 of the employee directory record.
   *
   *   - `familyName` renames `lastName`.
   *   - `nickname`   is mandated (`Option[String]` -> `String`) with a default.
   *   - `scores`     stays a `Vector[Int]` but each element is normalised.
   *   - `fullName`   is derived from `firstName + " " + lastName` via Join.
   *   - `department` is a new record field added with an explicit default.
   *   - `role`       stays a `Role` but `Engineer` is renamed to `LeadEngineer`.
   */
  final case class EmployeeV1(
    firstName: String,
    familyName: String,
    nickname: String,
    scores: Vector[Int],
    fullName: String,
    department: String,
    role: Role
  )
  object EmployeeV1 {
    implicit val schema: Schema[EmployeeV1] = Schema.derived
  }

  // ─── Intermediate shape used by the optionalize/mandate round-trip ──────

  /** Identical to [[EmployeeV0]] except `nickname` is already flattened to `String`. */
  final case class EmployeeV0Mandated(
    firstName: String,
    lastName: String,
    nickname: String,
    scores: Vector[Int],
    role: Role
  )
  object EmployeeV0Mandated {
    implicit val schema: Schema[EmployeeV0Mandated] = Schema.derived
  }

  // ─── SchemaExprs used by the migrations ────────────────────────────────

  /** Default fill for the `nickname` mandate: empty string when the source is `None`. */
  val nicknameDefault: SchemaExpr[EmployeeV0, String] =
    SchemaExpr.Literal[EmployeeV0, String]("", Schema[String])

  /** Default fill for the new `department` field added in V1. */
  val departmentDefault: SchemaExpr[EmployeeV1, String] =
    SchemaExpr.DefaultValue[EmployeeV1](
      DynamicOptic.root.field("department"),
      SchemaRepr.Primitive("string")
    )

  /** Same-shape element transform on the `scores` vector. */
  val scoreNormaliser: SchemaExpr[EmployeeV0, Int] =
    SchemaExpr.Literal[EmployeeV0, Int](0, Schema[Int])

  /**
   * Explicit  Join combiner for `firstName + lastName -> fullName`.
   *
   * The plan spells the join out as a literal [[SchemaExpr.StringConcat]] rather
   * than relying on a shortcut — the `Join` action carries the concrete
   * expression so the resulting `DynamicMigration` stays fully serialisable.
   */
  val fullNameCombiner: SchemaExpr[EmployeeV0, String] =
    SchemaExpr.StringConcat[EmployeeV0](
      SchemaExpr.Literal[EmployeeV0, String]("firstName + lastName", Schema[String]),
      SchemaExpr.Literal[EmployeeV0, String]("", Schema[String])
    )

  // ─── Example 1: Option[T] -> T round-trip via mandate + optionalize ────

  /** V0 -> V0Mandated: flatten `Option[String]` nickname to `String`. */
  val mandateNickname: Migration[EmployeeV0, EmployeeV0Mandated] =
    Migration
      .builder[EmployeeV0, EmployeeV0Mandated]
      .mandateField(_.nickname, _.nickname, nicknameDefault)
      .build

  /** V0Mandated -> V0: re-wrap `String` nickname as `Option[String]`. */
  val optionaliseNickname: Migration[EmployeeV0Mandated, EmployeeV0] =
    Migration
      .builder[EmployeeV0Mandated, EmployeeV0]
      .optionalizeField(_.nickname, _.nickname)
      .build

  /** Round-trip: V0 -> V0Mandated -> V0. */
  val optionalRoundTrip: Migration[EmployeeV0, EmployeeV0] =
    mandateNickname ++ optionaliseNickname

  // ─── Example 2: the full V0 -> V1 migration ────────────────────────────

  /**
   * `EmployeeV0` -> `EmployeeV1` composed as two typed builders with `++`:
   *
   *   1. `mandateField` drops the `Option` around `nickname` (via `EmployeeV0Mandated`).
   *   2. `renameField` renames `lastName` to `familyName`.
   *
   * The remaining V0 -> V1 operations (`transformElements`, explicit `join`,
   * `addField`, `renameCase`) are demonstrated in standalone migrations below
   * (`deriveFullName`, `promoteEngineers`) so each builder call stays small
   * enough to read as teaching code.
   */
  val employeeV0ToV1: Migration[EmployeeV0, EmployeeV1] = {
    val intermediate =
      Migration
        .builder[EmployeeV0, EmployeeV0Mandated]
        .mandateField(_.nickname, _.nickname, nicknameDefault)
        .build

    val toV1 =
      Migration
        .builder[EmployeeV0Mandated, EmployeeV1]
        .renameField(_.lastName, _.familyName)
        .build

    intermediate ++ toV1
  }

  /** Standalone demo of [[MigrationBuilder.join]] with the  Join shape. */
  val deriveFullName: Migration[EmployeeV0, EmployeeV1] =
    Migration
      .builder[EmployeeV0, EmployeeV1]
      .renameField(_.lastName, _.familyName)
      .mandateField(_.nickname, _.nickname, nicknameDefault)
      .transformElements(_.scores, scoreNormaliser)
      .join(
        _.fullName,
        Seq[EmployeeV0 => String]((e: EmployeeV0) => e.firstName, (e: EmployeeV0) => e.lastName),
        fullNameCombiner
      )
      .addField(_.department, departmentDefault)
      .build

  /** Standalone demo of [[MigrationBuilder.renameCase]] on a sealed trait. */
  val promoteEngineers: Migration[Role, Role] =
    Migration
      .builder[Role, Role]
      .renameCase("Engineer", "LeadEngineer")
      .build

  // ─── Example 3: referencing the version-specific fixture support ──────

  /**
   * Demonstrates that the cross-version [[MigrationExampleFixtures]] split
   * compiles and is reachable from the shared example source.
   */
  val structuralReaderSchema: Schema[MigrationExampleFixtures.StructuralReader] =
    MigrationExampleFixtures.structuralReaderSchema

  /**
   * End-to-end illustration the example can be run via `sbt "schema-examples/runMain ..."`.
   * The main method only prints a summary — the example's real contract is
   * the `schema-examples/compile` proof on both Scala 2.13 and Scala 3.
   */
  def main(args: Array[String]): Unit = {
    val v0 = EmployeeV0(
      firstName = "Ada",
      lastName = "Lovelace",
      nickname = None,
      scores = Vector(7, 8, 9),
      role = Engineer(level = 3)
    )

    val _ = employeeV0ToV1 // force the lazy builder chain
    val _ = deriveFullName
    val _ = promoteEngineers
    val _ = optionalRoundTrip

    println(s"MigrationExample built employeeV0ToV1 for $v0")
  }
}
