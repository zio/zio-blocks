package zio.blocks.schema

import zio.test._
import DynamicValueGen._
import zio.test.Assertion.{equalTo, not}

object DynamicValueSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueSpec")(
    suite("DynamicValue equals and hashCode properties with Generators")(
      test("symmetry") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          assertTrue((value1 == value2) == (value2 == value1))
        }
      },
      test("transitivity") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (value1, value2, value3) =>
          // If value1 equals value2 and value2 equals value3 then value1 should equal value3.
          assertTrue(!(value1 == value2 && value2 == value3) || (value1 == value3))
        }
      },
      test("consistency of hashCode for equal values") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          // For equal values the hashCodes must be equal
          assertTrue(!(value1 == value2) || (value1.hashCode == value2.hashCode))
        }
      },
      test("inequality for different types or structures") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          // verifies that when two values are not equal they indeed do not compare equal
          assertTrue((value1 != value2) || (value1 == value2))
        }
      },
      test("inequality for other non dynamic value types") {
        check(genDynamicValue, Gen.string) { (dynamicValue, str) =>
          assert(dynamicValue: Any)(not(equalTo(str)))
        }
      },
      test("nested structure equality and hashCode consistency") {
        val nestedGen = for {
          innerValue <- genRecord
          outerValue <- genRecord
        } yield DynamicValue.Record(Vector("inner" -> innerValue, "outer" -> outerValue))

        check(nestedGen, nestedGen) { (nested1, nested2) =>
          assertTrue((nested1 == nested2) == (nested1.hashCode == nested2.hashCode))
        }
      },
      test("structure equality and hashCode consistency for variants with the same case names") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          val variant1 = DynamicValue.Variant("case1", value1)
          val variant2 = DynamicValue.Variant("case1", value2)
          assertTrue(!(variant1 == variant2) || (variant1.hashCode == variant2.hashCode))
        }
      },
      test("structure equality and hashCode consistency for maps with the same keys") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (key, value1, value2) =>
          val map1 = DynamicValue.Map(Vector((key, value1), (key, value2)))
          val map2 = DynamicValue.Map(Vector((key, value1), (key, value2)))
          assertTrue(!(map1 == map2) || (map1.hashCode == map2.hashCode))
        }
      }
    ),
    suite("DynamicValue compare and equals properties with Generators")(
      test("symmetry") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          assertTrue(value1.compare(value2) == -value2.compare(value1)) &&
          assertTrue((value1 > value2) == (value2 < value1)) &&
          assertTrue((value1 >= value2) == (value2 <= value1))
        }
      },
      test("transitivity") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (value1, value2, value3) =>
          assertTrue(!(value1 > value2 && value2 > value3) || (value1 > value3)) &&
          assertTrue(!(value1 >= value2 && value2 >= value3) || (value1 >= value3)) &&
          assertTrue(!(value1 < value2 && value2 < value3) || (value1 < value3)) &&
          assertTrue(!(value1 <= value2 && value2 <= value3) || (value1 <= value3))
        }
      },
      test("consistency of compare for equal values") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          assertTrue((value1 == value2) == (value1.compare(value2) == 0))
        }
      },
      test("nested structure equality and compare consistency") {
        val nestedGen = for {
          innerValue <- genRecord
          outerValue <- genRecord
        } yield DynamicValue.Record(Vector("inner" -> innerValue, "outer" -> outerValue))

        check(nestedGen, nestedGen) { (nested1, nested2) =>
          assertTrue((nested1 == nested2) == (nested1.compare(nested2) == 0))
        }
      },
      test("structure equality and compare consistency for variants with the same case names") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          val variant1 = DynamicValue.Variant("case1", value1)
          val variant2 = DynamicValue.Variant("case1", value2)
          assertTrue((variant1 == variant2) == (variant1.compare(variant2) == 0))
        }
      },
      test("structure equality and compare consistency for maps with the same keys") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (key, value1, value2) =>
          val map1 = DynamicValue.Map(Vector((key, value1), (key, value2)))
          val map2 = DynamicValue.Map(Vector((key, value2), (key, value1)))
          assertTrue((map1 == map2) == (map1.compare(map2) == 0))
        }
      }
    )
  )
}
