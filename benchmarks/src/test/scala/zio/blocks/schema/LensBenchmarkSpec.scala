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

object LensBenchmarkSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("LensBenchmarkSpec")(
    suite("LensGetBenchmark")(
      test("has consistent output") {
        assert((new LensGetBenchmark).direct)(equalTo("test")) &&
        assert((new LensGetBenchmark).monocle)(equalTo("test")) &&
        assert((new LensGetBenchmark).zioBlocks)(equalTo("test"))
      }
    ),
    suite("LensReplaceBenchmark")(
      test("has consistent output") {
        import zio.blocks.schema.LensDomain._

        assert((new LensReplaceBenchmark).direct)(equalTo(A(B(C(D(E("test2"))))))) &&
        assert((new LensReplaceBenchmark).monocle)(equalTo(A(B(C(D(E("test2"))))))) &&
        assert((new LensReplaceBenchmark).quicklens)(equalTo(A(B(C(D(E("test2"))))))) &&
        assert((new LensReplaceBenchmark).zioBlocks)(equalTo(A(B(C(D(E("test2")))))))
      }
    )
  )
}
