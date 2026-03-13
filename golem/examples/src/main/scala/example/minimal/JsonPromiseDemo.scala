package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}
import zio.blocks.schema.Schema

import scala.concurrent.Future

final case class PromisePayload(message: String, count: Int)
object PromisePayload {
  implicit val schema: Schema[PromisePayload] = Schema.derived
}

@agentDefinition(typeName = "json-promise-demo")
@description("Demonstrates JSON-typed promises and blocking promise await.")
trait JsonPromiseDemo extends BaseAgent[String] {

  @description("Creates a promise, completes with JSON, and awaits the JSON result.")
  def jsonRoundtrip(): Future[String]

  @description("Creates a promise, completes with raw bytes, and uses blocking await.")
  def blockingDemo(): Future[String]
}

object JsonPromiseDemo extends AgentCompanion[JsonPromiseDemo, String]
