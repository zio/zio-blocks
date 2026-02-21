package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "storage-demo")
@description("Demonstrates KeyValue bucket CRUD, Blobstore container/object operations, and Config reading.")
trait StorageDemo extends BaseAgent[String] {

  @description("Full KeyValue lifecycle: open bucket, set/get/exists/keys/delete.")
  def keyValueDemo(): Future[String]

  @description("Full Blobstore lifecycle: create container, write/read/list/delete objects.")
  def blobstoreDemo(): Future[String]

  @description("Read config values with typed error handling.")
  def configDemo(): Future[String]
}

object StorageDemo extends AgentCompanion[StorageDemo, String]
