package golem

import golem.runtime.agenttype.{AgentMethod, AgentType}
import golem.runtime.rpc.AgentClientRuntime

import scala.concurrent.Future

/**
 * A remote agent handle that exposes explicit await/trigger/schedule flows.
 *
 * Prefer `remote.rpc.call_foo(...)` (or `remote.call(...)`) to always await,
 * regardless of the method's declared return type. Use `remote.rpc.trigger_foo`
 * or `remote.rpc.schedule_foo` for fire-and-forget behavior.
 */
final case class RemoteAgent[Trait](
  private[golem] val resolved: AgentClientRuntime.ResolvedAgent[Trait]
) {
  def agentId: String = resolved.agentId

  def agentType: AgentType[Trait, Any] = resolved.agentType

  def call[In, Out](method: AgentMethod[Trait, In, Out], input: In): Future[Out] =
    resolved.await(method, input)

  /**
   * Fire-and-forget invocation; always returns Future[Unit] regardless of
   * method return type.
   */
  def trigger[In](method: AgentMethod[Trait, In, _], input: In): Future[Unit] =
    resolved.trigger(method, input)

  /**
   * Scheduled fire-and-forget; always returns Future[Unit] regardless of method
   * return type.
   */
  def schedule[In](method: AgentMethod[Trait, In, _], datetime: Datetime, input: In): Future[Unit] =
    resolved.schedule(method, datetime, input)
}
