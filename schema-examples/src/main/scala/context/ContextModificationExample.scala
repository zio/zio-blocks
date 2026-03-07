package context

import zio.blocks.context._
import util.ShowExpr.show

// Context is immutable; modification methods return new contexts.
// Context#add expands the context with a new value (or replaces if type exists).
// Context#update transforms a value if present.
// Context#++ merges two contexts (right side wins on conflict).
// Context#prune narrows to specific types.
object ContextModificationExample extends App {

  case class Config(debug: Boolean)
  case class Logger(name: String)
  case class Metrics(count: Int)

  // Start with a simple context.
  val ctx1 = Context(Config(debug = false))
  show(ctx1.size)

  // add expands the context with a new value.
  val ctx2 = ctx1.add(Logger("app"))
  show(ctx2.size)
  show(ctx2.get[Config])
  show(ctx2.get[Logger])

  // add replaces if the type already exists.
  val ctx3 = ctx2.add(Config(debug = true))
  show(ctx3.size)
  show(ctx3.get[Config].debug)

  // update transforms a value if present.
  val ctx4    = ctx3.add(Metrics(count = 100))
  val updated = ctx4.update[Metrics](m => m.copy(count = m.count + 50))
  show(updated.get[Metrics].count)

  // update also works on existing types.
  val configUpdated = updated.update[Config](c => c.copy(debug = false))
  show(configUpdated.get[Config])

  // ++ merges two contexts; right side wins on conflict.
  val left   = Context(Config(debug = false), Logger("left"))
  val right  = Context(Config(debug = true), Metrics(count = 99))
  val merged = left ++ right
  show(merged.size)
  show(merged.get[Config].debug)
  show(merged.get[Logger].name)
  show(merged.get[Metrics].count)

  // prune narrows a context to specific types.
  val full = Context(Config(true), Logger("app"), Metrics(100))
  show(full.size)
  val justConfig = full.prune[Config]
  show(justConfig.size)
  show(justConfig.getOption[Logger])

  // Chaining modifications.
  val chain = Context.empty
    .add(Config(debug = true))
    .add(Logger("chain"))
    .add(Metrics(count = 0))
    .update[Metrics](m => m.copy(count = 42))
  show(chain.size)
  show(chain.get[Metrics].count)
}
