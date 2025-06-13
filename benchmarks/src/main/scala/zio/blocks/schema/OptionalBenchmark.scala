package zio.blocks.schema

import monocle.{Focus, POptional}
import monocle.macros.GenPrism
import org.openjdk.jmh.annotations._

class OptionalGetOptionBenchmark extends BaseBenchmark {
  var a1: OptionalDomain.A1 =
    OptionalDomain.A1(OptionalDomain.B1(OptionalDomain.C1(OptionalDomain.D1(OptionalDomain.E1("test")))))

  @Benchmark
  def direct: Option[String] = {
    a1.b match {
      case b1: OptionalDomain.B1 =>
        b1.c match {
          case c1: OptionalDomain.C1 =>
            c1.d match {
              case d1: OptionalDomain.D1 =>
                d1.e match {
                  case e1: OptionalDomain.E1 => return new Some(e1.s)
                  case _                     =>
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
  def monocle: Option[String] = OptionalDomain.A1_.b_b1_c_c1_d_d1_e_e1_s_monocle.getOption(a1)

  @Benchmark
  def zioBlocks: Option[String] = OptionalDomain.A1.b_b1_c_c1_d_d1_e_e1_s.getOption(a1)
}

class OptionalReplaceBenchmark extends BaseBenchmark {
  var a1: OptionalDomain.A1 =
    OptionalDomain.A1(OptionalDomain.B1(OptionalDomain.C1(OptionalDomain.D1(OptionalDomain.E1("test")))))

  @Benchmark
  def direct: OptionalDomain.A1 = {
    val a1 = this.a1
    a1.b match {
      case b1: OptionalDomain.B1 =>
        b1.c match {
          case c1: OptionalDomain.C1 =>
            c1.d match {
              case d1: OptionalDomain.D1 =>
                d1.e match {
                  case e1: OptionalDomain.E1 =>
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
  def monocle: OptionalDomain.A1 = OptionalDomain.A1_.b_b1_c_c1_d_d1_e_e1_s_monocle.replace("test2").apply(a1)

  @Benchmark
  def quicklens: OptionalDomain.A1 = {
    import com.softwaremill.quicklens._

    OptionalDomain.A1_.b_b1_c_c1_d_d1_e_e1_s_quicklens.apply(a1).setTo("test2")
  }

  @Benchmark
  def zioBlocks: OptionalDomain.A1 = OptionalDomain.A1.b_b1_c_c1_d_d1_e_e1_s.replace(a1, "test2")
}

object OptionalDomain {
  sealed trait E

  object E extends CompanionOptics[E] {
    implicit val schema: Schema[E] = Schema.derived
  }

  case class E1(s: String) extends E

  object E1 extends CompanionOptics[E1] {
    implicit val schema: Schema[E1] = Schema.derived
  }

  case class E2(i: Int) extends E

  object E2 extends CompanionOptics[E2] {
    implicit val schema: Schema[E2] = Schema.derived
  }

  sealed trait D

  object D extends CompanionOptics[D] {
    implicit val schema: Schema[D] = Schema.derived
  }

  case class D1(e: E) extends D

  object D1 extends CompanionOptics[D1] {
    implicit val schema: Schema[D1] = Schema.derived
  }

  case class D2(i: Int) extends D

  object D2 extends CompanionOptics[D2] {
    implicit val schema: Schema[D2] = Schema.derived
  }

  sealed trait C

  object C extends CompanionOptics[C] {
    implicit val schema: Schema[C] = Schema.derived
  }

  case class C1(d: D) extends C

  object C1 extends CompanionOptics[C1] {
    implicit val schema: Schema[C1] = Schema.derived
  }

  case class C2(i: Int) extends C

  object C2 extends CompanionOptics[C2] {
    implicit val schema: Schema[C2] = Schema.derived
  }

  sealed abstract class B

  object B extends CompanionOptics[B] {
    implicit val schema: Schema[B] = Schema.derived
  }

  case class B1(c: C) extends B

  object B1 extends CompanionOptics[B1] {
    implicit val schema: Schema[B1] = Schema.derived
  }

  case class B2(i: Int) extends B

  object B2 extends CompanionOptics[B2] {
    implicit val schema: Schema[B2] = Schema.derived
  }

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
