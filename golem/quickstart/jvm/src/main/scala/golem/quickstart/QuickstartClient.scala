/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
