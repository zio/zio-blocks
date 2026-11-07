package shareonemodel

import zio.blocks.schema._

/** One item on a to-do list. */
final case class Task(id: Int, title: String, done: Boolean)

/** A to-do list that belongs to one owner. */
final case class TaskList(owner: String, tasks: List[Task])

object TaskList {
  implicit val schema: Schema[TaskList] = Schema.derived
}

/** The list the server sends: two tasks, one finished and one still open. */
object Sample {
  val list: TaskList = TaskList(
    "Ada",
    List(
      Task(1, "Write the tutorial", done = true),
      Task(2, "Share one model", done = false)
    )
  )
}
