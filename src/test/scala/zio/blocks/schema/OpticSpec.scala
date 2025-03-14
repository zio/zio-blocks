package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object OpticSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("OpticSpec")(
    suite("Lens")(
      test("has consistent equals and hashCode") {
        assert(Record1.b)(equalTo(Record1.b)) &&
        assert(Record1.b.hashCode)(equalTo(Record1.b.hashCode)) &&
        assert(Record2.r1_b)(equalTo(Record2.r1_b)) &&
        assert(Record2.r1_b.hashCode)(equalTo(Record2.r1_b.hashCode)) &&
        assert(Record3.v1)(equalTo(Record3.v1)) &&
        assert(Record3.v1.hashCode)(equalTo(Record3.v1.hashCode)) &&
        assert(Record1.f: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.l: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.vi: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.r1: Any)(not(equalTo(Record1.b))) &&
        assert(Record2.r1_f: Any)(not(equalTo(Record2.r1_b)))
        assert(Record2.r1_b: Any)(not(equalTo("")))
      },
      test("has associative equals and hashCode") {
        assert(Record3.r2_r1_b_left: Any)(equalTo(Record3.r2_r1_b_right)) &&
        assert(Record3.r2_r1_b_left.hashCode)(equalTo(Record3.r2_r1_b_right.hashCode))
      },
      test("returns an initial structure") {
        assert(Record1.b.structure)(equalTo(Record1.reflect)) &&
        assert(Record2.r1_b.structure)(equalTo(Record2.reflect))
      },
      test("returns a focus structure") {
        assert(Record1.b.focus)(equalTo(Reflect.boolean[Binding])) &&
        assert(Record2.r1_b.focus)(equalTo(Reflect.boolean[Binding]))
      },
      test("refines a binding") {
        assert(Record1.b.refineBinding(RefineBinding.noBinding()))(equalTo(Record1.b.noBinding)) &&
        assert(Record2.r1_b.refineBinding(RefineBinding.noBinding()))(equalTo(Record2.r1_b.noBinding))
      },
      test("gets a focus value") {
        assert(Record1.b.get(Record1(true, 1)))(equalTo(true)) &&
        assert(Record1.b.get(Record1(false, 1)))(equalTo(false)) &&
        assert(Record2.r1_b.get(Record2(2L, Vector.empty, Record1(true, 1))))(equalTo(true)) &&
        assert(Record2.r1_b.get(Record2(2L, Vector.empty, Record1(false, 1))))(equalTo(false)) &&
        assert(
          Record3.r2_r1_b_left.get(Record3(Record1(false, 3), Record2(2L, Vector.empty, Record1(true, 1)), Case1(0.5)))
        )(
          equalTo(true)
        ) &&
        assert(
          Record3.r2_r1_b_right.get(Record3(Record1(true, 3), Record2(2L, Vector.empty, Record1(false, 1)), Case1(0.5)))
        )(
          equalTo(false)
        )
      },
      test("sets a focus value") {
        assert(Record1.b.set(Record1(true, 1), false))(equalTo(Record1(false, 1))) &&
        assert(Record2.r1_b.set(Record2(2L, Vector.empty, Record1(true, 1)), false))(
          equalTo(Record2(2L, Vector.empty, Record1(false, 1)))
        ) &&
        assert(
          Record3.r2_r1_b_left
            .set(Record3(Record1(true, 3), Record2(2L, Vector.empty, Record1(true, 1)), Case1(0.5)), false)
        )(
          equalTo(Record3(Record1(true, 3), Record2(2L, Vector.empty, Record1(false, 1)), Case1(0.5)))
        )
      }
    ),
    suite("Prism")(
      test("has consistent equals and hashCode") {
        assert(Variant1.c1)(equalTo(Variant1.c1)) &&
        assert(Variant1.c1.hashCode)(equalTo(Variant1.c1.hashCode)) &&
        assert(Variant1.c2: Any)(not(equalTo(Variant1.c1))) &&
        assert(Variant1.v2: Any)(not(equalTo(Variant1.c1))) &&
        assert(Variant2.c3: Any)(not(equalTo(Variant1.c1))) &&
        assert(Variant2.c4: Any)(not(equalTo(Variant1.c1)))
      },
      test("returns a base class structure") {
        assert(Variant1.c1.structure)(equalTo(Variant1.reflect)) &&
        assert(Variant1.c2.structure)(equalTo(Variant1.reflect)) &&
        assert(Variant1.v2.structure)(equalTo(Variant1.reflect)) &&
        assert(Variant1.v2_c3.structure)(equalTo(Variant1.reflect)) &&
        assert(Variant1.v2_c4.structure)(equalTo(Variant1.reflect))
      },
      test("returns a case class structure") {
        assert(Variant1.c1.focus)(equalTo(Case1.reflect)) &&
        assert(Variant1.c2.focus)(equalTo(Case2.reflect)) &&
        assert(Variant1.v2.focus)(equalTo(Variant2.reflect)) &&
        assert(Variant1.v2_c3.focus)(equalTo(Case3.reflect)) &&
        assert(Variant1.v2_c4.focus)(equalTo(Case4.reflect))
      },
      test("refines a binding") {
        assert(Variant1.c1.refineBinding(RefineBinding.noBinding()))(equalTo(Variant1.c1.noBinding)) &&
        assert(Variant1.v2_c3.refineBinding(RefineBinding.noBinding()))(equalTo(Variant1.v2_c3.noBinding))
      },
      test("gets an optional case class value") {
        assert(Variant1.c1.getOption(Case1(0.1): Variant1))(isSome(equalTo(Case1(0.1)))) &&
        assert(Variant1.c2.getOption(Case2(Record3(null, null, null)): Variant1))(
          isSome(equalTo(Case2(Record3(null, null, null))))
        ) &&
        assert(Variant1.v2.getOption(Case3(Case1(0.1)): Variant1))(isSome(equalTo(Case3(Case1(0.1)): Variant2))) &&
        assert(Variant1.v2_c3.getOption(Case3(Case1(0.1)): Variant1))(isSome(equalTo(Case3(Case1(0.1))))) &&
        assert(Variant2.c3.getOption(Case3(Case1(0.1)): Variant2))(isSome(equalTo(Case3(Case1(0.1))))) &&
        assert(Variant2.c4.getOption(Case4(List(Record3(null, null, null))): Variant2))(
          isSome(equalTo(Case4(List(Record3(null, null, null)))))
        )
      },
      test("reverse gets a base class value") {
        assert(Variant1.c1.reverseGet(Case1(0.1)))(equalTo(Case1(0.1): Variant1)) &&
        assert(Variant1.c2.reverseGet(Case2(Record3(null, null, null))))(
          equalTo(Case2(Record3(null, null, null)): Variant1)
        ) &&
        assert(Variant1.v2.reverseGet(Case3(Case1(0.1)): Variant2))(equalTo(Case3(Case1(0.1)): Variant1)) &&
        assert(Variant1.v2_c3.reverseGet(Case3(Case1(0.1))))(equalTo(Case3(Case1(0.1)): Variant1)) &&
        assert(Variant2.c3.reverseGet(Case3(Case1(0.1))))(equalTo(Case3(Case1(0.1)): Variant2)) &&
        assert(Variant2.c4.reverseGet(Case4(List(Record3(null, null, null)))))(
          equalTo(Case4(List(Record3(null, null, null))): Variant2)
        )
      }
    ),
    suite("Optional")(
      test("has consistent equals and hashCode") {
        assert(Variant1.c1_d)(equalTo(Variant1.c1_d)) &&
        assert(Variant1.c1_d.hashCode)(equalTo(Variant1.c1_d.hashCode)) &&
        assert(Variant1.c2_r3)(equalTo(Variant1.c2_r3)) &&
        assert(Variant1.c2_r3.hashCode)(equalTo(Variant1.c2_r3.hashCode)) &&
        assert(Variant1.c2_r3_r1)(equalTo(Variant1.c2_r3_r1)) &&
        assert(Variant1.c2_r3_r1.hashCode)(equalTo(Variant1.c2_r3_r1.hashCode)) &&
        assert(Variant2.c3_v1)(equalTo(Variant2.c3_v1)) &&
        assert(Variant2.c3_v1.hashCode)(equalTo(Variant2.c3_v1.hashCode)) &&
        assert(Variant2.c3_v1_v2)(equalTo(Variant2.c3_v1_v2)) &&
        assert(Variant2.c3_v1_v2.hashCode)(equalTo(Variant2.c3_v1_v2.hashCode)) &&
        assert(Variant2.c3_v1_v2_c4_lr3)(equalTo(Variant2.c3_v1_v2_c4_lr3)) &&
        assert(Variant2.c3_v1_v2_c4_lr3.hashCode)(equalTo(Variant2.c3_v1_v2_c4_lr3.hashCode)) &&
        assert(Case3.v1_c1_d)(equalTo(Case3.v1_c1_d)) &&
        assert(Case3.v1_c1_d.hashCode)(equalTo(Case3.v1_c1_d.hashCode)) &&
        assert(Variant1.c2_r3: Any)(not(equalTo(Variant1.c1_d))) &&
        assert(Variant1.c2_r3: Any)(not(equalTo(Case3.v1_c1_d)))
      },
      test("has associative equals and hashCode") {
        assert(Variant1.c2_r3_r2_r1_b_left)(equalTo(Variant1.c2_r3_r2_r1_b_right)) &&
        assert(Variant1.c2_r3_r2_r1_b_left.hashCode)(equalTo(Variant1.c2_r3_r2_r1_b_right.hashCode)) &&
        assert(Variant2.c3_v1_c1_left)(equalTo(Variant2.c3_v1_c1_right)) &&
        assert(Variant2.c3_v1_c1_left.hashCode)(equalTo(Variant2.c3_v1_c1_left.hashCode))
      },
      test("returns an initial structure") {
        assert(Variant1.c1_d.structure)(equalTo(Variant1.reflect)) &&
        assert(Variant1.c2_r3.structure)(equalTo(Variant1.reflect)) &&
        assert(Variant2.c3_v1_c1_left.structure)(equalTo(Variant2.reflect)) &&
        assert(Variant2.c3_v1_c1_right.structure)(equalTo(Variant2.reflect)) &&
        assert(Variant2.c3_v1_c1_d_left.structure)(equalTo(Variant2.reflect)) &&
        assert(Variant2.c3_v1_c1_d_right.structure)(equalTo(Variant2.reflect)) &&
        assert(Variant1.c2_r3_r1.structure)(equalTo(Variant1.reflect)) &&
        assert(Case3.v1_c1_d.structure)(equalTo(Case3.reflect)) &&
        assert(Case3.v1_c1.structure)(equalTo(Case3.reflect))
      },
      test("returns a focus structure") {
        assert(Variant1.c1_d.focus)(equalTo(Reflect.double[Binding])) &&
        assert(Variant1.c2_r3.focus)(equalTo(Record3.reflect)) &&
        assert(Variant2.c3_v1_c1_left.focus)(equalTo(Case1.reflect)) &&
        assert(Variant2.c3_v1_c1_right.focus)(equalTo(Case1.reflect)) &&
        assert(Variant2.c3_v1_c1_d_left.focus)(equalTo(Reflect.double[Binding])) &&
        assert(Variant2.c3_v1_c1_d_right.focus)(equalTo(Reflect.double[Binding])) &&
        assert(Variant1.c2_r3_r1.focus)(equalTo(Record1.reflect)) &&
        assert(Case3.v1_c1_d.focus)(equalTo(Reflect.double[Binding])) &&
        assert(Case3.v1_c1.focus)(equalTo(Case1.reflect))
      },
      test("refines a binding") {
        assert(Variant1.c1_d.refineBinding(RefineBinding.noBinding()))(equalTo(Variant1.c1_d.noBinding)) &&
        assert(Variant1.c2_r3.refineBinding(RefineBinding.noBinding()))(equalTo(Variant1.c2_r3.noBinding)) &&
        assert(Variant2.c3_v1_c1_left.refineBinding(RefineBinding.noBinding()))(
          equalTo(Variant2.c3_v1_c1_left.noBinding)
        ) &&
        assert(Variant2.c3_v1_c1_right.refineBinding(RefineBinding.noBinding()))(
          equalTo(Variant2.c3_v1_c1_right.noBinding)
        ) &&
        assert(Variant2.c3_v1_c1_d_left.refineBinding(RefineBinding.noBinding()))(
          equalTo(Variant2.c3_v1_c1_d_left.noBinding)
        ) &&
        assert(Variant2.c3_v1_c1_d_right.refineBinding(RefineBinding.noBinding()))(
          equalTo(Variant2.c3_v1_c1_d_right.noBinding)
        ) &&
        assert(Variant1.c2_r3_r1.refineBinding(RefineBinding.noBinding()))(
          equalTo(Variant1.c2_r3_r1.noBinding)
        ) &&
        assert(Case3.v1_c1_d.refineBinding(RefineBinding.noBinding()))(equalTo(Case3.v1_c1_d.noBinding))
      },
      test("gets an optional focus value") {
        assert(Variant1.c2_r3_r1.getOption(Case2(Record3(Record1(true, 0.1f), null, null))))(
          isSome(equalTo(Record1(true, 0.1f)))
        ) &&
        assert(Variant2.c3_v1_c1_left.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case1(0.1)))) &&
        assert(Variant2.c3_v1_c1_right.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case1(0.1)))) &&
        assert(Variant2.c3_v1_c1_d_right.getOption(Case3(Case1(0.1))))(isSome(equalTo(0.1))) &&
        assert(Variant2.c3_v1.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case1(0.1)))) &&
        assert(Case3.v1_c1_d.getOption(Case3(Case1(0.1))))(isSome(equalTo(0.1))) &&
        assert(Case3.v1_c1.getOption(Case3(Case1(0.1))))(isSome(equalTo(Case1(0.1))))
      },
      test("sets a focus value") {
        assert(Variant1.c2_r3_r1.set(Case2(Record3(Record1(true, 0.1f), null, null)), Record1(false, 0.2f)))(
          equalTo(Case2(Record3(Record1(false, 0.2f), null, null)))
        ) &&
        assert(Variant2.c3_v1_c1_left.set(Case3(Case1(0.1)), Case1(0.2)))(equalTo(Case3(Case1(0.2)))) &&
        assert(Variant2.c3_v1_c1_right.set(Case3(Case1(0.1)), Case1(0.2)))(equalTo(Case3(Case1(0.2)))) &&
        assert(Variant2.c3_v1_c1_d_right.set(Case3(Case1(0.1)), 0.2))(equalTo(Case3(Case1(0.2)))) &&
        assert(Variant2.c3_v1.set(Case3(Case1(0.1)), Case1(0.2)))(equalTo(Case3(Case1(0.2)))) &&
        assert(Case3.v1_c1_d.set(Case3(Case1(0.1)), 0.2))(equalTo(Case3(Case1(0.2)))) &&
        assert(Case3.v1_c1.set(Case3(Case1(0.1)), Case1(0.2)))(equalTo(Case3(Case1(0.2))))
      }
    ),
    suite("Traversal")(
      test("has consistent equals and hashCode") {
        assert(Record2.vi)(equalTo(Record2.vi)) &&
        assert(Record2.vi.hashCode)(equalTo(Record2.vi.hashCode)) &&
        assert(Collections.as)(equalTo(Collections.as)) &&
        assert(Collections.as.hashCode)(equalTo(Collections.as.hashCode)) &&
        assert(Collections.mkc)(equalTo(Collections.mkc)) &&
        assert(Collections.mkc.hashCode)(equalTo(Collections.mkc.hashCode)) &&
        assert(Collections.mvs)(equalTo(Collections.mvs)) &&
        assert(Collections.mvs.hashCode)(equalTo(Collections.mvs.hashCode)) &&
        assert(Record2.r1_f: Any)(not(equalTo(Record2.vi))) &&
        assert(Variant2.c4_lr3: Any)(not(equalTo(Case4.lr3))) &&
        assert(Collections.lb: Any)(not(equalTo(""))) &&
        assert(Collections.mkc: Any)(not(equalTo(""))) &&
        assert(Collections.mvs: Any)(not(equalTo("")))
      },
      test("returns an initial structure") {
        assert(Collections.as.structure)(equalTo(Reflect.array(Reflect.string[Binding]))) &&
        assert(Collections.mkc.structure)(equalTo(Reflect.map(Reflect.char[Binding], Reflect.string[Binding]))) &&
        assert(Collections.mvs.structure)(equalTo(Reflect.map(Reflect.char[Binding], Reflect.string[Binding]))) &&
        assert(Collections.lc1.structure)(equalTo(Reflect.list(Variant1.reflect))) &&
        assert(Collections.lc1_d.structure)(equalTo(Reflect.list(Variant1.reflect))) &&
        assert(Collections.lc4.structure)(equalTo(Reflect.list(Case4.reflect))) &&
        assert(Collections.lr1.structure)(equalTo(Reflect.list(Record1.reflect))) &&
        assert(Record2.vi.structure)(equalTo(Record2.reflect)) &&
        assert(Case4.lr3.structure)(equalTo(Case4.reflect)) &&
        assert(Variant2.c4_lr3.structure)(equalTo(Variant2.reflect)) &&
        assert(Variant2.c3_v1_v2_c4_lr3.structure)(equalTo(Variant2.reflect))
      },
      test("returns a collection element structure") {
        assert(Collections.as.focus)(equalTo(Reflect.string[Binding])) &&
        assert(Collections.mkc.focus)(equalTo(Reflect.char[Binding])) &&
        assert(Collections.mvs.focus)(equalTo(Reflect.string[Binding])) &&
        assert(Collections.lc1.focus)(equalTo(Case1.reflect)) &&
        assert(Collections.lc1_d.focus)(equalTo(Reflect.double[Binding])) &&
        assert(Collections.lc4.focus)(equalTo(Record3.reflect)) &&
        assert(Collections.lr1.focus)(equalTo(Reflect.boolean[Binding])) &&
        assert(Record2.vi.focus)(equalTo(Reflect.int[Binding])) &&
        assert(Case4.lr3.focus)(equalTo(Record3.reflect)) &&
        assert(Variant2.c4_lr3.focus)(equalTo(Record3.reflect)) &&
        assert(Variant2.c3_v1_v2_c4_lr3.focus)(equalTo(Record3.reflect))
      },
      test("refines a binding") {
        assert(Collections.as.refineBinding(RefineBinding.noBinding()))(equalTo(Collections.as.noBinding)) &&
        assert(Collections.mkc.refineBinding(RefineBinding.noBinding()))(equalTo(Collections.mkc.noBinding)) &&
        assert(Collections.mvs.refineBinding(RefineBinding.noBinding()))(equalTo(Collections.mvs.noBinding)) &&
        assert(Collections.lc1.refineBinding(RefineBinding.noBinding()))(equalTo(Collections.lc1.noBinding)) &&
        assert(Collections.lc1_d.refineBinding(RefineBinding.noBinding()))(equalTo(Collections.lc1_d.noBinding)) &&
        assert(Collections.lc4.refineBinding(RefineBinding.noBinding()))(equalTo(Collections.lc4.noBinding)) &&
        assert(Collections.lr1.refineBinding(RefineBinding.noBinding()))(equalTo(Collections.lr1.noBinding)) &&
        assert(Record2.vi.refineBinding(RefineBinding.noBinding()))(equalTo(Record2.vi.noBinding)) &&
        assert(Case4.lr3.refineBinding(RefineBinding.noBinding()))(equalTo(Case4.lr3.noBinding)) &&
        assert(Variant2.c4_lr3.refineBinding(RefineBinding.noBinding()))(equalTo(Variant2.c4_lr3.noBinding)) &&
        assert(Variant2.c3_v1_v2_c4_lr3.refineBinding(RefineBinding.noBinding()))(
          equalTo(Variant2.c3_v1_v2_c4_lr3.noBinding)
        )
      },
      test("folds collection values") {
        assert(Collections.as.fold[String](Array("1", "2", "3"))("", _ + _))(equalTo("123")) &&
        assert(Collections.mkc.fold[String](Map('a' -> "1", 'b' -> "2", 'c' -> "3"))("", _ + _.toString))(
          equalTo("abc")
        ) &&
        assert(Collections.mvs.fold[String](Map('a' -> "1", 'b' -> "2", 'c' -> "3"))("", _ + _))(equalTo("123")) &&
        assert(Collections.lc1.fold[String](List(Case1(0.1)))("", _ + _.toString))(equalTo("Case1(0.1)")) &&
        assert(Collections.lc1_d.fold[Double](List(Case1(0.1), Case1(0.4)))(0.0, _ + _))(equalTo(0.5)) &&
        assert(Collections.lc4.fold[String](List(Case4(List(Record3(null, null, null)))))("", _ + _.toString))(
          equalTo("Record3(null,null,null)")
        ) &&
        assert(Collections.lr1.fold[Boolean](List(Record1(true, 0.1f)))(false, _ | _))(equalTo(true)) &&
        assert(Record2.vi.fold[Int](Record2(2L, Vector(1, 2, 3), null))(0, _ + _))(equalTo(6)) &&
        assert(Variant2.c4_lr3.fold[Record3](Case4(List(Record3(null, null, null))))(null, (_, x) => x))(
          equalTo(Record3(null, null, null))
        ) &&
        assert(
          Variant2.c3_v1_v2_c4_lr3.fold[Record3](Case3(Case4(List(Record3(null, null, null)))))(null, (_, x) => x)
        )(equalTo(Record3(null, null, null)))
      },
      test("modifies collection values") {
        assert(Collections.as.modify(Array("1", "2", "3"), _ + "x").toList)(equalTo(List("1x", "2x", "3x"))) &&
        assert(Collections.mkc.modify(Map('a' -> "1", 'b' -> "2", 'c' -> "3"), _.toUpper))(
          equalTo(Map('A' -> "1", 'B' -> "2", 'C' -> "3"))
        ) &&
        assert(Collections.mvs.modify(Map('a' -> "1", 'b' -> "2", 'c' -> "3"), _ + "x"))(
          equalTo(Map('a' -> "1x", 'b' -> "2x", 'c' -> "3x"))
        ) &&
        assert(Collections.lc1.modify(List(Case1(0.1)), _.copy(d = 0.2)))(equalTo(List(Case1(0.2)))) &&
        assert(Collections.lc1_d.modify(List(Case1(0.1)), _ + 0.4))(equalTo(List(Case1(0.5)))) &&
        assert(Collections.lc4.modify(List(Case4(List(Record3(null, null, null)))), _.copy(r1 = Record1(true, 0.1f))))(
          equalTo(List(Case4(List(Record3(Record1(true, 0.1f), null, null)))))
        ) &&
        assert(Collections.lr1.modify(List(Record1(true, 0.1f)), x => !x))(equalTo(List(Record1(false, 0.1f)))) &&
        assert(Record2.vi.modify(Record2(2L, Vector(1, 2, 3), null), _ + 1))(
          equalTo(Record2(2L, Vector(2, 3, 4), null))
        ) &&
        assert(Variant2.c4_lr3.modify(Case4(List(Record3(null, null, null))), _ => null))(equalTo(Case4(List(null)))) &&
        assert(Variant2.c3_v1_v2_c4_lr3.modify(Case3(Case4(List(Record3(null, null, null)))), _ => null))(
          equalTo(Case3(Case4(List(null))))
        )
      }
    )
  )

  case class Record1(b: Boolean, f: Float)

  object Record1 {
    val reflect: Reflect.Record.Bound[Record1] = Reflect.Record(
      fields = List(
        Term("b", Reflect.boolean, Doc.Empty, Nil),
        Term("f", Reflect.float, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record1"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Record1] {
          def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1, floats = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): Record1 =
            Record1(in.getBoolean(baseOffset, 0), in.getFloat(baseOffset, 0))
        },
        deconstructor = new Deconstructor[Record1] {
          def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1, floats = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Record1): Unit = {
            out.setBoolean(baseOffset, 0, in.b)
            out.setFloat(baseOffset, 0, in.f)
          }
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val b: Lens.Bound[Record1, Boolean] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Record1, Boolean]])
    val f: Lens.Bound[Record1, Float]   = Lens(reflect, reflect.fields(1).asInstanceOf[Term.Bound[Record1, Float]])
  }

  case class Record2(l: Long, vi: Vector[Int], r1: Record1)

  object Record2 {
    val reflect: Reflect.Record.Bound[Record2] = Reflect.Record(
      fields = List(
        Term("l", Reflect.long, Doc.Empty, Nil),
        Term("vi", Reflect.vector(Reflect.int), Doc.Empty, Nil),
        Term("r1", Record1.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record2"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Record2] {
          def usedRegisters: RegisterOffset = RegisterOffset(longs = 1, objects = 2)

          def construct(in: Registers, baseOffset: RegisterOffset): Record2 =
            Record2(
              in.getLong(baseOffset, 0),
              in.getObject(baseOffset, 0).asInstanceOf[Vector[Int]],
              in.getObject(baseOffset, 1).asInstanceOf[Record1]
            )
        },
        deconstructor = new Deconstructor[Record2] {
          def usedRegisters: RegisterOffset = RegisterOffset(longs = 1, objects = 2)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Record2): Unit = {
            out.setLong(baseOffset, 0, in.l)
            out.setObject(baseOffset, 0, in.vi)
            out.setObject(baseOffset, 1, in.r1)
          }
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val l: Lens.Bound[Record2, Long] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Record2, Long]])
    val vi: Traversal.Bound[Record2, Int] =
      Lens(reflect, reflect.fields(1).asInstanceOf[Term.Bound[Record2, Vector[Int]]]).vector
    val r1: Lens.Bound[Record2, Record1] =
      Lens(reflect, reflect.fields(2).asInstanceOf[Term.Bound[Record2, Record1]])
    val r1_b: Lens.Bound[Record2, Boolean] = r1(Record1.b)
    val r1_f: Lens.Bound[Record2, Float]   = r1(Record1.f)
  }

  case class Record3(r1: Record1, r2: Record2, v1: Variant1)

  object Record3 {
    val reflect: Reflect.Record.Bound[Record3] = Reflect.Record(
      fields = List(
        Term("r1", Record1.reflect, Doc.Empty, Nil),
        Term("r2", Record2.reflect, Doc.Empty, Nil),
        Term("v1", Reflect.Deferred(() => Variant1.reflect), Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record3"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Record3] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 3)

          def construct(in: Registers, baseOffset: RegisterOffset): Record3 =
            Record3(
              in.getObject(baseOffset, 0).asInstanceOf[Record1],
              in.getObject(baseOffset, 1).asInstanceOf[Record2],
              in.getObject(baseOffset, 2).asInstanceOf[Variant1]
            )
        },
        deconstructor = new Deconstructor[Record3] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 3)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Record3): Unit = {
            out.setObject(baseOffset, 0, in.r1)
            out.setObject(baseOffset, 1, in.r2)
            out.setObject(baseOffset, 2, in.v1)
          }
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val r1: Lens.Bound[Record3, Record1] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Record3, Record1]])
    val r2: Lens.Bound[Record3, Record2] =
      Lens(reflect, reflect.fields(1).asInstanceOf[Term.Bound[Record3, Record2]])
    lazy val v1: Lens.Bound[Record3, Variant1] =
      Lens(reflect, reflect.fields(2).asInstanceOf[Term.Bound[Record3, Variant1]])
    val r2_r1_b_left: Lens.Bound[Record3, Boolean]  = r2(Record2.r1)(Record1.b)
    val r2_r1_b_right: Lens.Bound[Record3, Boolean] = r2(Record2.r1(Record1.b))
  }

  sealed trait Variant1

  object Variant1 {
    lazy val reflect: Reflect.Variant.Bound[Variant1] = Reflect.Variant(
      cases = List(
        Term("case1", Case1.reflect, Doc.Empty, Nil),
        Term("case2", Case2.reflect, Doc.Empty, Nil),
        Term("variant2", Variant2.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Variant"),
      variantBinding = Binding.Variant(
        discriminator = new Discriminator[Variant1] {
          override def discriminate(a: Variant1): Int = a match {
            case _: Case1    => 0
            case _: Case2    => 1
            case _: Variant2 => 2
          }
        },
        matchers = Matchers(
          new Matcher[Case1] {
            override def unsafeDowncast(a: Any): Case1 = a.asInstanceOf[Case1]
          },
          new Matcher[Case2] {
            override def unsafeDowncast(a: Any): Case2 = a.asInstanceOf[Case2]
          },
          new Matcher[Variant2] {
            override def unsafeDowncast(a: Any): Variant2 = a.asInstanceOf[Variant2]
          }
        )
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    lazy val c1: Prism.Bound[Variant1, Case1] =
      Prism(reflect, reflect.cases(0).asInstanceOf[Term.Bound[Variant1, Case1]])
    lazy val c2: Prism.Bound[Variant1, Case2] =
      Prism(reflect, reflect.cases(1).asInstanceOf[Term.Bound[Variant1, Case2]])
    lazy val v2: Prism.Bound[Variant1, Variant2] =
      Prism(reflect, reflect.cases(2).asInstanceOf[Term.Bound[Variant1, Variant2]])
    lazy val v2_c3: Prism.Bound[Variant1, Case3]                    = v2(Variant2.c3)
    lazy val v2_c4: Prism.Bound[Variant1, Case4]                    = v2(Variant2.c4)
    lazy val v2_c4_lr3: Traversal.Bound[Variant1, Record3]          = v2(Variant2.c4(Case4.lr3))
    lazy val c1_d: Optional.Bound[Variant1, Double]                 = c1(Case1.d)
    lazy val c2_r3: Optional.Bound[Variant1, Record3]               = c2(Case2.r3)
    lazy val c2_r3_r1: Optional.Bound[Variant1, Record1]            = c2_r3(Record3.r1)
    lazy val c2_r3_r2_r1_b_left: Optional.Bound[Variant1, Boolean]  = c2_r3(Record3.r2_r1_b_left)
    lazy val c2_r3_r2_r1_b_right: Optional.Bound[Variant1, Boolean] = c2_r3(Record3.r2_r1_b_right)
  }

  case class Case1(d: Double) extends Variant1

  object Case1 {
    val reflect: Reflect.Record.Bound[Case1] = Reflect.Record(
      fields = List(
        Term("d", Reflect.double, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Case1"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Case1] {
          def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): Case1 =
            Case1(in.getDouble(baseOffset, 0))
        },
        deconstructor = new Deconstructor[Case1] {
          def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case1): Unit =
            out.setDouble(baseOffset, 0, in.d)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val d: Lens.Bound[Case1, Double] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Case1, Double]])
  }

  case class Case2(r3: Record3) extends Variant1

  object Case2 {
    val reflect: Reflect.Record.Bound[Case2] = Reflect.Record(
      fields = List(
        Term("r3", Record3.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Case2"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Case2] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): Case2 =
            Case2(in.getObject(baseOffset, 0).asInstanceOf[Record3])
        },
        deconstructor = new Deconstructor[Case2] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case2): Unit =
            out.setObject(baseOffset, 0, in.r3)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val r3: Lens.Bound[Case2, Record3] = Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Case2, Record3]])
  }

  sealed trait Variant2 extends Variant1

  object Variant2 {
    val reflect: Reflect.Variant.Bound[Variant2] = Reflect.Variant(
      cases = List(
        Term("case3", Case3.reflect, Doc.Empty, Nil),
        Term("case4", Case4.reflect, Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Variant"),
      variantBinding = Binding.Variant(
        discriminator = new Discriminator[Variant2] {
          override def discriminate(a: Variant2): Int = a match {
            case _: Case3 => 0
            case _: Case4 => 1
          }
        },
        matchers = Matchers(
          new Matcher[Case3] {
            override def unsafeDowncast(a: Any): Case3 = a.asInstanceOf[Case3]
          },
          new Matcher[Case4] {
            override def unsafeDowncast(a: Any): Case4 = a.asInstanceOf[Case4]
          }
        )
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val c3: Prism.Bound[Variant2, Case3] =
      Prism(reflect, reflect.cases(0).asInstanceOf[Term.Bound[Variant2, Case3]])
    val c4: Prism.Bound[Variant2, Case4] =
      Prism(reflect, reflect.cases(1).asInstanceOf[Term.Bound[Variant2, Case4]])
    lazy val c3_v1: Optional.Bound[Variant2, Variant1]           = c3(Case3.v1)
    lazy val c3_v1_c1_left: Optional.Bound[Variant2, Case1]      = c3(Case3.v1)(Variant1.c1)
    lazy val c3_v1_c1_right: Optional.Bound[Variant2, Case1]     = c3(Case3.v1_c1)
    lazy val c3_v1_c1_d_left: Optional.Bound[Variant2, Double]   = c3(Case3.v1)(Variant1.c1_d)
    lazy val c3_v1_c1_d_right: Optional.Bound[Variant2, Double]  = c3_v1(Variant1.c1_d)
    lazy val c3_v1_v2: Optional.Bound[Variant2, Variant2]        = c3(Case3.v1)(Variant1.v2)
    val c4_lr3: Traversal.Bound[Variant2, Record3]               = c4(Case4.lr3)
    lazy val c3_v1_v2_c4_lr3: Traversal.Bound[Variant2, Record3] = c3_v1_v2(c4_lr3)
  }

  case class Case3(v1: Variant1) extends Variant2

  object Case3 {
    lazy val reflect: Reflect.Record.Bound[Case3] = Reflect.Record(
      fields = List(
        Term("v1", Reflect.Deferred(() => Variant1.reflect), Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Case3"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Case3] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): Case3 =
            Case3(in.getObject(baseOffset, 0).asInstanceOf[Variant1])
        },
        deconstructor = new Deconstructor[Case3] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case3): Unit =
            out.setObject(baseOffset, 0, in.v1)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    lazy val v1: Lens.Bound[Case3, Variant1] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Case3, Variant1]])
    lazy val v1_c1: Optional.Bound[Case3, Case1]    = v1(Variant1.c1)
    lazy val v1_c1_d: Optional.Bound[Case3, Double] = v1(Variant1.c1_d)
  }

  case class Case4(lr3: List[Record3]) extends Variant2

  object Case4 {
    val reflect: Reflect.Record.Bound[Case4] = Reflect.Record(
      fields = List(
        Term("lr3", Reflect.list(Record3.reflect), Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Case4"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Case4] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def construct(in: Registers, baseOffset: RegisterOffset): Case4 =
            Case4(in.getObject(baseOffset, 0).asInstanceOf[List[Record3]])
        },
        deconstructor = new Deconstructor[Case4] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case4): Unit =
            out.setObject(baseOffset, 0, in.lr3)
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    val lr3: Traversal.Bound[Case4, Record3] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Case4, List[Record3]]]).list
  }

  case class Record5(si: Set[Int], as: Array[String])

  object Record5 {
    val reflect: Reflect.Record.Bound[Record5] = Reflect.Record(
      fields = List(
        Term("si", Reflect.set(Reflect.int), Doc.Empty, Nil),
        Term("as", Reflect.array(Reflect.string), Doc.Empty, Nil)
      ),
      typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record5"),
      recordBinding = Binding.Record(
        constructor = new Constructor[Record5] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 2)

          def construct(in: Registers, baseOffset: RegisterOffset): Record5 = Record5(
            in.getObject(baseOffset, 0).asInstanceOf[Set[Int]],
            in.getObject(baseOffset, 1).asInstanceOf[Array[String]]
          )
        },
        deconstructor = new Deconstructor[Record5] {
          def usedRegisters: RegisterOffset = RegisterOffset(objects = 2)

          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Record5): Unit = {
            out.setObject(baseOffset, 0, in.si)
            out.setObject(baseOffset, 1, in.as)
          }
        }
      ),
      doc = Doc.Empty,
      modifiers = Nil
    )
    /* FIXME: Is a bug in the compiler?
    val si: Traversal.Bound[Record5, Int] =
      Lens(reflect, reflect.fields(0).asInstanceOf[Term.Bound[Record5, Set[Int]]]).set
     */
    val as: Traversal.Bound[Record5, String] =
      Lens(reflect, reflect.fields(1).asInstanceOf[Term.Bound[Record5, Array[String]]]).array
  }

  object Collections {
    val lb: Traversal.Bound[List[Byte], Byte]            = Reflect.list(Reflect.byte[Binding]).values
    val vs: Traversal.Bound[Vector[Short], Short]        = Traversal.vectorValues(Reflect.short)
    val as: Traversal.Bound[Array[String], String]       = Traversal.arrayValues(Reflect.string)
    val sf: Traversal.Bound[Set[Float], Float]           = Traversal.setValues(Reflect.float)
    val mkc: Traversal.Bound[Map[Char, String], Char]    = Traversal.mapKeys(Reflect.map(Reflect.char, Reflect.string))
    val mvs: Traversal.Bound[Map[Char, String], String]  = Traversal.mapValues(Reflect.map(Reflect.char, Reflect.string))
    val lr1: Traversal.Bound[List[Record1], Boolean]     = Traversal.listValues(Record1.reflect).apply(Record1.b)
    lazy val lc1: Traversal.Bound[List[Variant1], Case1] = Traversal.listValues(Variant1.reflect).apply(Variant1.c1)
    lazy val lc1_d: Traversal.Bound[List[Variant1], Double] =
      Traversal.listValues(Variant1.reflect).apply(Variant1.c1_d)
    val lc4: Traversal.Bound[List[Case4], Record3] = Traversal.listValues(Case4.reflect).apply(Case4.lr3)
  }
}
