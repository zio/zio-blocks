package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object LazySpec extends ZIOSpecDefault {

  def spec = suite("LazySpec")(
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
    )
  )
}
