package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object ReflectToStringSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ReflectToStringSpec")(
    test("Primitive.toString") {
      val reflect = Schema[Int].reflect
      assert(reflect.toString)(equalTo("Primitive[Int]"))
    },
    test("Record.toString") {
      val reflect  = Schema[Person].reflect
      val expected =
        """Record[Person](
          |  name: Primitive[String],
          |  age: Primitive[Int]
          |)""".stripMargin
      assert(reflect.toString)(equalTo(expected))
    },
    test("Variant.toString") {
      val reflect  = Schema[Option[Int]].reflect
      val expected =
        """Variant[Option[Int]](
          |  None: Record[None](),
          |  Some: Record[Some[Int]](
          |    value: Primitive[Int]
          |  )
          |)""".stripMargin
      assert(reflect.toString)(equalTo(expected))
    },
    test("Sequence.toString") {
      val reflect = Schema[List[Int]].reflect
      assert(reflect.toString)(equalTo("Sequence[Int, List[Int]]"))
    },
    test("Map.toString") {
      val reflect = Schema[Map[String, Int]].reflect
      assert(reflect.toString)(equalTo("Map[String, Int, Map[String, Int]]"))
    }
  )

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }
}
