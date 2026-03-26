package example.integrationtests

import golem.HostApi
import golem.runtime.annotations.agentImplementation

import scala.annotation.unused
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

@agentImplementation()
final class WebhookDemoImpl(@unused private val key: String) extends WebhookDemo {

  private var pending: Option[HostApi.WebhookHandler] = None

  override def createWebhookUrl(): Future[String] = Future.successful {
    val webhook = HostApi.createWebhook()
    pending = Some(webhook)
    webhook.url
  }

  override def awaitWebhookJson(): Future[String] = {
    pending match {
      case Some(handler) =>
        pending = None
        handler.await().map { payload =>
          val event = payload.json[WebhookEvent]()
          s"message=${event.message},count=${event.count}"
        }
      case None =>
        Future.successful("no pending webhook")
    }
  }
}
