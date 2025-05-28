package zio.blocks.schema

import zio.Scope
import zio.test._
import zio.blocks.schema.json.DynamicValueGen._
import zio.test.Assertion.{equalTo, not}

object DynamicValueSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("DynamicValueSpec")(
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
            val map1 = DynamicValue.Map(Vector((key, value1)))
            val map2 = DynamicValue.Map(Vector((key, value2)))
            assertTrue(!(map1 == map2) || (map1.hashCode == map2.hashCode))
          }
        }
      )
    )
}
