package smalljsonbackendfromblocks

import java.nio.file.Files
import zio.http._

/**
 * Build a Small JSON Backend from Blocks — helpers for the examples
 *
 * Builds requests from strings, prints responses, and creates a fresh temporary
 * database file for each example run.
 */
object Demo {
  def get(target: String): Request = Request.get(url(target))

  def post(target: String, json: String): Request =
    Request.post(url(target), Body.fromArray(json.getBytes("UTF-8"), ContentType.`application/json`))

  def show(response: Response): Unit =
    println(s"${response.status.code} ${response.body.text}")

  def tempDbFile(): String = {
    val file = Files.createTempFile("bookstore", ".db")
    file.toFile.deleteOnExit()
    file.toString
  }

  private def url(target: String): URL =
    URL.parse(target).fold(error => throw new IllegalArgumentException(error), identity)
}
