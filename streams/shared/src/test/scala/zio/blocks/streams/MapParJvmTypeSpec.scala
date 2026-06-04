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

package zio.blocks.streams

import zio._
import zio.test._

object MapParJvmTypeSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("MapParJvmTypeSpec")(
    test("mapPar Int=>Int reader propagates JvmType.Int (#5 regression)") {
      val reader = Stream.range(0, 10).mapPar(2)(identity).compile(0, Stream.DefaultBufferSize)
      try assertTrue(reader.jvmType eq JvmType.Int)
      finally reader.close()
    },

    test("mapPar Int=>Long reader propagates JvmType.Long") {
      val reader = Stream.range(0, 10).mapPar(2)(_.toLong).compile(0, Stream.DefaultBufferSize)
      try assertTrue(reader.jvmType eq JvmType.Long)
      finally reader.close()
    },

    test("mapPar Int=>String reader propagates JvmType.AnyRef") {
      val reader = Stream.range(0, 10).mapPar(2)(_.toString).compile(0, Stream.DefaultBufferSize)
      try assertTrue(reader.jvmType eq JvmType.AnyRef)
      finally reader.close()
    }
  )
}
