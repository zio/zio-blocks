package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object TypeNameToStringSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("TypeNameToStringSpec")(
    test("toString for simple type") {
      val typeName = TypeName.derived[Int]
      assert(typeName.toString)(equalTo("Int"))
    },
    test("toString for parametrized type") {
      val typeName = TypeName.derived[Option[String]]
      assert(typeName.toString)(equalTo("Option[String]"))
    },
    test("toString for nested parametrized type") {
      val typeName = TypeName.derived[Map[String, List[Int]]]
      assert(typeName.toString)(equalTo("Map[String, List[Int]]"))
    }
  )
}
