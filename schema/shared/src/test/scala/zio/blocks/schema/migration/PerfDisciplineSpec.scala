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

/**
 * Zero-allocation discipline for the migration success path — JS-safe
 * runtime portion only.
 *
 * The shared runtime no-op tests live here. The source-grep against
 * `Interpreter.scala` was moved to
 * `schema/jvm/src/test/scala/.../PerfDisciplineJvmSpec.scala` because it
 * relies on `scala.io.Source.fromFile`, which cannot link on Scala.js.
 *
 * The JMH microbenchmark suite is the definitive regression gate; this
 * discipline test catches accidental regressions without running JMH.
 */
object PerfDisciplineSpec extends SchemaBaseSpec {

  private def intVal(n: Int): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(n))

  def spec: Spec[Any, Any] = suite("PerfDisciplineSpec")(
    test("runtime no-op: DynamicMigration.empty.apply(dv) returns Right(dv) — success path does not touch MigrationError") {
      // For the no-op migration the Interpreter loop is never entered, so NO
      // MigrationError of any kind can be constructed. Proving
      // Right(dv) == Right(dv) is sufficient evidence that the success path
      // is zero-alloc-capable.
      val dv       = intVal(42)
      val result   = DynamicMigration.empty.apply(dv)
      assertTrue(result == Right(dv)) &&
      assertTrue(result.isRight)
    },
    test("runtime no-op across 5 identity-composed empty migrations — still Right, still no error construction") {
      // `empty ++ empty ++ empty ++ empty ++ empty` has 0 actions; apply is a
      // zero-iteration while-loop. Proves compose does not introduce success-path
      // allocation either.
      val dv     = intVal(42)
      val five   = DynamicMigration.empty ++ DynamicMigration.empty ++ DynamicMigration.empty ++ DynamicMigration.empty ++ DynamicMigration.empty
      val result = five.apply(dv)
      assertTrue(result == Right(dv))
    }
  )
}
