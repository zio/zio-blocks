package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic.Node.{Case, Elements, Field, MapKeys, MapValues}
import zio.blocks.schema.OpticCheck.{EmptyMap, EmptySequence, UnexpectedCase}
import zio.test._
import zio.test.Assertion._

object OpticCheckSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("OpticCheckSpec")(
    test("can contain errors") {
      val opticCheck = OpticCheck(errors =
        ::(
          UnexpectedCase(
            expectedCase = "Case2",
            actualCase = "Case1",
            full = DynamicOptic(Chunk(Case("Case2"), Field("lr3"), Elements)),
            prefix = DynamicOptic(Chunk(Case("Case2"))),
            actualValue = null
          ),
          Nil
        )
      )
      assert(opticCheck.getMessage)(
        equalTo(
          "During attempted access at .when[Case2].lr3.each, encountered an unexpected case at .when[Case2]: expected Case2, but got Case1"
        )
      ) &&
      assert(opticCheck.hasError)(equalTo(true)) &&
      assert(opticCheck.errors.forall(_.isError))(equalTo(true))
    },
    test("can contain warnings") {
      val opticCheck = OpticCheck(errors =
        ::(
          EmptySequence(
            full = DynamicOptic(Chunk(Elements, MapKeys)),
            prefix = DynamicOptic(Chunk(Elements))
          ),
          Nil
        )
      )
      assert(opticCheck.getMessage)(
        equalTo("During attempted access at .each.eachKey, encountered an empty sequence at .each")
      ) &&
      assert(opticCheck.hasWarning)(equalTo(true)) &&
      assert(opticCheck.errors.forall(_.isWarning))(equalTo(true))
    },
    test("can be combined") {
      val opticCheck1 = OpticCheck(errors =
        ::(
          UnexpectedCase(
            expectedCase = "Case2",
            actualCase = "Case1",
            full = DynamicOptic(Chunk(Case("Case2"), Field("lr3"), Elements)),
            prefix = DynamicOptic(Chunk(Case("Case2"))),
            actualValue = null
          ),
          Nil
        )
      )
      val opticCheck2 = OpticCheck(errors =
        ::(
          EmptyMap(
            full = DynamicOptic(Chunk(MapValues, Elements)),
            prefix = DynamicOptic(Chunk(MapValues))
          ),
          Nil
        )
      )
      val opticCheck3 = opticCheck1 ++ opticCheck2
      assert(opticCheck3.getMessage)(
        equalTo(
          "During attempted access at .when[Case2].lr3.each, encountered an unexpected case at .when[Case2]: expected Case2, but got Case1\nDuring attempted access at .eachValue.each, encountered an empty map at .eachValue"
        )
      ) &&
      assert(opticCheck3.hasError)(equalTo(true)) &&
      assert(opticCheck3.hasWarning)(equalTo(true))
    }
  )
}
