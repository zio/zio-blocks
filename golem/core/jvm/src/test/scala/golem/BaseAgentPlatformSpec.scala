package golem

import zio.test._

import scala.util.Try

object BaseAgentPlatformSpec extends ZIOSpecDefault {
  def spec = suite("BaseAgentPlatformSpec")(
    test("BaseAgentPlatform accessors throw on JVM") {
      val ex1 = Try(BaseAgentPlatform.agentId).failed.get
      val ex2 = Try(BaseAgentPlatform.agentType).failed.get
      val ex3 = Try(BaseAgentPlatform.agentName).failed.get

      assertTrue(
        ex1.isInstanceOf[UnsupportedOperationException],
        ex1.getMessage.contains("BaseAgent is only available"),
        ex2.isInstanceOf[UnsupportedOperationException],
        ex2.getMessage.contains("BaseAgent is only available"),
        ex3.isInstanceOf[UnsupportedOperationException],
        ex3.getMessage.contains("BaseAgent is only available")
      )
    }
  )
}
