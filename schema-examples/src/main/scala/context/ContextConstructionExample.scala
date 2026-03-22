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

package context

import zio.blocks.context._
import util.ShowExpr.show

// Context.empty creates an empty, type-safe dependency container.
// Use Context.apply(...) to construct contexts with 1–10 values.
// The phantom type parameter tracks which types are present.
object ContextConstructionExample extends App {

  case class Config(debug: Boolean)
  case class Logger(name: String)
  case class Metrics(count: Int)

  // Start with an empty context.
  show(Context.empty.size)
  show(Context.empty.isEmpty)

  // Create a context with one value.
  val ctx1 = Context(Config(debug = true))
  show(ctx1.size)
  show(ctx1.nonEmpty)

  // Create a context with multiple values (up to 10 supported).
  val ctx2 = Context(
    Config(debug = false),
    Logger("myapp")
  )
  show(ctx2.size)
  show(ctx2.isEmpty)

  // Create a larger context.
  val ctx3 = Context(
    Config(debug = true),
    Logger("prod"),
    Metrics(count = 100)
  )
  show(ctx3.size)

  // Build incrementally from empty using add.
  val ctxBuilt = Context.empty
    .add(Config(debug = false))
    .add(Logger("init"))
    .add(Metrics(count = 0))
  show(ctxBuilt.size)
  show(ctxBuilt.nonEmpty)

  // toString shows the context contents in a readable format.
  show(ctx3.toString)
}
