package golem.runtime.rpc.jvm

import golem.runtime.MethodMetadata
import golem.runtime.agenttype.{AgentMethod, AgentType, ConstructorType, MethodInvocation}
import golem.data.{GolemSchema, StructuredSchema}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import java.lang.reflect.Proxy

class JvmAgentClientSpec extends AnyFunSuite with Matchers {

  trait FutureAgent {
    def ping(): Future[String]
  }

  trait SyncAgent {
    def ping(): String
  }

  trait DemoAgent {
    def ping(): Future[String]
    def lookupThing(name: String, count: Int): Future[String]
    def badArg(arg: BigInt): Future[String]
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

    proxy.toString.should(include("JvmAgentClientProxy(demo/FutureAgent())"))
    proxy.hashCode().shouldBe("demo/FutureAgent()".hashCode)
    (proxy == proxy).shouldBe(true)
  }

  test("non-Future methods are rejected") {
    JvmAgentClient.configure(JvmAgentClientConfig(component = "demo"))
    val proxy = JvmAgentClient.connect[SyncAgent](agentType(classOf[SyncAgent], "SyncAgent"), ())

    intercept[UnsupportedOperationException] {
      proxy.ping()
    }
  }

  test("Future methods invoke CLI and decode output") {
    val methodMeta = MethodMetadata(
      name = "ping",
      description = None,
      prompt = None,
      mode = None,
      input = StructuredSchema.Tuple(Nil),
      output = StructuredSchema.Tuple(Nil)
    )
    val method = AgentMethod[DemoAgent, Unit, Unit](
      metadata = methodMeta,
      functionName = "demo.ping",
      inputSchema = GolemSchema.unitGolemSchema,
      outputSchema = GolemSchema.unitGolemSchema,
      invocation = MethodInvocation.Awaitable
    )
    val aType: AgentType[DemoAgent, Unit] = AgentType(
      traitClassName = classOf[DemoAgent].getName,
      typeName = "DemoAgent",
      constructor = ConstructorType(GolemSchema.unitGolemSchema),
      methods = List(method)
    )

    JvmAgentClient.configure(
      JvmAgentClientConfig(
        component = "demo",
        golemCli = "sh",
        golemCliFlags = Vector("-c", "printf 'Invocation results in WAVE format:\\n - \"ok\"'")
      )
    )

    val proxy  = JvmAgentClient.connect[DemoAgent](aType, ())
    val result = Await.result(proxy.ping(), 2.seconds)
    result.shouldBe("ok")
  }

  test("configure uses default CLI settings") {
    JvmAgentClient.configure("demo-defaults")
    val proxy = JvmAgentClient.connect[FutureAgent](agentType(classOf[FutureAgent], "FutureAgent"), ())
    proxy.toString.should(include("demo-defaults/FutureAgent()"))
  }

  test("connect renders constructor args in agent id") {
    val aType: AgentType[DemoAgent, (Int, String)] = AgentType(
      traitClassName = classOf[DemoAgent].getName,
      typeName = "DemoAgent",
      constructor = ConstructorType(implicitly[GolemSchema[(Int, String)]]),
      methods = Nil
    )

    JvmAgentClient.configure(JvmAgentClientConfig(component = "demo"))
    val proxy = JvmAgentClient.connect[DemoAgent](aType, (1, "a"))

    proxy.toString.should(include("DemoAgent(1, \"a\")"))
  }

  test("equals handles null argument array") {
    JvmAgentClient.configure(JvmAgentClientConfig(component = "demo"))
    val proxy   = JvmAgentClient.connect[FutureAgent](agentType(classOf[FutureAgent], "FutureAgent"), ())
    val handler = Proxy.getInvocationHandler(proxy)
    val method  = proxy.getClass.getMethod("equals", classOf[Object])
    val result  = handler.invoke(proxy, method, null).asInstanceOf[java.lang.Boolean]

    result.booleanValue().shouldBe(false)
  }

  test("connect rejects unsupported constructor args") {
    val aType: AgentType[DemoAgent, Unit] = AgentType(
      traitClassName = classOf[DemoAgent].getName,
      typeName = "DemoAgent",
      constructor = ConstructorType(GolemSchema.unitGolemSchema),
      methods = Nil
    )

    JvmAgentClient.configure(JvmAgentClientConfig(component = "demo"))

    intercept[IllegalArgumentException] {
      JvmAgentClient.connect[DemoAgent](aType, BigInt(1))
    }
  }

  test("method name fallback uses kebab-case") {
    val aType: AgentType[DemoAgent, Unit] = AgentType(
      traitClassName = classOf[DemoAgent].getName,
      typeName = "DemoAgent",
      constructor = ConstructorType(GolemSchema.unitGolemSchema),
      methods = Nil
    )

    JvmAgentClient.configure(
      JvmAgentClientConfig(
        component = "demo",
        golemCli = "sh",
        golemCliFlags = Vector("-c", "printf 'Invocation results in WAVE format:\\n - \"ok\"'")
      )
    )

    val proxy  = JvmAgentClient.connect[DemoAgent](aType, ())
    val result = Await.result(proxy.lookupThing("x", 1), 2.seconds)
    result.shouldBe("ok")
  }

  test("unsupported argument types are rejected before invoking CLI") {
    val aType: AgentType[DemoAgent, Unit] = AgentType(
      traitClassName = classOf[DemoAgent].getName,
      typeName = "DemoAgent",
      constructor = ConstructorType(GolemSchema.unitGolemSchema),
      methods = Nil
    )

    JvmAgentClient.configure(
      JvmAgentClientConfig(
        component = "demo",
        golemCli = "sh",
        golemCliFlags = Vector("-c", "printf 'Invocation results in WAVE format:\\n - \"ok\"'")
      )
    )

    val proxy = JvmAgentClient.connect[DemoAgent](aType, ())
    intercept[IllegalArgumentException] {
      proxy.badArg(BigInt(1))
    }
  }

  test("fallback to kebab-case when agent type not found") {
    val aType: AgentType[DemoAgent, Unit] = AgentType(
      traitClassName = classOf[DemoAgent].getName,
      typeName = "DemoAgent",
      constructor = ConstructorType(GolemSchema.unitGolemSchema),
      methods = Nil
    )

    JvmAgentClient.configure(
      JvmAgentClientConfig(
        component = "demo",
        golemCli = "sh",
        golemCliFlags = Vector("-c", "echo 'Agent type not found'; exit 1")
      )
    )

    val proxy = JvmAgentClient.connect[DemoAgent](aType, ())
    val ex    = intercept[RuntimeException] {
      Await.result(proxy.ping(), 2.seconds)
    }

    ex.getMessage.should(include("Agent type not found"))
  }

  test("fallback handles agent parse errors") {
    val aType: AgentType[DemoAgent, Unit] = AgentType(
      traitClassName = classOf[DemoAgent].getName,
      typeName = "DemoAgent",
      constructor = ConstructorType(GolemSchema.unitGolemSchema),
      methods = Nil
    )

    JvmAgentClient.configure(
      JvmAgentClientConfig(
        component = "demo",
        golemCli = "sh",
        golemCliFlags = Vector("-c", "echo 'Failed to parse agent name'; exit 1")
      )
    )

    val proxy = JvmAgentClient.connect[DemoAgent](aType, ())
    val ex    = intercept[RuntimeException] {
      Await.result(proxy.ping(), 2.seconds)
    }

    ex.getMessage.should(include("Failed to parse agent name"))
  }

  test("fallback succeeds when kebab-case agent exists") {
    val aType: AgentType[DemoAgent, Unit] = AgentType(
      traitClassName = classOf[DemoAgent].getName,
      typeName = "DemoAgent",
      constructor = ConstructorType(GolemSchema.unitGolemSchema),
      methods = Nil
    )

    val script =
      """if echo "$@" | grep -q "demo-agent"; then
        |  printf 'Invocation results in WAVE format:\n - "ok"'
        |  exit 0
        |else
        |  echo 'Agent type not found'
        |  exit 1
        |fi
        |""".stripMargin

    JvmAgentClient.configure(
      JvmAgentClientConfig(
        component = "demo",
        golemCli = "sh",
        golemCliFlags = Vector("-c", script)
      )
    )

    val proxy  = JvmAgentClient.connect[DemoAgent](aType, ())
    val result = Await.result(proxy.ping(), 2.seconds)
    result.shouldBe("ok")
  }

  test("non-type errors do not trigger fallback") {
    val aType: AgentType[DemoAgent, Unit] = AgentType(
      traitClassName = classOf[DemoAgent].getName,
      typeName = "DemoAgent",
      constructor = ConstructorType(GolemSchema.unitGolemSchema),
      methods = Nil
    )

    JvmAgentClient.configure(
      JvmAgentClientConfig(
        component = "demo",
        golemCli = "sh",
        golemCliFlags = Vector("-c", "echo 'boom'; exit 1")
      )
    )

    val proxy = JvmAgentClient.connect[DemoAgent](aType, ())
    val ex    = intercept[RuntimeException] {
      Await.result(proxy.ping(), 2.seconds)
    }

    ex.getMessage.should(include("boom"))
  }

  test("missing wave output fails decoding") {
    val aType: AgentType[DemoAgent, Unit] = AgentType(
      traitClassName = classOf[DemoAgent].getName,
      typeName = "DemoAgent",
      constructor = ConstructorType(GolemSchema.unitGolemSchema),
      methods = Nil
    )

    JvmAgentClient.configure(
      JvmAgentClientConfig(
        component = "demo",
        golemCli = "sh",
        golemCliFlags = Vector("-c", "printf 'no wave here'")
      )
    )

    val proxy = JvmAgentClient.connect[DemoAgent](aType, ())
    val ex    = intercept[RuntimeException] {
      Await.result(proxy.ping(), 2.seconds)
    }

    ex.getMessage.should(include("Could not find WAVE result"))
  }

  test("invalid wave payload reports decode error") {
    val aType: AgentType[DemoAgent, Unit] = AgentType(
      traitClassName = classOf[DemoAgent].getName,
      typeName = "DemoAgent",
      constructor = ConstructorType(GolemSchema.unitGolemSchema),
      methods = Nil
    )

    JvmAgentClient.configure(
      JvmAgentClientConfig(
        component = "demo",
        golemCli = "sh",
        golemCliFlags = Vector("-c", "printf 'Invocation results in WAVE format:\\n - not-a-number'")
      )
    )

    val proxy = JvmAgentClient.connect[DemoAgent](aType, ())
    val ex    = intercept[RuntimeException] {
      Await.result(proxy.ping(), 2.seconds)
    }

    ex.getMessage.should(include("Unsupported WAVE result"))
  }
}
