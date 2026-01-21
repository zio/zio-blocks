package golem

import golem.runtime.agenttype.{AgentMethod, AgentType}
import golem.runtime.rpc.AgentClientRuntime

import scala.concurrent.Future

/**
 * A remote agent handle that exposes all three invocation styles regardless of
 * a method's declared return type.
 *
 *   - **await**: `call(...)` (same as calling the method on `api`)
 *   - **trigger**: `trigger(...)`
 *   - **schedule**: `schedule(...)`
 */
final case class RemoteAgent[Trait](
  api: Trait,
  private[golem] val resolved: AgentClientRuntime.ResolvedAgent[Trait]
) {
  def agentId: String = resolved.agentId

  def agentType: AgentType[Trait, Any] = resolved.agentType

  def call[In, Out](method: AgentMethod[Trait, In, Out], input: In): Future[Out] =
    resolved.await(method, input)

  /** Fire-and-forget invocation; always returns Future[Unit] regardless of method return type. */
  def trigger[In](method: AgentMethod[Trait, In, _], input: In): Future[Unit] =
    resolved.trigger(method, input)

  /** Scheduled fire-and-forget; always returns Future[Unit] regardless of method return type. */
  def schedule[In](method: AgentMethod[Trait, In, _], datetime: Datetime, input: In): Future[Unit] =
    resolved.schedule(method, datetime, input)
}
