package example.integrationtests

import golem.runtime.annotations.{agentDefinition, description, endpoint}
import golem.BaseAgent

import scala.concurrent.Future

@agentDefinition(mount = "/api/fetch/{value}")
@description("Example agent demonstrating outgoing HTTP requests using fetch")
trait FetchAgent extends BaseAgent {
  class Id(val value: String)

  @endpoint(method = "GET", path = "/call?port={port}")
  @description("Makes a GET request to localhost on the given port and returns the response body")
  def fetchFromPort(port: Int): Future[String]
}
