package golem

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AgentCompanionSpec extends AnyFunSuite with Matchers {
  trait PhantomAgent  extends BaseAgent[Unit]
  object PhantomAgent extends AgentCompanion[PhantomAgent]

  private val phantomId = Uuid(BigInt(0), BigInt(1))

  test("Jvm AgentCompanion phantom helpers throw") {
    intercept[UnsupportedOperationException] {
      PhantomAgent.getPhantom((), phantomId)
    }
    intercept[UnsupportedOperationException] {
      PhantomAgent.getPhantom(phantomId)
    }
    intercept[UnsupportedOperationException] {
      PhantomAgent.getPhantom("a", "b", phantomId)
    }
    intercept[UnsupportedOperationException] {
      PhantomAgent.getPhantom(1, 2, 3, phantomId)
    }
  }
}
