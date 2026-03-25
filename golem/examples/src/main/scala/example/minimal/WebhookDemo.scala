package example.minimal

import golem.runtime.annotations.{agentDefinition, constructor, description, endpoint}
import golem.BaseAgent
import zio.blocks.schema.Schema

import scala.concurrent.Future

final case class WebhookEvent(message: String, count: Int)
object WebhookEvent {
  implicit val schema: Schema[WebhookEvent] = Schema.derived
}

@agentDefinition(
  mount = "/api/webhook-demo/{value}",
  webhookSuffix = "/incoming",
  cors = Array("*")
)
@description("Demonstrates webhook creation and awaiting webhook payloads")
trait WebhookDemo extends BaseAgent {
  @constructor def create(value: String): Unit = ()

  @endpoint(method = "GET", path = "/create")
  @description("Creates a webhook and returns the webhook URL")
  def createWebhookUrl(): Future[String]

  @endpoint(method = "GET", path = "/await")
  @description("Awaits the webhook payload and returns the decoded JSON")
  def awaitWebhookJson(): Future[String]
}

