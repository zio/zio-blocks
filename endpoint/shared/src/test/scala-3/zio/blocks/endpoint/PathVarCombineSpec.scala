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

object PathVarCombineSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("PathVar Combined/Transform propagation (Scala 3)")(
    test("int ~ literal ~ string produces an ordered, concatenated PathVars tuple") {
      val chain = SegmentCodec.int("userId") ~ SegmentCodec.literal("-") ~ SegmentCodec.string("slug")
      implicitly[chain.PathVars =:= (PathVar["userId", Int] *: PathVar["slug", String] *: EmptyTuple)]
      assertCompletes
    },
    test("two same-named segments produce two distinct PathVars entries at different positions") {
      val chain = SegmentCodec.int("id") ~ SegmentCodec.literal("-") ~ SegmentCodec.int("id")
      implicitly[chain.PathVars =:= (PathVar["id", Int] *: PathVar["id", Int] *: EmptyTuple)]
      assertCompletes
    },
    test("Transform passes PathVars through unchanged") {
      val transformed = SegmentCodec.int("id").transform(identity, identity)
      implicitly[transformed.PathVars =:= (PathVar["id", Int] *: EmptyTuple)]
      assertCompletes
    }
  )
}
