package golem

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BaseAgentSpec extends AnyFunSuite with Matchers {
  final class DummyAgent extends BaseAgent[Unit]

  test("BaseAgent delegates to platform accessors") {
    val agent = new DummyAgent

    intercept[UnsupportedOperationException] {
      agent.agentId
    }
    intercept[UnsupportedOperationException] {
      agent.agentType
    }
    intercept[UnsupportedOperationException] {
      agent.agentName
    }
  }
}
