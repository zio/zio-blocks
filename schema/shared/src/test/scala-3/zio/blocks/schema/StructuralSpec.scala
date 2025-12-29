package zio.blocks.schema

import zio.test._

object StructuralSpec extends ZIOSpecDefault {
  private case class Person(name: String, age: Int)
  private case class User(age: Int, name: String)

  def spec = suite("StructuralSpec")(
    test("structural type names are normalized and deterministic") {
      val s1 = Schema.derived[Person].structural
      val s2 = Schema.derived[User].structural

      val tn1 = s1.reflect.typeName
      val tn2 = s2.reflect.typeName

      assertTrue(tn1 == tn2) &&
      assertTrue(tn1.name.indexOf("age") < tn1.name.indexOf("name"))
    }
  ,
    test("construct Selectable from nominal value via DynamicValue") {
      val person = Person("Alice", 30)
      val schema = Schema.derived[Person]
      val dv = schema.toDynamicValue(person)
      val sel = StructuralRuntime.fromDynamicValue(dv)
      // access fields via selectDynamic
      val name = sel.selectDynamic("name").asInstanceOf[String]
      val age = sel.selectDynamic("age").asInstanceOf[Int]
      assertTrue(name == "Alice") && assertTrue(age == 30)
    }
  )
}
