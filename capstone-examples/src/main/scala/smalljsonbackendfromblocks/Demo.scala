package smalljsonbackendfromblocks

import zio.http._

/**
 * Build a Small JSON Backend from Blocks — helpers for the examples
 *
 * Builds requests from strings and prints responses, so the examples can call
 * the routes without a server.
 */
object Demo {
  def get(target: String): Request = Request.get(url(target))

  def post(target: String, json: String): Request =
    Request.post(url(target), Body.fromArray(json.getBytes("UTF-8"), ContentType.`application/json`))

  def show(response: Response): Unit =
    println(s"${response.status.code} ${response.body.text}")

  private def url(target: String): URL =
    URL.parse(target).fold(error => throw new IllegalArgumentException(error), identity)
}
