//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-async:0.0.56
//> using file Model.scala
//> using file Pipeline.scala
// Run from this folder:  scala-cli run Step1WalkPagesExample.scala
// Or from zio-blocks-examples:  sbt "paginatedApi/runMain paginatedapi.Step1WalkPagesExample"

package paginatedapi

/**
 * Paginated API into a stream: walking pages with unfold, stopping on the last page
 *
 * Walks three pages with Stream.unfold and prints how many pages came back and which cursors were requested.
 */
object Step1WalkPagesExample {
  def main(args: Array[String]): Unit = {
    val api     = FakeTrackerApi.threePages
    val emitted = Pipeline.pages(api).runCollect
    println(s"pages emitted: ${emitted.map(_.size)}")
    println(s"cursors requested: ${api.requests}")
  }
}
