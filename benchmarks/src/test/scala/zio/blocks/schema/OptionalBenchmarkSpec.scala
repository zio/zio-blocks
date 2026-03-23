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

import zio.test.Assertion._
import zio.test._

object OptionalBenchmarkSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("OptionalBenchmarkSpec")(
    suite("OptionalGetOptionBenchmark")(
      test("has consistent output") {
        assert((new OptionalGetOptionBenchmark).direct)(isSome(equalTo("test"))) &&
        assert((new OptionalGetOptionBenchmark).monocle)(isSome(equalTo("test"))) &&
        assert((new OptionalGetOptionBenchmark).zioBlocks)(isSome(equalTo("test")))
      }
    ),
    suite("OptionalReplaceBenchmark")(
      test("has consistent output") {
        import zio.blocks.schema.OptionalDomain._

        assert((new OptionalReplaceBenchmark).direct)(equalTo(A1(B1(C1(D1(E1("test2"))))))) &&
        assert((new OptionalReplaceBenchmark).monocle)(equalTo(A1(B1(C1(D1(E1("test2"))))))) &&
        assert((new OptionalReplaceBenchmark).quicklens)(equalTo(A1(B1(C1(D1(E1("test2"))))))) &&
        assert((new OptionalReplaceBenchmark).zioBlocks)(equalTo(A1(B1(C1(D1(E1("test2")))))))
      }
    )
  )
}
