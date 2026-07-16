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

package zio.blocks.data.migration

import zio._
import zio.blocks.schema.Schema
import zio.blocks.schema.migration.{DynamicMigration, Migration}
import zio.test._
import zio.test.Assertion._

object CoreSpec extends ZIOSpecDefault {

  // Minimal schemas for testing MigrationPath
  final case class V1(name: String)
  final case class V2(name: String, age: Int)

  implicit val schemaV1: Schema[V1] = Schema.derived
  implicit val schemaV2: Schema[V2] = Schema.derived

  // Identity dynamic migration (for test construction only)
  val identityDyn: DynamicMigration = DynamicMigration.empty

  val migV1toV2: Migration[V1, V2] = Migration(schemaV1, schemaV2, identityDyn)

  val v0 = DataVersion(0, 0, 0)
  val v1 = DataVersion(0, 1, 0)
  val v2 = DataVersion(0, 2, 0)

  def spec = suite("data-migration core model")(
    test("DataVersion ordering and comparison") {
      assertTrue(v0.compare(v1) < 0) &&
      assertTrue(v1.compare(v2) < 0) &&
      assertTrue(v0.compare(v1) <= 0) &&
      assertTrue(v2.compare(v1) >= 0)
    },
    test("MigrationPath rejects downgrade (from >= to)") {
      val bad = MigrationPath(v2, v1, migV1toV2)
      assertTrue(bad.from.compare(bad.to) >= 0) // construction should have failed via require
    } @@ TestAspect.failing, // expect failure due to require
    test("MigrationPath accepts valid upgrade path") {
      val ok = MigrationPath(v1, v2, migV1toV2)
      assertTrue(ok.from.compare(ok.to) < 0)
    },
    test("ExecutionModel sealed trait exhaustiveness (pattern match covers all)") {
      def classify(em: ExecutionModel): String = em match {
        case ExecutionModel.Tiny  => "tiny"
        case ExecutionModel.Small => "small"
        case ExecutionModel.Large => "large"
      }
      assertTrue(classify(ExecutionModel.Tiny) == "tiny") &&
      assertTrue(classify(ExecutionModel.Small) == "small") &&
      assertTrue(classify(ExecutionModel.Large) == "large")
    },
    test("TargetStrategy sealed trait exhaustiveness") {
      def classify(ts: TargetStrategy): String = ts match {
        case TargetStrategy.InPlace          => "inplace"
        case TargetStrategy.ShadowTable(n)   => s"shadow:$n"
      }
      assertTrue(classify(TargetStrategy.InPlace) == "inplace") &&
      assertTrue(classify(TargetStrategy.ShadowTable("tmp")) == "shadow:tmp")
    }
  )
}
