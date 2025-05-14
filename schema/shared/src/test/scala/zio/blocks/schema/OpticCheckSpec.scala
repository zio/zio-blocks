package zio.blocks.schema
import zio.Scope
import zio.test.Assertion._
import zio.test._
import zio.internal.macros.Node

object OpticCheckSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("OpticCheckSpec")(
      test("Algebra of OpticCheck") {
        // val test = new DynamicOptic(IndexedSeq(Node("test")))
        val test = new DynamicOptic(IndexedSeq(???))
        val check1 = OpticCheck(
          ::(
            OpticCheck.UnexpectedCase(
              "case1",
              "case2",
              DynamicOptic(???),
              DynamicOptic(???),
              "actualValue"
            ),
            ???
          )
        )

        val combinedCheck = check1 ++ check1
        assert(combinedCheck.errors.length)(equalTo(4))
        assert(true)(equalTo(true))
      }
    )
}
