package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "host-api-explorer")
@description("Explores raw host APIs to discover their JS shape for typing.")
trait HostApiExplorer extends BaseAgent[String] {

  @description("Explore the WASI config store module")
  def exploreConfig(): Future[String]

  @description("Explore the durability module")
  def exploreDurability(): Future[String]

  @description("Explore the context module")
  def exploreContext(): Future[String]

  @description("Explore the oplog module")
  def exploreOplog(): Future[String]

  @description("Explore the WASI keyvalue module")
  def exploreKeyValue(): Future[String]

  @description("Explore the WASI blobstore module")
  def exploreBlobstore(): Future[String]

  @description("Explore the RDBMS module")
  def exploreRdbms(): Future[String]

  @description("Explore all raw host APIs in one call")
  def exploreAll(): Future[String]
}

object HostApiExplorer extends AgentCompanion[HostApiExplorer, String]
