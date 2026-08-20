package reader

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.blocks.streams.Stream
import zio.blocks.scope.Scope

/**
 * Demonstrates reader composition with ++ (concat), resource cleanup with
 * withRelease, and integration with Stream.start for manual element-by-element
 * pulling within a Scope.
 */
object ReaderCompositionExample extends App {

  println("=== concat: ++ operator ===")
  val r1       = Reader.fromChunk(Chunk(1, 2, 3))
  val r2       = Reader.fromChunk(Chunk(4, 5, 6))
  val combined = r1 ++ r2

  var v           = combined.read(-1)
  val allCombined = scala.collection.mutable.ArrayBuffer[Int]()
  while (v != -1) {
    allCombined += v
    v = combined.read(-1)
  }
  println(s"Combined result: ${allCombined.toList}")

  println("\n=== Multiple concat: a ++ b ++ c ===")
  val ra    = Reader.fromChunk(Chunk("a"))
  val rb    = Reader.fromChunk(Chunk("b"))
  val rc    = Reader.fromChunk(Chunk("c"))
  val multi = ra ++ rb ++ rc

  var sv     = multi.read(null: String)
  val result = scala.collection.mutable.ArrayBuffer[String]()
  while (sv != null) {
    result += sv
    sv = multi.read(null: String)
  }
  println(s"Multiple concat: ${result.toList}")

  println("\n=== withRelease: cleanup on close ===")
  var cleanupCalled  = false
  val resourceReader = Reader.fromChunk(Chunk(10, 20)).withRelease { () =>
    cleanupCalled = true
    println("  Cleanup executed!")
  }
  var res = resourceReader.read(-1)
  while (res != -1) {
    res = resourceReader.read(-1)
  }
  resourceReader.close()
  println(s"Cleanup was called: $cleanupCalled")

  println("\n=== Stream.start: manual pull with Scope ===")
  Scope.global.scoped { scope =>
    import scope.*

    // Create a stream and open it for manual pulling
    val reader: scope.$[Reader[Int]] = Stream.range(1, 6).start(using scope)

    $(reader) { r =>
      var streamV      = r.read(-1)
      val manualResult = scala.collection.mutable.ArrayBuffer[Int]()
      while (streamV != -1) {
        manualResult += streamV
        streamV = r.read(-1)
      }
      println(s"Manual stream pull: ${manualResult.toList}")
    }
    // reader is automatically closed when scope exits
  }

  println("\n=== repeat: infinite reader ===")
  val infiniteReader = Reader.repeat(99)
  infiniteReader.setRepeat()

  var repeatCount = 0
  var repV        = infiniteReader.read(-1)
  while (repeatCount < 3) {
    println(s"Infinite read $repeatCount: $repV")
    repV = infiniteReader.read(-1)
    repeatCount += 1
  }
  infiniteReader.close()
}
