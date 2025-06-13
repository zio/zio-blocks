package zio.blocks.schema

import monocle.{Focus, PLens}
import org.openjdk.jmh.annotations._

class LensGetBenchmark extends BaseBenchmark {
  var a: LensDomain.A = LensDomain.A(LensDomain.B(LensDomain.C(LensDomain.D(LensDomain.E("test")))))

  @Benchmark
  def direct: String = a.b.c.d.e.s

  @Benchmark
  def monocle: String = LensDomain.A_.b_c_d_e_s_monocle.get(a)

  @Benchmark
  def zioBlocks: String = LensDomain.A.b_c_d_e_s.get(a)
}

class LensReplaceBenchmark extends BaseBenchmark {
  var a: LensDomain.A = LensDomain.A(LensDomain.B(LensDomain.C(LensDomain.D(LensDomain.E("test")))))

  @Benchmark
  def direct: LensDomain.A = {
    val a = this.a
    val b = a.b
    val c = b.c
    val d = c.d
    a.copy(b = b.copy(c = c.copy(d = d.copy(e = d.e.copy(s = "test2")))))
  }

  @Benchmark
  def monocle: LensDomain.A = LensDomain.A_.b_c_d_e_s_monocle.replace("test2").apply(a)

  @Benchmark
  def quicklens: LensDomain.A = {
    import com.softwaremill.quicklens._

    LensDomain.A_.b_c_d_e_s_quicklens.apply(a).setTo("test2")
  }

  @Benchmark
  def zioBlocks: LensDomain.A = LensDomain.A.b_c_d_e_s.replace(a, "test2")
}

object LensDomain {
  case class E(s: String)

  object E extends CompanionOptics[E] {
    implicit val schema: Schema[E] = Schema.derived
  }

  case class D(e: E)

  object D extends CompanionOptics[D] {
    implicit val schema: Schema[D] = Schema.derived
  }

  case class C(d: D)

  object C extends CompanionOptics[C] {
    implicit val schema: Schema[C] = Schema.derived
  }

  case class B(c: C)

  object B extends CompanionOptics[B] {
    implicit val schema: Schema[B] = Schema.derived
  }

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
}
