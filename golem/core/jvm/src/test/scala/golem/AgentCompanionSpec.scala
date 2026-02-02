package golem

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class AgentCompanionSpec extends AnyFunSuite with Matchers {
  trait PhantomAgent  extends BaseAgent[Unit]
  object PhantomAgent extends AgentCompanion[PhantomAgent]

  private val phantomId = Uuid(BigInt(0), BigInt(1))

  private def assertUnsupported(call: => Any): Unit =
    try {
      call match {
        case future: Future[?] =>
          val failure = Await.result(future.failed, 2.seconds)
          failure shouldBe a[UnsupportedOperationException]
        case _ =>
          fail("Expected UnsupportedOperationException to be thrown")
      }
    } catch {
      case _: UnsupportedOperationException => ()
    }

  test("Jvm AgentCompanion phantom helpers throw") {
    assertUnsupported(PhantomAgent.getPhantom((), phantomId))
    assertUnsupported(PhantomAgent.getPhantom(phantomId))
    assertUnsupported(PhantomAgent.getPhantom("a", "b", phantomId))
    assertUnsupported(PhantomAgent.getPhantom(1, 2, 3, phantomId))
  }
}
