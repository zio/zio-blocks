package golem.quickstart

import golem.runtime.rpc.jvm.{JvmAgentClient, JvmAgentClientConfig}
import golem.quickstart.counter.CounterAgent
import golem.quickstart.shard.ShardAgent

import scala.concurrent.duration.*
import scala.concurrent.Await

/**
 * Repo-local JVM smoke test for the quickstart agents.
 *
 * Expected flow:
 *   - Deploy via golem-cli from the checked-in app dir
 *     (`./golem/quickstart/jvm-test.sh` runs from `golem/quickstart/app`)
 *   - Run this main (uses golem-cli to invoke the deployed agents)
 */
object QuickstartClient {
  def main(args: Array[String]): Unit = {
    val cfg = JvmAgentClientConfig.fromEnv(defaultComponent = "scala:quickstart-counter")
    JvmAgentClient.configure(cfg)

    val counterIdBase = sys.env.getOrElse("GOLEM_QUICKSTART_COUNTER_ID", "demo")
    val tableBase     = sys.env.getOrElse("GOLEM_QUICKSTART_SHARD_TABLE", "table")

    def runOnce(counterId: String, tableName: String): Unit = {
      val counter = Await.result(CounterAgent.get(counterId), 30.seconds)
      val n1      = Await.result(counter.increment(), 30.seconds)
      println(s"[quickstart-jvm] CounterAgent.increment($counterId) => $n1")

      val shard = Await.result(ShardAgent.get(tableName, 1), 30.seconds)
      val id    = Await.result(shard.id(), 30.seconds)
      println(s"[quickstart-jvm] ShardAgent.id($tableName,1) => $id")

      Await.result(shard.set("k", "v"), 30.seconds)
      val got = Await.result(shard.get("k"), 30.seconds)
      println(s"[quickstart-jvm] ShardAgent.get(k) => $got")
    }

    val forceFresh    = sys.env.get("GOLEM_QUICKSTART_FRESH_IDS").contains("1")
    val initialSuffix =
      if (forceFresh) "-" + java.util.UUID.randomUUID().toString
      else ""

    val counterId0 = s"$counterIdBase$initialSuffix"
    val table0     = s"$tableBase$initialSuffix"

    try runOnce(counterId0, table0)
    catch {
      case t: Throwable =>
        val msg           = Option(t.getMessage).getOrElse("")
        val looksPoisoned =
          msg.contains("Previous Invocation Failed") ||
            msg.contains("newCounterAgent") ||
            msg.contains("guest.initialize")

        if (!forceFresh && looksPoisoned) {
          val suffix = "-" + java.util.UUID.randomUUID().toString
          val c1     = s"$counterIdBase$suffix"
          val t1     = s"$tableBase$suffix"
          runOnce(c1, t1)
        } else throw t
    }
  }
}
