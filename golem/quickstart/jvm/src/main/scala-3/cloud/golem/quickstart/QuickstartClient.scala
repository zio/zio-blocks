package cloud.golem.quickstart

import cloud.golem.runtime.rpc.jvm.{JvmAgentClient, JvmAgentClientConfig}
import cloud.golem.quickstart.counter.CounterAgent
import cloud.golem.quickstart.shard.ShardAgent

import scala.concurrent.duration.*
import scala.concurrent.Await

/**
 * Repo-local JVM smoke test for the quickstart agents.
 *
 * Expected flow:
 *   - Deploy the quickstart JS component (e.g.
 *     `zioGolemQuickstartJS/golemDeploy`)
 *   - Run this main (uses golem-cli to invoke the deployed agents)
 */
object QuickstartClient {
  def main(args: Array[String]): Unit = {
    val cfg = JvmAgentClientConfig.fromEnv(defaultComponent = "scala:quickstart-counter")
    JvmAgentClient.configure(cfg)

    val counter = Await.result(CounterAgent.get("demo"), 30.seconds)
    val n1      = Await.result(counter.increment(), 30.seconds)
    println(s"[quickstart-jvm] CounterAgent.increment => $n1")

    val shard = Await.result(ShardAgent.get("table", 1), 30.seconds)
    val id    = Await.result(shard.id(), 30.seconds)
    println(s"[quickstart-jvm] ShardAgent.id => $id")

    Await.result(shard.set("k", "v"), 30.seconds)
    val got = Await.result(shard.get("k"), 30.seconds)
    println(s"[quickstart-jvm] ShardAgent.get(k) => $got")
  }
}
