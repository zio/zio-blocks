package example.minimal

import golem.runtime.annotations.agentImplementation

import scala.annotation.unused
import scala.concurrent.Future

@agentImplementation()
final class WeatherAgentImpl(@unused private val apiKey: String) extends WeatherAgent {

  override def getWeather(city: String): Future[String] =
    Future.successful(s"Sunny in $city")

  override def search(query: String, n: Int): Future[String] =
    Future.successful(s"Found $n results for '$query'")

  override def submitReport(tenantId: String, data: String): Future[String] =
    Future.successful(s"Report from tenant $tenantId: $data")

  override def greetWithPath(name: String, filePath: String): Future[String] =
    Future.successful(s"Hello $name, path: $filePath")

  override def root(): Future[String] =
    Future.successful("Welcome to the Weather API")

  override def publicEndpoint(): Future[String] =
    Future.successful("Public endpoint")
}

@agentImplementation()
final class InventoryAgentImpl(
  @unused private val warehouse: String,
  @unused private val zone: Int
) extends InventoryAgent {

  override def getStock(): Future[String] =
    Future.successful(s"Stock for warehouse=$warehouse, zone=$zone: 42 items")

  override def getItem(itemId: String): Future[String] =
    Future.successful(s"Item $itemId in warehouse=$warehouse, zone=$zone")
}

@agentImplementation()
final class WebhookAgentImpl(@unused private val key: String) extends WebhookAgent {
  override def receive(payload: String): Future[String] =
    Future.successful(s"Received: $payload")
}
