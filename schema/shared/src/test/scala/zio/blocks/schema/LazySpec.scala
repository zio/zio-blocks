package zio.blocks.schema
import zio.ZIO
import zio.test.Assertion._
import zio.test._
import scala.collection.immutable.ArraySeq

object LazySpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("LazySpec")(
    test("equals") {
      assert(Lazy(42))(equalTo(Lazy(42))) &&
      assert(Lazy(42))(not(equalTo(Lazy(43)))) &&
      assert(Lazy(42): Any)(not(equalTo(42)))
    },
    test("hashCode") {
      assert(Lazy(42).hashCode)(equalTo(Lazy(42).hashCode))
    },
    test("toString") {
      assert(Lazy(42).toString)(equalTo("Lazy(<not evaluated>)")) &&
      assert({
        val lazyValue = Lazy(42)
        lazyValue.force
        lazyValue.toString
      })(equalTo("Lazy(42)"))
    },
    test("isEvaluated") {
      val lazyValue = Lazy(42)
      assert(lazyValue.isEvaluated)(isFalse)
      val _ = lazyValue.force
      assert(lazyValue.isEvaluated)(isTrue)
    },
    test("force (success result)") {
      var world = List.empty[Int]
      val lazyValue = Lazy({
        world = world :+ 42
        world
      })
      assert(world)(equalTo(List.empty[Int])) &&
      assert(lazyValue.force)(equalTo(List(42))) &&
      assert(world)(equalTo(List(42))) &&
      assert(lazyValue.force)(equalTo(List(42)))
    },
    test("force (error result)") {
      val lazyValue = Lazy[Int](sys.error("test"))
      ZIO.attempt(lazyValue.force).flip.map { e =>
        assertTrue(e.isInstanceOf[Throwable])
      } &&
      ZIO.attempt(lazyValue.force).flip.map { e =>
        assertTrue(e.isInstanceOf[Throwable])
      }
    },
    test("ensuring (success result)") {
      var world = List.empty[Int]
      val finalizer = Lazy({
        world = world :+ 43
        world
      })
      val lazyValue = Lazy({
        world = world :+ 42
        world
      }).ensuring(finalizer)
      assert(world)(equalTo(List.empty[Int])) &&
      assert(lazyValue.force)(equalTo(List(42))) &&
      assert(world)(equalTo(List(42, 43))) &&
      assert(finalizer.isEvaluated)(isTrue) &&
      assert(finalizer.force)(equalTo(List(42, 43)))
    },
    test("ensuring (error result)") {
      var world = List.empty[Int]
      val finalizer = Lazy({
        world = world :+ 43
        world
      })
      ZIO.attempt(Lazy[Int](sys.error("test")).ensuring(finalizer).force).flip.map { e =>
        assertTrue(e.isInstanceOf[Throwable]) &&
        assert(world)(equalTo(List(43))) &&
        assert(finalizer.isEvaluated)(isTrue) &&
        assert(finalizer.force)(equalTo(List(43)))
      }
    },
    test("catchAll (success result)") {
      assert(Lazy(42).catchAll(_ => Lazy(43)))(equalTo(Lazy(42)))
    },
    test("catchAll (error result)") {
      assert(Lazy(sys.error("test")).catchAll(_ => Lazy(43)))(equalTo(Lazy(43)))
    },
    test("flatMap (success result)") {
      assert(Lazy(42).flatMap(i => Lazy(i + 1)))(equalTo(Lazy(43)))
    },
    test("flatMap (error result)") {
      ZIO.attempt(Lazy(42).flatMap(_ => Lazy(sys.error("test")).as("42")).force).flip.map { e =>
        assertTrue(e.isInstanceOf[Throwable])
      }
    },
    test("fail") {
      ZIO.attempt(Lazy.fail(new RuntimeException()).force).flip.map { e =>
        assertTrue(e.isInstanceOf[Throwable])
      }
    },
    test("flatten") {
      assert(Lazy(Lazy(42)).flatten)(equalTo(Lazy(42)))
    },
    test("map") {
      assert(Lazy(42).map(_.toString))(equalTo(Lazy("42")))
    },
    test("as") {
      assert(Lazy(42).as("42"))(equalTo(Lazy("42"))) &&
      assert(Lazy(42).as(()))(equalTo(Lazy(())))
    },
    test("zip") {
      assert(Lazy(42).zip(Lazy(43)))(equalTo(Lazy((42, 43))))
    },
    test("unit") {
      assert(Lazy(42).unit)(equalTo(Lazy(())))
    },
    test("collectAll") {
      assert(Lazy.collectAll(List(Lazy(42), Lazy(43))))(equalTo(Lazy(List(42, 43)))) &&
      assert(Lazy.collectAll(Vector(Lazy(42), Lazy(43))))(equalTo(Lazy(Vector(42, 43)))) &&
      assert(Lazy.collectAll(ArraySeq(Lazy(42), Lazy(43))))(equalTo(Lazy(ArraySeq(42, 43)))) &&
      assert(Lazy.collectAll(Set(Lazy(42), Lazy(43))))(equalTo(Lazy(Set(42, 43))))
    },
    test("foreach") {
      assert(Lazy.foreach(List(42, 43))(x => Lazy(x.toString)))(equalTo(Lazy(List("42", "43")))) &&
      assert(Lazy.foreach(Vector(42, 43))(x => Lazy(x.toString)))(equalTo(Lazy(Vector("42", "43")))) &&
      assert(Lazy.foreach(ArraySeq(42, 43))(x => Lazy(x.toString)))(equalTo(Lazy(ArraySeq("42", "43")))) &&
      assert(Lazy.foreach(Set(42, 43))(x => Lazy(x.toString)))(equalTo(Lazy(Set("42", "43"))))
    }
  )
}
