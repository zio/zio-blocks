package golem.data

import zio.blocks.schema.Schema
import zio.test._
import zio.test.Assertion._

object DataInteropSpec extends ZIOSpecDefault {
  final case class Person(name: String, age: Int, tags: List[String], nickname: Option[String])
  implicit val personSchema: Schema[Person] = Schema.derived

  final case class Bag(values: Map[String, Int], labels: Set[String])
  implicit val bagSchema: Schema[Bag] = Schema.derived

  override def spec: Spec[TestEnvironment, Any] =
    suite("DataInteropSpec")(
      test("round trips records with options and lists") {
        val value   = Person("Ada", 37, List("math", "code"), Some("ada"))
        val encoded = DataInterop.toData(value)
        assert(DataInterop.fromData[Person](encoded))(isRight(equalTo(value)))
      },
      test("round trips records with None options") {
        val value   = Person("Bob", 12, Nil, None)
        val encoded = DataInterop.toData(value)
        assert(DataInterop.fromData[Person](encoded))(isRight(equalTo(value)))
      },
      test("round trips maps and sets") {
        val value   = Bag(Map("a" -> 1, "b" -> 2), Set("x", "y"))
        val encoded = DataInterop.toData(value)
        assert(DataInterop.fromData[Bag](encoded))(isRight(equalTo(value)))
      }
    )
}
