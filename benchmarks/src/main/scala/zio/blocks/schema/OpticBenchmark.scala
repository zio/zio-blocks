package zio.blocks.schema

import com.softwaremill.quicklens._
import monocle.{Focus, PLens, POptional}
import monocle.macros.GenPrism
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.{
  Binding,
  Constructor,
  Deconstructor,
  Discriminator,
  Matcher,
  Matchers,
  RegisterOffset,
  Registers
}
import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
abstract class BaseBenchmark

class LensGetBenchmark extends BaseBenchmark {
  import LensDomain._

  var a: A = A(B(C(D(E("test")))))

  @Benchmark
  def direct: String = a.b.c.d.e.s

  @Benchmark
  def monocle: String = A.b_c_d_e_s_monocle.get(a)

  @Benchmark
  def zioBlocks: String = A.b_c_d_e_s.get(a)
}

class LensReplaceBenchmark extends BaseBenchmark {
  import LensDomain._

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
  def monocle: A = A.b_c_d_e_s_monocle.replace("test2").apply(a)

  @Benchmark
  def quicklens: A = A.b_c_d_e_s_quicklens.apply(a).setTo("test2")

  @Benchmark
  def zioBlocks: A = A.b_c_d_e_s.replace(a, "test2")
}

class OptionalGetOptionBenchmark extends BaseBenchmark {
  import OptionalDomain._

  var a1: A1 = A1(B1(C1(D1(E1("test")))))

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
  def monocle: Option[String] = A1.b_b1_c_c1_d_d1_e_e1_s_monocle.getOption(a1)

  @Benchmark
  def zioBlocks: Option[String] = A1.b_b1_c_c1_d_d1_e_e1_s.getOption(a1)
}

class OptionalReplaceBenchmark extends BaseBenchmark {
  import OptionalDomain._

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
                  case e1: E1 => return a1.copy(b = b1.copy(c = c1.copy(d = d1.copy(e = e1.copy(s = "test2")))))
                  case _      =>
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
  def monocle: A1 = A1.b_b1_c_c1_d_d1_e_e1_s_monocle.replace("test2").apply(a1)

  @Benchmark
  def quicklens: A1 = A1.b_b1_c_c1_d_d1_e_e1_s_quicklens.apply(a1).setTo("test2")

  @Benchmark
  def zioBlocks: A1 = A1.b_b1_c_c1_d_d1_e_e1_s.replace(a1, "test2")
}

class TraversalFoldBenchmark extends BaseBenchmark {
  import TraversalDomain._

  @Param(Array("1", "10", "100", "1000", "10000"))
  var size: Int = 10

  var ai: Array[Int] = (1 to size).toArray

  @Setup
  def setup(): Unit = ai = (1 to size).toArray

  @Benchmark
  def direct: Int = {
    var res = 0
    var i   = 0
    while (i < ai.length) {
      res += ai(i)
      i += 1
    }
    res
  }

  @Benchmark
  def zioBlocks: Int = a_i.fold[Int](ai)(0, _ + _)
}

class TraversalModifyBenchmark extends BaseBenchmark {
  import TraversalDomain._

  @Param(Array("1", "10", "100", "1000", "10000"))
  var size: Int = 10

  var ai: Array[Int] = (1 to size).toArray

  @Setup
  def setup(): Unit = ai = (1 to size).toArray

  @Benchmark
  def direct: Array[Int] = {
    var res = new Array[Int](ai.length)
    var i   = 0
    while (i < ai.length) {
      res(i) = ai(i) + 1
      i += 1
    }
    res
  }

  @Benchmark
  def zioBlocks: Array[Int] = a_i.modify(ai, _ + 1)
}

object LensDomain {
  case class E(s: String)

  object E {
    val reflect: Reflect.Record.Bound[E] = Reflect.Record(
      fields = List(
        Term("s", Reflect.string, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "E"),
      recordBinding = Binding.Record(
        constructor = new Constructor[E] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): E =
            E(in.getObject(baseOffset, 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[E] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: E): Unit =
            out.setObject(baseOffset, 0, in.s)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val s: Lens.Bound[E, String] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[E, String]])
  }

  case class D(e: E)

  object D {
    val reflect: Reflect.Record.Bound[D] = Reflect.Record(
      fields = List(
        Term("e", E.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "D"),
      recordBinding = Binding.Record(
        constructor = new Constructor[D] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): D =
            D(in.getObject(baseOffset, 0).asInstanceOf[E])
        },
        deconstructor = new Deconstructor[D] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: D): Unit =
            out.setObject(baseOffset, 0, in.e)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val e: Lens.Bound[D, E] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[D, E]])
  }

  case class C(d: D)

  object C {
    val reflect: Reflect.Record.Bound[C] = Reflect.Record(
      fields = List(
        Term("d", D.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "C"),
      recordBinding = Binding.Record(
        constructor = new Constructor[C] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): C =
            C(in.getObject(baseOffset, 0).asInstanceOf[D])
        },
        deconstructor = new Deconstructor[C] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: C): Unit =
            out.setObject(baseOffset, 0, in.d)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val d: Lens.Bound[C, D] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[C, D]])
  }

  case class B(c: C)

  object B {
    val reflect: Reflect.Record.Bound[B] = Reflect.Record(
      fields = List(
        Term("c", C.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "B"),
      recordBinding = Binding.Record(
        constructor = new Constructor[B] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): B =
            B(in.getObject(baseOffset, 0).asInstanceOf[C])
        },
        deconstructor = new Deconstructor[B] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: B): Unit =
            out.setObject(baseOffset, 0, in.c)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val c: Lens.Bound[B, C] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[B, C]])
  }

  case class A(b: B)

  object A {
    val reflect: Reflect.Record.Bound[A] = Reflect.Record(
      fields = List(
        Term("b", B.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "A"),
      recordBinding = Binding.Record(
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): A =
            A(in.getObject(baseOffset, 0).asInstanceOf[B])
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A): Unit =
            out.setObject(baseOffset, 0, in.b)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val b: Lens.Bound[A, B] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[A, B]])
    val b_c_d_e_s: Lens.Bound[A, String] =
      b.apply(B.c).apply(C.d).apply(D.e).apply(E.s)
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

object OptionalDomain {
  sealed trait E

  object E {
    val reflect: Reflect.Variant.Bound[E] = Reflect.Variant(
      cases = List(
        Term("e1", E1.reflect, Doc.Empty, Nil),
        Term("e2", E2.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "E"),
      variantBinding = Binding.Variant(
        discriminator = new Discriminator[E] {
          def discriminate(a: E): Int = a match {
            case _: E1 => 0
            case _: E2 => 1
          }
        },
        matchers = Matchers(
          new Matcher[E1] {
            def downcastOrNull(a: Any): E1 = a match {
              case x: E1 => x
              case _     => null
            }
          },
          new Matcher[E2] {
            def downcastOrNull(a: Any): E2 = a match {
              case x: E2 => x
              case _     => null
            }
          }
        )
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val e1: Prism.Bound[E, E1] = Prism(reflect, reflect.cases(0).asInstanceOf[Term.Bound[E, E1]])
  }

  case class E1(s: String) extends E

  object E1 {
    val reflect: Reflect.Record.Bound[E1] = Reflect.Record(
      fields = List(
        Term("s", Reflect.string, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "E1"),
      recordBinding = Binding.Record(
        constructor = new Constructor[E1] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): E1 =
            E1(in.getObject(baseOffset, 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[E1] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: E1): Unit =
            out.setObject(baseOffset, 0, in.s)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val s: Lens.Bound[E1, String] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[E1, String]])
  }

  case class E2(i: Int) extends E

  object E2 {
    val reflect: Reflect.Record.Bound[E2] = Reflect.Record(
      fields = List(
        Term("i", Reflect.int, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "E2"),
      recordBinding = Binding.Record(
        constructor = new Constructor[E2] {
          def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): E2 =
            E2(in.getInt(baseOffset, 0))
        },
        deconstructor = new Deconstructor[E2] {
          def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: E2): Unit =
            out.setInt(baseOffset, 0, in.i)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val i: Lens.Bound[E2, Int] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[E2, Int]])
  }

  sealed trait D

  object D {
    val reflect: Reflect.Variant.Bound[D] = Reflect.Variant(
      cases = List(
        Term("d1", D1.reflect, Doc.Empty, Nil),
        Term("d2", D2.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "D"),
      variantBinding = Binding.Variant(
        discriminator = new Discriminator[D] {
          def discriminate(a: D): Int = a match {
            case _: D1 => 0
            case _: D2 => 1
          }
        },
        matchers = Matchers(
          new Matcher[D1] {
            def downcastOrNull(a: Any): D1 = a match {
              case x: D1 => x
              case _     => null
            }
          },
          new Matcher[D2] {
            def downcastOrNull(a: Any): D2 = a match {
              case x: D2 => x
              case _     => null
            }
          }
        )
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val d1: Prism.Bound[D, D1] = Prism(reflect, reflect.cases(0).asInstanceOf[Term.Bound[D, D1]])
  }

  case class D1(e: E) extends D

  object D1 {
    val reflect: Reflect.Record.Bound[D1] = Reflect.Record(
      fields = List(
        Term("e", E.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "D1"),
      recordBinding = Binding.Record(
        constructor = new Constructor[D1] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): D1 =
            D1(in.getObject(baseOffset, 0).asInstanceOf[E])
        },
        deconstructor = new Deconstructor[D1] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: D1): Unit =
            out.setObject(baseOffset, 0, in.e)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val e: Lens.Bound[D1, E] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[D1, E]])
  }

  case class D2(i: Int) extends D

  object D2 {
    val reflect: Reflect.Record.Bound[D2] = Reflect.Record(
      fields = List(
        Term("i", Reflect.int, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "D2"),
      recordBinding = Binding.Record(
        constructor = new Constructor[D2] {
          def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): D2 =
            D2(in.getInt(baseOffset, 0))
        },
        deconstructor = new Deconstructor[D2] {
          def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: D2): Unit =
            out.setInt(baseOffset, 0, in.i)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val i: Lens.Bound[D2, Int] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[D2, Int]])
  }

  sealed trait C

  object C {
    val reflect: Reflect.Variant.Bound[C] = Reflect.Variant(
      cases = List(
        Term("c1", C1.reflect, Doc.Empty, Nil),
        Term("c2", C2.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "C"),
      variantBinding = Binding.Variant(
        discriminator = new Discriminator[C] {
          def discriminate(a: C): Int = a match {
            case _: C1 => 0
            case _: C2 => 1
          }
        },
        matchers = Matchers(
          new Matcher[C1] {
            def downcastOrNull(a: Any): C1 = a match {
              case x: C1 => x
              case _     => null
            }
          },
          new Matcher[C2] {
            def downcastOrNull(a: Any): C2 = a match {
              case x: C2 => x
              case _     => null
            }
          }
        )
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val c1: Prism.Bound[C, C1] = Prism(reflect, reflect.cases(0).asInstanceOf[Term.Bound[C, C1]])
  }

  case class C1(d: D) extends C

  object C1 {
    val reflect: Reflect.Record.Bound[C1] = Reflect.Record(
      fields = List(
        Term("d", D.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "C1"),
      recordBinding = Binding.Record(
        constructor = new Constructor[C1] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): C1 =
            C1(in.getObject(baseOffset, 0).asInstanceOf[D])
        },
        deconstructor = new Deconstructor[C1] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: C1): Unit =
            out.setObject(baseOffset, 0, in.d)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val d: Lens.Bound[C1, D] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[C1, D]])
  }

  case class C2(i: Int) extends C

  object C2 {
    val reflect: Reflect.Record.Bound[C2] = Reflect.Record(
      fields = List(
        Term("i", Reflect.int, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "C2"),
      recordBinding = Binding.Record(
        constructor = new Constructor[C2] {
          def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): C2 =
            C2(in.getInt(baseOffset, 0))
        },
        deconstructor = new Deconstructor[C2] {
          def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: C2): Unit =
            out.setInt(baseOffset, 0, in.i)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val i: Lens.Bound[C2, Int] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[C2, Int]])
  }

  sealed trait B

  object B {
    val reflect: Reflect.Variant.Bound[B] = Reflect.Variant(
      cases = List(
        Term("b1", B1.reflect, Doc.Empty, Nil),
        Term("b2", B2.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "B"),
      variantBinding = Binding.Variant(
        discriminator = new Discriminator[B] {
          def discriminate(a: B): Int = a match {
            case _: B1 => 0
            case _: B2 => 1
          }
        },
        matchers = Matchers(
          new Matcher[B1] {
            def downcastOrNull(a: Any): B1 = a match {
              case x: B1 => x
              case _     => null
            }
          },
          new Matcher[B2] {
            def downcastOrNull(a: Any): B2 = a match {
              case x: B2 => x
              case _     => null
            }
          }
        )
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val b1: Prism.Bound[B, B1] = Prism(reflect, reflect.cases(0).asInstanceOf[Term.Bound[B, B1]])
  }

  case class B1(c: C) extends B

  object B1 {
    val reflect: Reflect.Record.Bound[B1] = Reflect.Record(
      fields = List(
        Term("c", C.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "B1"),
      recordBinding = Binding.Record(
        constructor = new Constructor[B1] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): B1 =
            B1(in.getObject(baseOffset, 0).asInstanceOf[C1])
        },
        deconstructor = new Deconstructor[B1] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: B1): Unit =
            out.setObject(baseOffset, 0, in.c)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val c: Lens.Bound[B1, C] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[B1, C]])
  }

  case class B2(i: Int) extends B

  object B2 {
    val reflect: Reflect.Record.Bound[B2] = Reflect.Record(
      fields = List(
        Term("i", Reflect.int, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "B2"),
      recordBinding = Binding.Record(
        constructor = new Constructor[B2] {
          def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): B2 =
            B2(in.getInt(baseOffset, 0))
        },
        deconstructor = new Deconstructor[B2] {
          def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: B2): Unit =
            out.setInt(baseOffset, 0, in.i)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val i: Lens.Bound[B2, Int] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[B2, Int]])
  }

  case class A1(b: B)

  object A1 {
    val reflect: Reflect.Record.Bound[A1] = Reflect.Record(
      fields = List(
        Term("b", B1.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "A1"),
      recordBinding = Binding.Record(
        constructor = new Constructor[A1] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): A1 =
            A1(in.getObject(baseOffset, 0).asInstanceOf[B1])
        },
        deconstructor = new Deconstructor[A1] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A1): Unit =
            out.setObject(baseOffset, 0, in.b)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val b: Lens.Bound[A1, B] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[A1, B]])
    val b_b1_c_c1_d_d1_e_e1_s: Optional.Bound[A1, String] =
      b(B.b1)(B1.c)(C.c1)(C1.d)(D.d1)(D1.e)(E.e1)(E1.s)
    val b_b1_c_c1_d_d1_e_e1_s_quicklens: A1 => PathModify[A1, String] =
      (modify(_: A1)(_.b.when[B1]))
        .andThenModify(modify(_: B1)(_.c.when[C1]))
        .andThenModify(modify(_: C1)(_.d.when[D1]))
        .andThenModify(modify(_: D1)(_.e.when[E1]))
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

object TraversalDomain {
  val a_i: Traversal.Bound[Array[Int], Int] = Traversal.arrayValues(Reflect.int[Binding])
}
