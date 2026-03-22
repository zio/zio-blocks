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

package zio.blocks.codegen.ir

import zio.test._
import zio.test.Assertion._

object TypeParamSpec extends ZIOSpecDefault {
  def spec =
    suite("TypeParam")(
      suite("construction")(
        test("creates simple invariant type param") {
          val tp = TypeParam("A")
          assert(tp.name)(equalTo("A")) &&
          assert(tp.variance)(equalTo(Variance.Invariant)) &&
          assert(tp.upperBound)(isNone) &&
          assert(tp.lowerBound)(isNone)
        },
        test("creates covariant type param") {
          val tp = TypeParam("A", Variance.Covariant)
          assert(tp.variance)(equalTo(Variance.Covariant))
        },
        test("creates contravariant type param") {
          val tp = TypeParam("A", Variance.Contravariant)
          assert(tp.variance)(equalTo(Variance.Contravariant))
        },
        test("creates type param with upper bound") {
          val tp = TypeParam("A", upperBound = Some(TypeRef("Serializable")))
          assert(tp.upperBound)(isSome(equalTo(TypeRef("Serializable"))))
        },
        test("creates type param with lower bound") {
          val tp = TypeParam("A", lowerBound = Some(TypeRef.Nothing))
          assert(tp.lowerBound)(isSome(equalTo(TypeRef.Nothing)))
        },
        test("creates type param with both bounds") {
          val tp = TypeParam(
            "A",
            lowerBound = Some(TypeRef.Nothing),
            upperBound = Some(TypeRef("AnyRef"))
          )
          assert(tp.lowerBound)(isSome) &&
          assert(tp.upperBound)(isSome)
        }
      ),
      suite("equality")(
        test("equal type params") {
          val tp1 = TypeParam("A", Variance.Covariant)
          val tp2 = TypeParam("A", Variance.Covariant)
          assert(tp1)(equalTo(tp2))
        },
        test("unequal when name differs") {
          val tp1 = TypeParam("A")
          val tp2 = TypeParam("B")
          assert(tp1)(not(equalTo(tp2)))
        },
        test("unequal when variance differs") {
          val tp1 = TypeParam("A", Variance.Covariant)
          val tp2 = TypeParam("A", Variance.Contravariant)
          assert(tp1)(not(equalTo(tp2)))
        }
      ),
      suite("HKT type params")(
        test("creates type param with nested type params (F[_])") {
          val tp = TypeParam("F", typeParams = List(TypeParam("_")))
          assert(tp.name)(equalTo("F")) &&
          assert(tp.typeParams.length)(equalTo(1)) &&
          assert(tp.typeParams(0).name)(equalTo("_"))
        },
        test("creates type param with nested HKT (F[_[_]])") {
          val tp = TypeParam("F", typeParams = List(TypeParam("G", typeParams = List(TypeParam("_")))))
          assert(tp.typeParams.length)(equalTo(1)) &&
          assert(tp.typeParams(0).typeParams.length)(equalTo(1))
        },
        test("covariant HKT") {
          val tp = TypeParam("F", Variance.Covariant, typeParams = List(TypeParam("_")))
          assert(tp.variance)(equalTo(Variance.Covariant)) &&
          assert(tp.typeParams.length)(equalTo(1))
        },
        test("typeParams defaults to empty") {
          val tp = TypeParam("A")
          assert(tp.typeParams)(isEmpty)
        }
      ),
      suite("context bounds")(
        test("creates type param with context bound") {
          val tp = TypeParam("A", contextBounds = List(TypeRef("Ordering")))
          assert(tp.contextBounds.length)(equalTo(1)) &&
          assert(tp.contextBounds(0).name)(equalTo("Ordering"))
        },
        test("creates type param with multiple context bounds") {
          val tp = TypeParam("A", contextBounds = List(TypeRef("Ordering"), TypeRef("Show")))
          assert(tp.contextBounds.length)(equalTo(2))
        },
        test("contextBounds defaults to empty") {
          val tp = TypeParam("A")
          assert(tp.contextBounds)(isEmpty)
        },
        test("context bound with variance and upper bound") {
          val tp = TypeParam(
            "A",
            Variance.Covariant,
            upperBound = Some(TypeRef("AnyRef")),
            contextBounds = List(TypeRef("Schema"))
          )
          assert(tp.variance)(equalTo(Variance.Covariant)) &&
          assert(tp.upperBound)(isSome) &&
          assert(tp.contextBounds.length)(equalTo(1))
        }
      )
    )
}
