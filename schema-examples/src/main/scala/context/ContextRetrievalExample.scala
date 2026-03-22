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

// Context#get[A] retrieves a value by type with compile-time proof of existence.
// Context#getOption[A] retrieves a value if present, returning None if missing.
// Supertype matching allows retrieving a subtype using a more general supertype.
object ContextRetrievalExample extends App {

  case class Config(debug: Boolean)
  case class Logger(name: String)
  case class Metrics(count: Int)

  trait Animal { def sound: String }
  case class Dog(name: String) extends Animal {
    def sound = "Woof"
  }

  // Create a context with multiple values.
  val ctx = Context(
    Config(debug = true),
    Logger("app"),
    Metrics(count = 42)
  )

  // Retrieve by exact type.
  val config: Config = ctx.get[Config]
  show(config)

  val logger: Logger = ctx.get[Logger]
  show(logger)

  val metrics: Metrics = ctx.get[Metrics]
  show(metrics)

  // getOption returns Some if the type is present.
  val configOpt: Option[Config] = ctx.getOption[Config]
  show(configOpt)

  // getOption returns None if the type is missing (safe for optional lookups).
  val missingOpt: Option[String] = ctx.getOption[String]
  show(missingOpt)

  // Supertype matching: retrieve a subtype using a supertype.
  val dogCtx         = Context(Dog("Buddy"))
  val animal: Animal = dogCtx.get[Animal]
  show(animal.sound)

  // getOption also supports supertype matching.
  val animalOpt: Option[Animal] = dogCtx.getOption[Animal]
  show(animalOpt.map(_.sound))

  // Cache efficiency: repeated lookups return the cached instance.
  val first  = ctx.get[Logger]
  val second = ctx.get[Logger]
  show(first eq second)
}
