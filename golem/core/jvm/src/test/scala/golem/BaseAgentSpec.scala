package golem

import zio.test._

import scala.util.Try

object BaseAgentSpec extends ZIOSpecDefault {
  final class DummyAgent extends BaseAgent[Unit]

  def spec = suite("BaseAgentSpec")(
    test("BaseAgent delegates to platform accessors") {
      val agent = new DummyAgent

      assertTrue(
        Try(agent.agentId).failed.get.isInstanceOf[UnsupportedOperationException],
        Try(agent.agentType).failed.get.isInstanceOf[UnsupportedOperationException],
        Try(agent.agentName).failed.get.isInstanceOf[UnsupportedOperationException]
      )
    }
  )
}
