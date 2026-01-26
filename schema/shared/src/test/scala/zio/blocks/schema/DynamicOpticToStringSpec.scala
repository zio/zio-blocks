package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object DynamicOpticToStringSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicOpticToStringSpec")(
    test("toString for Field") {
      val optic = DynamicOptic.root.field("name")
      assert(optic.toString)(equalTo(".name"))
    },
    test("toString for nested Field") {
      val optic = DynamicOptic.root.field("address").field("street")
      assert(optic.toString)(equalTo(".address.street"))
    },
    test("toString for Case") {
      val optic = DynamicOptic.root.field("name").caseOf("Some")
      assert(optic.toString)(equalTo(".name<Some>"))
    },
    test("toString for AtIndex") {
      val optic = DynamicOptic.root.field("users").at(0)
      assert(optic.toString)(equalTo(".users[0]"))
    },
    test("toString for AtIndices") {
      val optic = DynamicOptic.root.field("users").atIndices(0, 2, 5)
      assert(optic.toString)(equalTo(".users[0,2,5]"))
    },
    test("toString for Elements") {
      val optic = DynamicOptic.root.field("items").elements
      assert(optic.toString)(equalTo(".items[*]"))
    },
    test("toString for AtMapKey with String") {
      val optic = DynamicOptic.root.field("config").atKey("host")
      assert(optic.toString)(equalTo(".config{\"host\"}"))
    },
    test("toString for AtMapKey with Int") {
      val optic = DynamicOptic.root.field("ports").atKey(80)
      assert(optic.toString)(equalTo(".ports{80}"))
    },
    test("toString for AtMapKeys") {
      val optic = DynamicOptic.root.field("ports").atKeys(80, 8080)
      assert(optic.toString)(equalTo(".ports{80, 8080}"))
    },
    test("toString for MapValues") {
      val optic = DynamicOptic.root.field("lookup").mapValues
      assert(optic.toString)(equalTo(".lookup{*}"))
    },
    test("toString for MapKeys") {
      val optic = DynamicOptic.root.field("ports").mapKeys
      assert(optic.toString)(equalTo(".ports{*:}"))
    },
    test("toString for Wrapped") {
      val optic = DynamicOptic.root.wrapped
      assert(optic.toString)(equalTo(".~"))
    }
  )
}
