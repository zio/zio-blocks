package golem.runtime.rpc.jvm

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JvmAgentClientConfigSpec extends AnyFunSuite with Matchers {
  test("fromEnv uses defaults when env vars are missing") {
    val cfg = JvmAgentClientConfig.fromEnv("demo-component")

    cfg.component.shouldBe("demo-component")
    cfg.golemCli.shouldBe("golem-cli")
    cfg.golemCliFlags.shouldBe(Vector("--local"))
  }

}
