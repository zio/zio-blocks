package golem

import scala.language.experimental.macros

object RemoteAgent {
  implicit class Ops[Trait](private val remote: golem.RemoteAgent[Trait]) extends AnyVal {
    def api: Trait =
      golem.runtime.rpc.AgentClient.bind[Trait](remote.resolved)

    def rpc: Any = macro golem.runtime.macros.RemoteAgentOpsMacro.rpcImpl[Trait]
  }
}
