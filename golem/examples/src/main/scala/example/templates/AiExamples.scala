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

package example.templates

import golem.runtime.annotations.{agentDefinition, description, prompt}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "chat")
@description("LLM chat with durable history (Scala equivalent of the Rust/TS LLM session template).")
trait ChatAgent extends BaseAgent[String] {

  @description("Ask a question")
  def ask(question: String): Future[String]

  @description("Show chat history event tags (message/response/etc)")
  def history(): Future[List[String]]
}

object ChatAgent extends AgentCompanion[ChatAgent, String]

@agentDefinition(typeName = "research")
@description("Web search + summarize (Scala equivalent of the Rust/TS websearch summary template).")
trait ResearchAgent extends BaseAgent[Unit] {

  @prompt("What topic do you want to research?")
  @description("Research and summarize a topic")
  def research(topic: String): Future[String]
}

object ResearchAgent extends AgentCompanion[ResearchAgent, Unit]
