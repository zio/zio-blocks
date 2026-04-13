/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stream

import zio.blocks.streams.Stream
import util.ShowExpr.show

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

  // Error transformation with mapError
  println("\n4. Transforming error types with mapError:")
  val mapped = (Stream.fail(NotFound): Stream[ApiError, String]).mapError {
    case NotFound => ServerError(404)
    case e        => e
  }
  show(mapped.runCollect)

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

  // Handling defects (exceptions)
  println("\n7. Catching defects (exceptions) with catchDefect:")
  val risky = Stream.attempt("not-a-number".toInt)
  val safe  = risky.catchDefect { case _: NumberFormatException =>
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
