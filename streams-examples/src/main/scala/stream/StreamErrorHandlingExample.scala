package stream

import zio.blocks.streams.Stream
import zio.sbt.ExprEval.show

object StreamErrorHandlingExample extends App {
  println("=== Stream Error Handling ===\n")

  sealed trait ApiError
  case object NotFound                    extends ApiError
  case class ValidationError(msg: String) extends ApiError
  case class ServerError(code: Int)       extends ApiError

  // Basic fail
  println("1. Creating a failing stream:")
  val failed: Stream[ApiError, String] = Stream.fail(NotFound)
  show(failed.runCollect)

  // catchAll for recovery
  println("\n2. Recovering from errors with catchAll:")
  val recovered = Stream.fail(NotFound).catchAll(_ => Stream.succeed("default-value"))
  show(recovered.runCollect)

  // orElse for recovery
  println("\n3. Using orElse (lazy fallback evaluation):")
  val fallback = Stream.fail(NotFound) || Stream(1, 2, 3)
  show(fallback.runCollect)

  // Error transformation with error-producing flatMap
  println("\n4. Producing typed errors in flatMap:")
  val errorExample = Stream(1, 2, 3, 4).flatMap { x =>
    if (x == 3) Stream.fail[ApiError](ValidationError("cannot process"))
    else Stream(x)
  }
  show(errorExample.runCollect)

  // Handling errors in flatMap chains
  println("\n5. Error handling in flatMap chains:")
  val chain = Stream(1, 2, 3, 4).flatMap { x =>
    if (x == 3) Stream.fail(ValidationError(s"Cannot process $x"))
    else Stream(x * 10)
  }
  show(chain.runCollect)

  // Recovering from errors in flatMap
  println("\n6. Recovering from errors with catchAll in chains:")
  val recovered_chain = Stream(1, 2, 3, 4).flatMap { x =>
    if (x == 3) Stream.fail(ValidationError(s"Cannot process $x"))
    else Stream(x * 10)
  }
    .catchAll(_ => Stream.succeed(-1))

  show(recovered_chain.runCollect)

  // Handling typed errors from attempt
  println("\n7. Recovering typed errors from Stream.attempt with catchAll:")
  val risky = Stream.attempt("not-a-number".toInt)
  val safe  = risky.catchAll { case _: NumberFormatException =>
    Stream.succeed(-1)
  }
  show(safe.runCollect)

  // Multiple error branches
  println("\n8. Distinguishing error types in recovery:")
  val multi_errors = Stream(1, 2, 3, 4).flatMap { x =>
    x match {
      case 2 => Stream.fail(NotFound)
      case 3 => Stream.fail(ValidationError("Invalid data"))
      case _ => Stream(x * 10)
    }
  }.catchAll {
    case NotFound             => Stream("missing")
    case ValidationError(msg) => Stream(s"invalid: $msg")
    case _                    => Stream("unknown error")
  }

  show(multi_errors.runCollect)
}
