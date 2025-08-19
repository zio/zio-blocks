package zio.blocks.schema

import monocle.{Focus, PLens}
import org.openjdk.jmh.annotations._

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

/* FIXME: sbt fmt fails to format it
class NamedTupleLensBenchmark extends BaseBenchmark {
  import zio.blocks.schema.LensDomain._

  var namedTuple: NamedTuple25 = (
    s01 = "",
    s02 = "",
    s03 = "",
    s04 = "",
    s05 = "",
    s06 = "",
    s07 = "",
    s08 = "",
    s09 = "",
    s10 = "",
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
  def get: String = NamedTuple25.s25.get(namedTuple)

  @Benchmark
  def replace: NamedTuple25 = NamedTuple25.s25.replace(namedTuple, "test")
}
 */

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
  /* FIXME: sbt fmt fails to format it
  type NamedTuple25 = (
    s01: String,
    s02: String,
    s03: String,
    s04: String,
    s05: String,
    s06: String,
    s07: String,
    s08: String,
    s09: String,
    s10: String,
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
   */
}
