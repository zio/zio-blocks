package zio.blocks.schema.as.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.dynamics

/** Tests for As with Dynamic structural types. */
object DynamicAsSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)

  class DynamicRecord(val fields: Map[String, Any]) extends Dynamic {
    def selectDynamic(name: String): Any = fields(name)
  }

  object DynamicRecord {
    def apply(map: Map[String, Any]): DynamicRecord = new DynamicRecord(map)
  }

  type PersonLike = DynamicRecord { def name: String; def age: Int }

  def spec: Spec[TestEnvironment, Any] = suite("DynamicAsSpec")(
    test("As[Person, PersonLike] round-trip") {
      val as       = As.derived[Person, PersonLike]
      val original = Person("Alice", 30)

      val roundTrip = for {
        dynamic <- as.into(original)
        back    <- as.from(dynamic)
      } yield back

      assert(roundTrip)(isRight(equalTo(original)))
    },
    test("reverse swaps conversion direction") {
      val as       = As.derived[Person, PersonLike]
      val reversed = as.reverse
      val dynamic  = DynamicRecord(Map("name" -> "Bob", "age" -> 25)).asInstanceOf[PersonLike]

      val result = reversed.into(dynamic)

      assert(result)(isRight(equalTo(Person("Bob", 25))))
    }
  )
}
