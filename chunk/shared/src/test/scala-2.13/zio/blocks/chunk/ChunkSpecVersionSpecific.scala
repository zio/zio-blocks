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

package zio.blocks.chunk

import zio.test._

import scala.collection.Factory

object ChunkSpecVersionSpecific extends ChunkBaseSpec {

  def spec = suite("ChunkSpecVersionSpecific")(
    test("to") {
      val list  = List(1, 2, 3)
      val chunk = Chunk(1, 2, 3)
      assertTrue(list.to(Chunk) == chunk)
    },
    test("factory") {
      val list    = List(1, 2, 3)
      val chunk   = Chunk(1, 2, 3)
      val factory = implicitly[Factory[Int, Chunk[Int]]]
      assertTrue(factory.fromSpecific(list) == chunk)
    }
  )
}
