package example.templates

import golem.runtime.annotations.{agentDefinition, description, prompt}
import golem.{AgentCompanion, BaseAgent}
import zio.blocks.schema.Schema

import scala.concurrent.Future

final case class Task(id: Int, title: String, completed: Boolean, createdAt: String)
object Task {
  implicit val schema: Schema[Task] = Schema.derived
}

final case class CreateTaskRequest(title: String)
object CreateTaskRequest {
  implicit val schema: Schema[CreateTaskRequest] = Schema.derived
}

@agentDefinition(typeName = "tasks")
@description("A simple agent demonstrating JSON API support (Scala equivalent of the Rust/TS JSON template).")
trait Tasks extends BaseAgent[String] {

  @prompt("Create a new task with the given title")
  @description("Creates a task and returns the complete task object")
  def createTask(request: CreateTaskRequest): Future[Task]

  @prompt("List all existing tasks")
  @description("Returns all tasks as a JSON array")
  def getTasks(): Future[List[Task]]

  @description("Marks a task as completed by its ID")
  def completeTask(id: Int): Future[Option[Task]]
}

object Tasks extends AgentCompanion[Tasks, String]
