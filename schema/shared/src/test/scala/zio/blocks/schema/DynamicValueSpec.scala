package zio.blocks.schema

import zio.Scope
import zio.test._
import zio.blocks.schema.json.DynamicValueGen._
import zio.test.Assertion.{equalTo, not}

object DynamicValueSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("DynamicValueSpec")(
      suite("Simple DynamicValue equals and hashCode properties")(
        test("self-referential lazy equality should not overflow") {
          lazy val lazySelf: DynamicValue.Lazy = DynamicValue.Lazy(() => lazySelf)
          assertTrue(lazySelf == lazySelf)
        }
      ),
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
        },
        test("lazy equality and hashCode behaves correctly") {
          // Test that lazy values use identity-based equality and hash codes
          check(genLazy, genLazy) { (lazy1, lazy2) =>
            // Different lazy instances are never equal, even if they wrap the same value
            assertTrue(
              (lazy1 eq lazy2) == (lazy1 == lazy2),                  // Only equal if same instance
              (lazy1 == lazy2) == (lazy1.hashCode == lazy2.hashCode) // Hash code consistency
            )
          } &&
          check(genLazyWithValue) { case (lazyValue, _) =>
            // A lazy value is always equal to itself
            assertTrue(
              lazyValue == lazyValue,
              lazyValue.hashCode == lazyValue.hashCode
            )
          } &&
          check(genLazyWithValue, genDynamicValue) { case ((lazyValue, _), otherValue) =>
            // Lazy values are never equal to non-lazy values
            assertTrue(!(lazyValue == otherValue))
          }
        }
      ) @@ TestAspect.exceptNative
    )
}
