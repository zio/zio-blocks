//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-async:0.0.56
//> using file Model.scala
//> using file Pipeline.scala
// Run from this folder:  scala-cli run CompleteExample.scala
// Or from zio-blocks-examples:  sbt "paginatedApi/runMain paginatedapi.CompleteExample"

package paginatedapi

/**
 * How to Turn a Paginated API into a Stream: the complete program
 *
 * Reads the issues with the non-blocking walk and prints the titles, then reads
 * a tracker whose second page fails.
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {
    val api = FakeTrackerApi.threePages
    println(Pipeline.issuesAsync(api).runCollect.map(_.map(_.title)))
    println(s"cursors requested: ${api.requests}")

    val failing = new FakeTrackerApi(
      Map(
        None       -> Right(RawPage(List(FakeTrackerApi.item(1, "Login fails on Safari", "open")), Some("c2"))),
        Some("c2") -> Left("503 Service Unavailable")
      )
    )
    println(Pipeline.issuesAsync(failing).runCollect)
  }
}
