package golem.quickstart

import scala.sys.process.{Process, ProcessLogger}

object QuickstartClient {
  def main(args: Array[String]): Unit = {
    val rawFlags = sys.env.getOrElse("GOLEM_CLI_FLAGS", "--local")
    val flags    = rawFlags.split("\\s+").toList.filter(_.nonEmpty)

    def run(cmdArgs: List[String]): Unit = {
      val fullArgs = "golem-cli" :: (flags ++ cmdArgs)
      val exitCode = Process(fullArgs).!(ProcessLogger(line => println(line)))
      if (exitCode != 0) {
        sys.error(s"golem-cli failed with exit code $exitCode: ${fullArgs.mkString(" ")}")
      }
    }

    val agentId = s"agent-${System.currentTimeMillis() / 1000}"

    run(List("agent", "invoke", s"""scala:quickstart-counter/counter-agent("$agentId")""", "increment"))
    run(List("agent", "invoke", s"""scala:quickstart-counter/counter-agent("$agentId")""", "increment"))
    run(List("agent", "invoke", """scala:quickstart-counter/shard-agent("demo",42)""", "id"))
  }
}
