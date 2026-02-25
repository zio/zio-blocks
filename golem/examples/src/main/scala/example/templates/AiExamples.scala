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
