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

import monocle.{Focus, POptional}
import monocle.macros.GenPrism
import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark

class OptionalGetOptionBenchmark extends BaseBenchmark {
  import zio.blocks.schema.OptionalDomain._

  var a1: OptionalDomain.A1 = A1(B1(C1(D1(E1("test")))))

  @Benchmark
  def direct: Option[String] = {
    a1.b match {
      case b1: B1 =>
        b1.c match {
          case c1: C1 =>
            c1.d match {
              case d1: D1 =>
                d1.e match {
                  case e1: E1 => return new Some(e1.s)
                  case _      =>
                }
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
    None
  }

  @Benchmark
  def monocle: Option[String] = A1_.b_b1_c_c1_d_d1_e_e1_s_monocle.getOption(a1)

  @Benchmark
  def zioBlocks: Option[String] = A1.b_b1_c_c1_d_d1_e_e1_s.getOption(a1)
}

class OptionalReplaceBenchmark extends BaseBenchmark {
  import zio.blocks.schema.OptionalDomain._

  var a1: A1 = A1(B1(C1(D1(E1("test")))))

  @Benchmark
  def direct: A1 = {
    val a1 = this.a1
    a1.b match {
      case b1: B1 =>
        b1.c match {
          case c1: C1 =>
            c1.d match {
              case d1: D1 =>
                d1.e match {
                  case e1: E1 =>
                    return a1.copy(b = b1.copy(c = c1.copy(d = d1.copy(e = e1.copy(s = "test2")))))
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
    a1
  }

  @Benchmark
  def monocle: A1 = A1_.b_b1_c_c1_d_d1_e_e1_s_monocle.replace("test2").apply(a1)

  @Benchmark
  def quicklens: A1 = A1_.b_b1_c_c1_d_d1_e_e1_s_quicklens.apply(a1).setTo("test2")

  @Benchmark
  def zioBlocks: A1 = A1.b_b1_c_c1_d_d1_e_e1_s.replace(a1, "test2")
}

object OptionalDomain {
  sealed trait E

  case class E1(s: String) extends E

  case class E2(i: Int) extends E

  sealed trait D

  case class D1(e: E) extends D

  case class D2(i: Int) extends D

  sealed trait C

  case class C1(d: D) extends C

  case class C2(i: Int) extends C

  sealed abstract class B

  case class B1(c: C) extends B

  case class B2(i: Int) extends B

  case class A1(b: B)

  object A1 extends CompanionOptics[A1] {
    implicit val schema: Schema[A1]                 = Schema.derived
    val b_b1_c_c1_d_d1_e_e1_s: Optional[A1, String] = optic(_.b.when[B1].c.when[C1].d.when[D1].e.when[E1].s)
  }

  object A1_ {
    import com.softwaremill.quicklens._

    val b_b1_c_c1_d_d1_e_e1_s_quicklens: A1 => PathModify[A1, String] =
      (modify(_: A1)(_.b))
        .andThenModify(modify(_: B)(_.when[B1]))
        .andThenModify(modify(_: B1)(_.c))
        .andThenModify(modify(_: C)(_.when[C1]))
        .andThenModify(modify(_: C1)(_.d))
        .andThenModify(modify(_: D)(_.when[D1]))
        .andThenModify(modify(_: D1)(_.e))
        .andThenModify(modify(_: E)(_.when[E1]))
        .andThenModify(modify(_: E1)(_.s))
    val b_b1_c_c1_d_d1_e_e1_s_monocle: POptional[A1, A1, String, String] =
      Focus[A1](_.b)
        .andThen(GenPrism[B, B1])
        .andThen(Focus[B1](_.c))
        .andThen(GenPrism[C, C1])
        .andThen(Focus[C1](_.d))
        .andThen(GenPrism[D, D1])
        .andThen(Focus[D1](_.e))
        .andThen(GenPrism[E, E1])
        .andThen(Focus[E1](_.s))
  }
}
