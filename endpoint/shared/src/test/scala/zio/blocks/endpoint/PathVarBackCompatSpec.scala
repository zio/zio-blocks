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

package zio.blocks.endpoint

import zio.test._

object PathVarBackCompatSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("PathVar backward compatibility (Todo 1)")(
    test("IntSeg(name, _, _) pattern match still compiles and extracts name") {
      val codec = SegmentCodec.int("id")
      val name  = codec match { case SegmentCodec.IntSeg(name, _, _) => name }
      assertTrue(name == "id")
    },
    test("StringSeg(name, _, _) pattern match still compiles and extracts name") {
      val codec = SegmentCodec.string("slug")
      val name  = codec match { case SegmentCodec.StringSeg(name, _, _) => name }
      assertTrue(name == "slug")
    }
  )
}
