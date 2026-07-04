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

  def spec: Spec[Any, Nothing] = suite("PathVar Combined/Transform propagation (Scala 2.13)")(
    test("int ~ literal ~ string produces an ordered, concatenated PathVars tuple") {
      val chain = SegmentCodec.int("userId") ~ SegmentCodec.literal("-") ~ SegmentCodec.string("slug")
      implicitly[chain.PathVars =:= Tuple2[PathVar["userId", Int], PathVar["slug", String]]]
      assertCompletes
    },
    test("two same-named segments produce two distinct PathVars entries at different positions") {
      val chain = SegmentCodec.int("id") ~ SegmentCodec.literal("-") ~ SegmentCodec.int("id")
      implicitly[chain.PathVars =:= Tuple2[PathVar["id", Int], PathVar["id", Int]]]
      assertCompletes
    },
    test("Transform passes PathVars through unchanged") {
      val transformed = SegmentCodec.int("id").transform[Int](identity, identity)
      implicitly[transformed.PathVars =:= Tuple1[PathVar["id", Int]]]
      assertCompletes
    },
    test("int ~ literal ~ string.unused produces an ordered tuple with both PathVar and PathVar.Ignored") {
      val chain = SegmentCodec.int("userId") ~ SegmentCodec.literal("-") ~ SegmentCodec.string("postId").unused
      implicitly[chain.PathVars =:= Tuple2[PathVar["userId", Int], PathVar.Ignored["postId", String]]]
      assertCompletes
    },
    test("string.unused ~ literal ~ int preserves order with the Ignored marker first") {
      val chain = SegmentCodec.string("postId").unused ~ SegmentCodec.literal("-") ~ SegmentCodec.int("userId")
      implicitly[chain.PathVars =:= Tuple2[PathVar.Ignored["postId", String], PathVar["userId", Int]]]
      assertCompletes
    },
    test("Transform passes an Ignored PathVars entry through unchanged") {
      val transformed = SegmentCodec.int("id").unused.transform[Int](identity, identity)
      implicitly[transformed.PathVars =:= Tuple1[PathVar.Ignored["id", Int]]]
      assertCompletes
    },
    test(".unused is byte-for-byte identical at runtime to its non-.unused counterpart") {
      val plain   = SegmentCodec.int("id")
      val ignored = SegmentCodec.int("id").unused
      assertTrue(
        SegmentCodec.formatSegment(plain, 42) == SegmentCodec.formatSegment(ignored, 42),
        SegmentCodec.decodeCombined(plain, "42", 0) == SegmentCodec.decodeCombined(ignored, "42", 0),
        SegmentCodec.kind(plain) == SegmentCodec.kind(ignored),
        SegmentCodec.key(plain) == SegmentCodec.key(ignored)
      )
    }
  )
}
