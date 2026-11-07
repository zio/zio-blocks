//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-async:0.0.56
//> using file Model.scala
//> using file Pipeline.scala
// Run from this folder:  scala-cli run Step3ErrorsExample.scala
// Or from zio-blocks-examples:  sbt "paginatedApi/runMain paginatedapi.Step3ErrorsExample"

package paginatedapi

/**
 * Paginated API into a stream: handling errors, restarting a run
 *
 * Shows a failed page request, a malformed item and a second run of the same
 * stream.
 */
object Step3ErrorsExample {
  def main(args: Array[String]): Unit = {
    val flaky = new FakeTrackerApi(
      Map(
        None       -> Right(RawPage(List(FakeTrackerApi.item(1, "Login fails on Safari", "open")), Some("c2"))),
        Some("c2") -> Left("503 Service Unavailable")
      )
    )
    println(Pipeline.issues(flaky).runCollect)
    println(s"cursors requested: ${flaky.requests}")

    val malformed = new FakeTrackerApi(
      Map(None -> Right(RawPage(List("""{"id":"six","title":"Broken","status":"open"}"""), None)))
    )
    println(Pipeline.issues(malformed).runCollect)

    val api = FakeTrackerApi.threePages
    val s   = Pipeline.issues(api)
    println(s.runCollect.map(_.size))
    println(s.runCollect.map(_.size))
    println(s"cursors requested: ${api.requests}")
  }
}
