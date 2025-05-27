package zio.blocks.schema
import zio.Scope
import zio.blocks.schema.json.DynamicValueGen.genDynamicValue
import zio.test._

object DynamicValueSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("DynamicValueSpec")(
    suite("DynamicValue equals and hashCode properties")(
      test("reflexivity") {
        check(genDynamicValue) { value =>
          assertTrue(value == value)
        }
      },
//      test("symmetry") {
//        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
//          assertTrue(
//            (value1 == value2) == (value2 == value1)
//          )
//        }
//      },
//      test("transitivity") {
//        check(genDynamicValue, genDynamicValue, genDynamicValue) { (value1, value2, value3) =>
//          assertTrue(
//            !(value1 == value2 && value2 == value3) || (value1 == value3)
//          )
//        }
//      },
//      test("consistency of hashCode for equal values") {
//        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
//          assertTrue(!(value1 == value2) || (value1.hashCode == value2.hashCode))
//        }
//      },
//      test("inequality for different types or structures") {
//        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
//          assertTrue(value1 != value2 || value1 == value2)
//        }
//      },
//      test("nested structure equality and hashCode consistency") {
//        val nestedGen = for {
//          innerValue <- genRecord
//          outerValue <- genRecord
//        } yield DynamicValue.Record(Vector("inner" -> innerValue, "outer" -> outerValue))
//
//        check(nestedGen, nestedGen) { (nested1, nested2) =>
//          assertTrue(
//            (nested1 == nested2) == (nested1.hashCode == nested2.hashCode)
//          )
//        }
//      },
//      test("lazy equality and hashCode behaves correctly") {
//
//        def genLazyCompare: Gen[Any, (DynamicValue.Lazy, DynamicValue)] =
//          for {
//            (lazyValue, value) <- genLazyWithValue
//            valueOrLazy <- Gen.oneOf(
//              Gen.const(value),
//              Gen.int(0, 2).map(mkLazy(lazyValue, _)), // nested lazy value of depth up to 5
//            )
//          } yield (lazyValue, valueOrLazy)
//
//        check(genLazyCompare) { case (lzy, lzyOrDyn) =>
//          assertTrue(
//            lzy == lzyOrDyn,
//            lzy.hashCode == lzyOrDyn.hashCode,
//          )
//        }
//      }
    )
  )
}
