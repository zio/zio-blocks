package golem.runtime.rpc.jvm.internal

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GolemCliProcessSpec extends AnyFunSuite with Matchers {
  test("run returns stdout on success") {
    val result = GolemCliProcess.run(new java.io.File("."), Seq("sh", "-c", "printf 'ok'"))

    result.map(_.trim).shouldBe(Right("ok"))
  }

  test("run returns error on failure") {
    val result = GolemCliProcess.run(new java.io.File("."), Seq("sh", "-c", "echo boom; exit 2"))

    result.isLeft.shouldBe(true)
    result.left.foreach { msg =>
      msg.should(include("exit=2"))
      msg.should(include("boom"))
    }
  }
}
