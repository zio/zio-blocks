/*
 * Copyright 2023 ZIO Blocks Maintainers
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

import monocle.{Focus, PLens}
import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark

class LensGetBenchmark extends BaseBenchmark {
  import zio.blocks.schema.LensDomain._

  var a: A = A(B(C(D(E("test")))))

  @Benchmark
  def direct: String = a.b.c.d.e.s

  @Benchmark
  def monocle: String = A_.b_c_d_e_s_monocle.get(a)

  @Benchmark
  def zioBlocks: String = A.b_c_d_e_s.get(a)
}

class LensReplaceBenchmark extends BaseBenchmark {
  import zio.blocks.schema.LensDomain._

  var a: A = A(B(C(D(E("test")))))

  @Benchmark
  def direct: A = {
    val a = this.a
    val b = a.b
    val c = b.c
    val d = c.d
    a.copy(b = b.copy(c = c.copy(d = d.copy(e = d.e.copy(s = "test2")))))
  }

  @Benchmark
  def monocle: A = A_.b_c_d_e_s_monocle.replace("test2").apply(a)

  @Benchmark
  def quicklens: A = A_.b_c_d_e_s_quicklens.apply(a).setTo("test2")

  @Benchmark
  def zioBlocks: A = A.b_c_d_e_s.replace(a, "test2")
}

class NamedTupleLensGetBenchmark extends BaseBenchmark {
  import zio.blocks.schema.LensDomain._

  var namedTuple: NamedTuple25 = (
    i01 = 1,
    i02 = 2,
    i03 = 3,
    i04 = 4,
    i05 = 5,
    i06 = 6,
    i07 = 7,
    i08 = 8,
    i09 = 9,
    i10 = 10,
    s11 = "",
    s12 = "",
    s13 = "",
    s14 = "",
    s15 = "",
    s16 = "",
    s17 = "",
    s18 = "",
    s19 = "",
    s20 = "",
    s21 = "",
    s22 = "",
    s23 = "",
    s24 = "",
    s25 = ""
  )

  @Benchmark
  def direct: String = namedTuple.s25

  @Benchmark
  def zioBlocks: String = NamedTuple25.s25.get(namedTuple)
}

import chanterelle._

class NamedTupleLensReplaceBenchmark extends BaseBenchmark {
  import zio.blocks.schema.LensDomain._

  var namedTuple: NamedTuple25 = (
    i01 = 1,
    i02 = 2,
    i03 = 3,
    i04 = 4,
    i05 = 5,
    i06 = 6,
    i07 = 7,
    i08 = 8,
    i09 = 9,
    i10 = 10,
    s11 = "",
    s12 = "",
    s13 = "",
    s14 = "",
    s15 = "",
    s16 = "",
    s17 = "",
    s18 = "",
    s19 = "",
    s20 = "",
    s21 = "",
    s22 = "",
    s23 = "",
    s24 = "",
    s25 = ""
  )

  @Benchmark
  def chanterelle: NamedTuple25 = namedTuple.transform(_.update(_.s25)(_ => "test"))

  @Benchmark
  def zioBlocks: NamedTuple25 = NamedTuple25.s25.replace(namedTuple, "test")
}

object LensDomain {
  case class E(s: String)

  case class D(e: E)

  case class C(d: D)

  case class B(c: C)

  case class A(b: B)

  object A extends CompanionOptics[A] {
    implicit val schema: Schema[A] = Schema.derived
    val b_c_d_e_s: Lens[A, String] = optic(_.b.c.d.e.s)
  }

  object A_ {
    import com.softwaremill.quicklens._

    val b_c_d_e_s_quicklens: A => PathModify[A, String] =
      (modify(_: A)(_.b))
        .andThenModify(modify(_: B)(_.c))
        .andThenModify(modify(_: C)(_.d))
        .andThenModify(modify(_: D)(_.e))
        .andThenModify(modify(_: E)(_.s))
    val b_c_d_e_s_monocle: PLens[A, A, String, String] =
      Focus[A](_.b).andThen(Focus[B](_.c)).andThen(Focus[C](_.d)).andThen(Focus[D](_.e)).andThen(Focus[E](_.s))
  }

  type NamedTuple25 = (
    i01: Int,
    i02: Int,
    i03: Int,
    i04: Int,
    i05: Int,
    i06: Int,
    i07: Int,
    i08: Int,
    i09: Int,
    i10: Int,
    s11: String,
    s12: String,
    s13: String,
    s14: String,
    s15: String,
    s16: String,
    s17: String,
    s18: String,
    s19: String,
    s20: String,
    s21: String,
    s22: String,
    s23: String,
    s24: String,
    s25: String
  )

  object NamedTuple25 extends CompanionOptics[NamedTuple25] {
    implicit val schema: Schema[NamedTuple25] = Schema.derived
    val s25: Lens[NamedTuple25, String]       = $(_.s25)
  }
}
