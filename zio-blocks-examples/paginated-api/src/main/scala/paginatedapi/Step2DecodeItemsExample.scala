//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-async:0.0.56
//> using file Model.scala
//> using file Pipeline.scala
// Run from this folder:  scala-cli run Step2DecodeItemsExample.scala
// Or from zio-blocks-examples:  sbt "paginatedApi/runMain paginatedapi.Step2DecodeItemsExample"

package paginatedapi

/**
 * Paginated API into a stream: flattening pages and decoding items
 *
 * Flattens the pages into issues, decodes each item with the Issue schema, and prints the titles.
 */
object Step2DecodeItemsExample {
  def main(args: Array[String]): Unit = {
    val api = FakeTrackerApi.threePages
    println(Pipeline.issues(api).runCollect.map(_.map(_.title)))
    println(s"cursors requested: ${api.requests}")
  }
}
