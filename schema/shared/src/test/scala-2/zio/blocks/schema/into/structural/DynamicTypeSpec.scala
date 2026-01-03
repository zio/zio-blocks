package zio.blocks.schema.into.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.dynamics

/** Tests for Into with Dynamic structural types. */
object DynamicTypeSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)

  class DynamicRecord(val fields: Map[String, Any]) extends Dynamic {
    def selectDynamic(name: String): Any = fields(name)
  }

  object DynamicRecord {
    def apply(map: Map[String, Any]): DynamicRecord = new DynamicRecord(map)
  }

  type PersonLike = DynamicRecord { def name: String; def age: Int }

  def spec: Spec[TestEnvironment, Any] = suite("DynamicTypeSpec")(
    test("case class to Dynamic structural type") {
      val source = Person("Alice", 30)
      val into   = Into.derived[Person, PersonLike]
      val result = into.into(source)

      result match {
        case Right(r) =>
          assert(r.selectDynamic("name"))(equalTo("Alice")) &&
          assert(r.selectDynamic("age"))(equalTo(30))
        case Left(err) =>
          assert(err.toString)(equalTo("should not fail"))
      }
    },
    test("Dynamic structural type to case class") {
      val source: PersonLike = DynamicRecord(Map("name" -> "Bob", "age" -> 25)).asInstanceOf[PersonLike]
      val into               = Into.derived[PersonLike, Person]
      val result             = into.into(source)

      assert(result)(isRight(equalTo(Person("Bob", 25))))
    },
    test("round-trip case class to Dynamic and back") {
      val original    = Person("Carol", 35)
      val toDynamic   = Into.derived[Person, PersonLike]
      val fromDynamic = Into.derived[PersonLike, Person]

      val result = toDynamic.into(original).flatMap(d => fromDynamic.into(d))

      assert(result)(isRight(equalTo(original)))
    }
  )
}
