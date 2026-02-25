package golem

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BaseAgentPlatformSpec extends AnyFunSuite with Matchers {
  test("BaseAgentPlatform accessors throw on JVM") {
    val ex1 = intercept[UnsupportedOperationException](BaseAgentPlatform.agentId)
    val ex2 = intercept[UnsupportedOperationException](BaseAgentPlatform.agentType)
    val ex3 = intercept[UnsupportedOperationException](BaseAgentPlatform.agentName)

    ex1.getMessage.should(include("BaseAgent is only available"))
    ex2.getMessage.should(include("BaseAgent is only available"))
    ex3.getMessage.should(include("BaseAgent is only available"))
  }
}
