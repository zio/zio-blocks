package example.minimal

import golem.runtime.annotations.{agentDefinition, constructor, description, endpoint, header}
import golem.BaseAgent

import scala.concurrent.Future

// ---------------------------------------------------------------------------
// Single-element constructor: @constructor private def create(value: String)
//
// The mount path variable must be named {value} — matching the @constructor
// parameter name.
// ---------------------------------------------------------------------------
@agentDefinition(mount = "/api/weather/{value}", cors = Array("*"))
@description("A weather agent demonstrating code-first HTTP routes with a single constructor parameter")
trait WeatherAgent extends BaseAgent {

  @constructor private def create(value: String): Unit = ()

  @endpoint(method = "GET", path = "/current/{city}")
  @description("Returns current weather for a city")
  def getWeather(city: String): Future[String]

  @endpoint(method = "GET", path = "/search?q={query}&limit={n}")
  @description("Search weather data")
  def search(query: String, n: Int): Future[String]

  @endpoint(method = "POST", path = "/report")
  @description("Submit a weather report with tenant header")
  def submitReport(@header("X-Tenant") tenantId: String, data: String): Future[String]

  @endpoint(method = "GET", path = "/greet/{name}/{*filePath}")
  @description("Catch-all path example")
  def greetWithPath(name: String, filePath: String): Future[String]

  @endpoint(method = "GET", path = "/")
  @description("Root endpoint")
  def root(): Future[String]

  @endpoint(method = "GET", path = "/public", auth = 0)
  @description("Public endpoint with auth disabled")
  def publicEndpoint(): Future[String]
}

// ---------------------------------------------------------------------------
// Tuple constructor: @constructor private def create(arg0: String, arg1: Int)
//
// Mount path variables must be named {arg0}, {arg1}, etc., matching the
// @constructor parameter names.
// ---------------------------------------------------------------------------
@agentDefinition(mount = "/api/inventory/{arg0}/{arg1}")
@description("An inventory agent demonstrating tuple constructor parameters")
trait InventoryAgent extends BaseAgent {

  @constructor private def create(arg0: String, arg1: Int): Unit = ()

  @endpoint(method = "GET", path = "/stock")
  @description("Get stock level for this warehouse/zone")
  def getStock(): Future[String]

  @endpoint(method = "GET", path = "/item/{itemId}")
  @description("Get a specific item")
  def getItem(itemId: String): Future[String]
}

// ---------------------------------------------------------------------------
// Case class constructor: @constructor private def create(region: String, catalog: String)
//
// Mount path variables match the @constructor parameter names: {region}, {catalog}.
// ---------------------------------------------------------------------------
final case class CatalogParams(region: String, catalog: String)
object CatalogParams {
  implicit val schema: zio.blocks.schema.Schema[CatalogParams] = zio.blocks.schema.Schema.derived
}

@agentDefinition(mount = "/api/catalog/{region}/{catalog}")
@description("A catalog agent demonstrating case-class constructor parameters")
trait CatalogAgent extends BaseAgent {

  @constructor private def create(region: String, catalog: String): Unit = ()

  @endpoint(method = "GET", path = "/search?q={query}")
  @description("Search the catalog")
  def search(query: String): Future[String]

  @endpoint(method = "GET", path = "/item/{itemId}")
  @description("Get a specific item")
  def getItem(itemId: String): Future[String]
}

// ---------------------------------------------------------------------------
// Phantom agent with webhook suffix
// ---------------------------------------------------------------------------
@agentDefinition(
  mount = "/webhook/{agent-type}/{value}",
  phantomAgent = true,
  webhookSuffix = "/{agent-type}/events"
)
@description("Demonstrates phantom agent with webhook suffix")
trait WebhookAgent extends BaseAgent {
  @constructor private def create(value: String): Unit = ()
  @endpoint(method = "POST", path = "/receive")
  def receive(payload: String): Future[String]
}

