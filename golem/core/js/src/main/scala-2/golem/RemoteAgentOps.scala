package golem

import scala.language.experimental.macros

/**
 * Scala 2 RPC surface that mirrors the Scala 3 `RemoteAgentOps` helpers:
 *
 *   - `remote.api.foo(...)` -> await (normal trait call)
 *   - `remote.rpc.call_foo(...)` -> await (always invoke-and-await)
 *   - `remote.rpc.trigger_foo(...)` -> trigger
 *   - `remote.rpc.schedule_foo(ts, ...)` -> schedule
 */
object RemoteAgentOps {
  implicit class Ops[Trait](private val remote: RemoteAgent[Trait]) extends AnyVal {
    def rpc = macro golem.runtime.macros.RemoteAgentOpsMacro.rpcImpl[Trait]
  }
}
