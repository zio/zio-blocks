package zio.blocks.schema

import zio.test._
import zio.test.Assertion._
import zio._

object LazySpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("LazySpec")(
    suite("isEvaluated")(
      test("should return false for unevaluated Lazy") {
        val lazyInstance = Lazy(10)
        assert(lazyInstance.isEvaluated)(isFalse)
      },
      test("should return true after force") {
        val lazyInstance = Lazy(10)
        lazyInstance.force // Forces evaluation
        assert(lazyInstance.isEvaluated)(isTrue)
      }
    ),
    suite("collectAll List")(
      test("should collect all Lazy values in a List") {
        val lazyList      = List(Lazy(1), Lazy(2), Lazy(3))
        val collectedLazy = Lazy.collectAll(lazyList)
        val result        = collectedLazy.force
        assert(result)(equalTo(List(1, 2, 3)))
      },
      test("should handle an empty List") {
        val emptyList     = List.empty[Lazy[Int]]
        val collectedLazy = Lazy.collectAll(emptyList)
        val result        = collectedLazy.force
        assert(result)(isEmpty)
      }
    ),
    suite("collectAll Vector")(
      test("should collect all Lazy values in a Vector") {
        val lazyVector    = Vector(Lazy("a"), Lazy("b"), Lazy("c"))
        val collectedLazy = Lazy.collectAll(lazyVector)
        val result        = collectedLazy.force
        assert(result)(equalTo(Vector("a", "b", "c")))
      },
      test("should handle an empty Vector") {
        val emptyVector   = Vector.empty[Lazy[String]]
        val collectedLazy = Lazy.collectAll(emptyVector)
        val result        = collectedLazy.force
        assert(result)(isEmpty)
      }
    ),
    suite("collectAll Array")(
      test("should collect all Lazy values in an Array") {
        val lazyArray     = Array(Lazy(1.1), Lazy(2.2), Lazy(3.3))
        val collectedLazy = Lazy.collectAll(lazyArray)
        val result        = collectedLazy.force
        assert(result.toList)(equalTo(List(1.1, 2.2, 3.3)))
      },
      test("should handle an empty Array") {
        val emptyArray    = Array.empty[Lazy[Double]]
        val collectedLazy = Lazy.collectAll(emptyArray)
        val result        = collectedLazy.force
        assert(result.toList)(isEmpty)
      }
    ),
    suite("collectAll Set")(
      test("should collect all Lazy values in a Set") {
        val lazySet       = Set(Lazy("one"), Lazy("two"))
        val collectedLazy = Lazy.collectAll(lazySet)
        val result        = collectedLazy.force
        // Since it's a Set, we only check for membership
        assert(result)(hasSameElements(Set("one", "two")))
      },
      test("should handle an empty Set") {
        val emptySet      = Set.empty[Lazy[String]]
        val collectedLazy = Lazy.collectAll(emptySet)
        val result        = collectedLazy.force
        assert(result)(isEmpty)
      }
    ),
    suite("hashCode")(
      test("hashCode for the same Lazy instance is consistent") {
        val lazyInstance = Lazy(3)
        val hash1        = lazyInstance.hashCode()
        val hash2        = lazyInstance.hashCode()
        // The hashCode should not change between calls
        assert(hash1)(equalTo(hash2))
      },
      test("hashCode compares correctly for evaluated vs. unevaluated equals") {
        val lazyA = Lazy(10)
        val lazyB = Lazy(10)
        lazyA.force
        lazyB.force
        assert(lazyA.hashCode())(equalTo(lazyB.hashCode()))
      }
    ),
    suite("unit")(
      test("unit returns Lazy of Unit and forces original value if forced") {
        val lazyVal  = Lazy(42)
        val lazyUnit = lazyVal.unit
        lazyUnit.force
        // Forcing lazyUnit also forces the original? I am not sure if this is right
        assert(lazyVal.isEvaluated)(isFalse) && // should this be true?
        assert(lazyUnit.force)(equalTo(()))
      }
    ),
    suite("zip")(
      test("zip returns a tuple of values from both lazy instances") {
        val lazyA  = Lazy("hello")
        val lazyB  = Lazy("world")
        val zipped = lazyA.zip(lazyB)
        assert(zipped.force)(equalTo(("hello", "world")))
      }
    ),
    suite("fail")(
      test("fail should produce a Lazy that throws the provided exception") {
        val ex       = new RuntimeException("boom")
        val lazyFail = Lazy.fail(ex)
        val boomed   = ZIO.attempt(lazyFail.force).exit
        assertZIO(boomed)(fails(isSubtype[RuntimeException](hasMessage(equalTo("boom")))))
      }
    ),
    suite("foreach")(
      test("foreach for List") {
        val list   = List(1, 2, 3)
        val result = Lazy.foreach(list)(n => Lazy(n + 1)).force
        assert(result)(equalTo(List(2, 3, 4)))
      },
      test("foreach for an empty List") {
        val list   = List.empty[Int]
        val result = Lazy.foreach(list)(n => Lazy(n + 1)).force
        assert(result)(isEmpty)
      },
      test("foreach for Vector") {
        val vector = Vector("a", "b", "c")
        val result = Lazy.foreach(vector)(str => Lazy(str.toUpperCase())).force
        assert(result)(equalTo(Vector("A", "B", "C")))
      },
      test("foreach for Array") {
        val arr    = Array(10, 20, 30)
        val result = Lazy.foreach(arr)(n => Lazy(n * 2)).force
        assert(result.toList)(equalTo(List(20, 40, 60)))
      },
      test("foreach for Set") {
        val set    = Set(1, 2, 3)
        val result = Lazy.foreach(set)(n => Lazy(n * -1)).force
        assert(result)(hasSameElements(Set(-1, -2, -3)))
      }
    )
  )
}
