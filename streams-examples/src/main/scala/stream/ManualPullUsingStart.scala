package stream

import zio.blocks.streams.*
import zio.blocks.streams.io.Reader
import zio.blocks.scope.*

object ManualPullUsingStart extends App {
  Scope.global.scoped { scope =>
    import scope.*

    // Open a stream for manual pulling
    val reader: $[Reader[Int]] = Stream.range(1, 6).start(using scope)

    $(reader) { r =>
      // Iterate through reader values using the protocol directly
      // (cannot use scoped value in nested function, so use it directly)
      var current = r.read(-1)
      while (current != -1) {
        println(current) // prints 1, 2, 3, 4, 5
        current = r.read(-1)
      }
    }
    // reader is closed automatically when scope exits
  }
}
