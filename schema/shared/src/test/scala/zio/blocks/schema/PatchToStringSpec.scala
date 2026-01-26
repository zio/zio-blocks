package zio.blocks.schema

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.patch._

object PatchToStringSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("PatchToStringSpec")(
    test("Patch.set.toString") {
      val patch = Patch.set(Schema[Person].reflect.asRecord.get.lensByName[String]("name").get, "Bob")
      assert(patch.toString)(equalTo("""DynamicPatch(.name = "Bob")"""))
    },
    test("Patch.increment.toString") {
      val patch = Patch.increment(Schema[Person].reflect.asRecord.get.lensByName[Int]("age").get, 1)
      assert(patch.toString)(equalTo("DynamicPatch(.age += 1)"))
    },
    test("Patch.composition.toString") {
      val p1    = Patch.set(Schema[Person].reflect.asRecord.get.lensByName[String]("name").get, "Bob")
      val p2    = Patch.increment(Schema[Person].reflect.asRecord.get.lensByName[Int]("age").get, 1)
      val patch = p1 ++ p2
      assert(patch.toString)(equalTo("""DynamicPatch(.name = "Bob", .age += 1)"""))
    }
  )

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }
}
