// Run from this folder:  sbt "shareOneModelJS/run"   (needs Node.js; start the server first)
package shareonemodel

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.thenable2future
import zio.blocks.schema._

/**
 * Share One Model: the Scala.js client decodes the JSON with the same `Schema`.
 *
 * Fetches the list from the server and prints it, or prints the decode error.
 */
object Concept5DecodeOnScalaJsExample {
  def main(args: Array[String]): Unit = {
    val response: Future[js.Dynamic] =
      js.Dynamic.global.fetch("http://localhost:8090/tasks").asInstanceOf[js.Promise[js.Dynamic]]
    val body: Future[String] =
      response.flatMap(r => r.text().asInstanceOf[js.Promise[String]])

    body.foreach { json =>
      json.fromJson[TaskList] match {
        case Left(error) => println(s"Decode failed: ${error.message}")
        case Right(list) =>
          println(s"Owner: ${list.owner}")
          list.tasks.foreach { task =>
            println(s"[${if (task.done) "x" else " "}] ${task.id}: ${task.title}")
          }
      }
    }
    body.failed.foreach(e => println(s"Request failed: ${e.getMessage}"))
  }
}
