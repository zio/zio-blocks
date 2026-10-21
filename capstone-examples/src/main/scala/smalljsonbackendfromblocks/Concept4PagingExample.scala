package smalljsonbackendfromblocks

/**
 * Build a Small JSON Backend from Blocks — Check the Query Parameters
 *
 * Reads `limit` and `offset` from a few request targets, valid and invalid.
 *
 * Run with: cd capstone-examples && sbt "runMain
 * smalljsonbackendfromblocks.Concept4PagingExample"
 */
object Concept4PagingExample {
  def main(args: Array[String]): Unit =
    List("/books", "/books?limit=1&offset=2", "/books?limit=abc", "/books?limit=500", "/books?offset=-1")
      .foreach(target => println(s"$target -> ${Paging.from(Demo.get(target), pageSize = 2)}"))
}
