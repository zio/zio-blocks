package golem.runtime.rpc.jvm

import golem.data.GolemSchema
import golem.runtime.agenttype.{AgentType, ConstructorType}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class JvmAgentClientSpec extends AnyFunSuite with Matchers {

  trait FutureAgent {
    def ping(): Future[String]
  }

  trait SyncAgent {
    def ping(): String
  }

  private def agentType[T](traitClass: Class[T], typeName: String): AgentType[T, Unit] =
    AgentType(traitClass.getName, typeName, ConstructorType(GolemSchema.unitGolemSchema), Nil)

  test("connect throws when not configured") {
    val ex = intercept[IllegalStateException] {
      JvmAgentClient.connect[FutureAgent](agentType(classOf[FutureAgent], "FutureAgent"), ())
    }
    ex.getMessage should include("JvmAgentClient is not configured")
  }

  test("proxy object methods are handled without invoking the CLI") {
    JvmAgentClient.configure(JvmAgentClientConfig(component = "demo"))
    val proxy = JvmAgentClient.connect[FutureAgent](agentType(classOf[FutureAgent], "FutureAgent"), ())

    proxy.toString should include("JvmAgentClientProxy(demo/FutureAgent())")
    proxy.hashCode() shouldBe "demo/FutureAgent()".hashCode
    (proxy == proxy) shouldBe true
  }

  test("non-Future methods are rejected") {
    JvmAgentClient.configure(JvmAgentClientConfig(component = "demo"))
    val proxy = JvmAgentClient.connect[SyncAgent](agentType(classOf[SyncAgent], "SyncAgent"), ())

    intercept[UnsupportedOperationException] {
      proxy.ping()
    }
  }
}
