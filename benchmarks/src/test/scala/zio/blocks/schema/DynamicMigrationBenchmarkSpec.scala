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

package zio.blocks.schema

import zio.blocks.schema.migration.DynamicMigration
import zio.test._
import zio.test.Assertion._

/**
 * Correctness-only sidecar spec for [[DynamicMigrationBenchmark]].
 *
 * Instantiates the benchmark class, calls `setup()`, then asserts semantic
 * outputs for representative single-action apply scenarios, the composed
 * 5-action apply, the 10-action structural reverse, the 10-action JSON
 * round-trip, and the no-op apply path.
 *
 * No timing assertions, throughput checks, or perf thresholds are made here.
 */
object DynamicMigrationBenchmarkSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationBenchmarkSpec")(
    test("benchmark fixtures are built and representative single-action apply paths return Right") {
      val benchmark = new DynamicMigrationBenchmark
      benchmark.setup()

      // Single-action apply correctness: each result must be a Right
      assert(benchmark.addFieldApply())(isRight) &&
      assert(benchmark.dropFieldApply())(isRight) &&
      assert(benchmark.renameApply())(isRight) &&
      assert(benchmark.transformValueApply())(isRight) &&
      assert(benchmark.changeTypeApply())(isRight) &&
      assert(benchmark.optionalizeApply())(isRight) &&
      assert(benchmark.renameCaseApply())(isRight) &&
      assert(benchmark.transformCaseApply())(isRight) &&
      assert(benchmark.transformElementsApply())(isRight) &&
      assert(benchmark.transformKeysApply())(isRight) &&
      assert(benchmark.transformValuesApply())(isRight)
    },
    test("mandate apply path returns Right (unwraps Some variant)") {
      val benchmark = new DynamicMigrationBenchmark
      benchmark.setup()
      // mandateInput is Variant("Some", Record(("value", 42))); mandate unwraps it
      assert(benchmark.mandateApply())(isRight)
    },
    test("join and split single-action apply paths return Right") {
      val benchmark = new DynamicMigrationBenchmark
      benchmark.setup()
      assert(benchmark.joinApply())(isRight) &&
      assert(benchmark.splitApply())(isRight)
    },
    test("composed 5-action apply returns Right") {
      val benchmark = new DynamicMigrationBenchmark
      benchmark.setup()
      assert(benchmark.composedApply())(isRight)
    },
    test("structural reverse of 10-action migration produces a migration with 10 actions") {
      val benchmark = new DynamicMigrationBenchmark
      benchmark.setup()
      val reversed = benchmark.structuralReverse10Action()
      assertTrue(reversed.actions.length == 10)
    },
    test("structural reverse is involutive: reverse.reverse has the same action count") {
      val benchmark = new DynamicMigrationBenchmark
      benchmark.setup()
      val original  = benchmark.reverse10Migration
      val roundtrip = original.reverse.reverse
      assertTrue(roundtrip.actions.length == original.actions.length)
    },
    test("JSON round-trip of 10-action migration returns Right and equals original") {
      val benchmark = new DynamicMigrationBenchmark
      benchmark.setup()
      assert(benchmark.jsonRoundTrip10Action())(isRight(equalTo(benchmark.jsonRoundTrip10Migration)))
    },
    test("no-op apply returns Right with the original DynamicValue unchanged") {
      val benchmark = new DynamicMigrationBenchmark
      benchmark.setup()
      assert(benchmark.noopApply())(isRight(equalTo(benchmark.noopInput)))
    },
    test("no-op migration is the identity element: DynamicMigration.empty has zero actions") {
      val benchmark = new DynamicMigrationBenchmark
      benchmark.setup()
      assertTrue(benchmark.noopMigration == DynamicMigration.empty) &&
      assertTrue(benchmark.noopMigration.isEmpty)
    }
  )
}
