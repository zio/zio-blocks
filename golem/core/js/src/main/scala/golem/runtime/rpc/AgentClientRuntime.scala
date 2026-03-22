package golem.runtime.rpc

import golem.data.GolemSchema
import golem.host.js._
import golem.runtime.agenttype.{AgentMethod, AgentType, MethodInvocation}
import golem.runtime.util.FutureInterop
import golem.Uuid
import golem.Datetime

import scala.concurrent.Future
import scala.scalajs.js

object AgentClientRuntime {
  @volatile private var remoteResolverOverride: Option[(String, JsDataValue) => Either[String, RemoteAgentClient]] = None
  @volatile private var clientBinderOverride: Option[Any => Any]                                                  = None

  def resolve[Trait, Constructor](
    agentType: AgentType[Trait, Constructor],
    constructorArgs: Constructor
  ): Either[String, ResolvedAgent[Trait]] =
    resolveWithPhantom(agentType, constructorArgs, phantom = None)

  def resolveWithPhantom[Trait, Constructor](
    agentType: AgentType[Trait, Constructor],
    constructorArgs: Constructor,
    phantom: Option[Uuid]
  ): Either[String, ResolvedAgent[Trait]] = {
    implicit val ctorSchema: GolemSchema[Constructor] = agentType.constructor.schema

    for {
      payload <- {
        val any = constructorArgs.asInstanceOf[Any]
        if (any == ((): Unit)) {
          Right(JsDataValue.tuple(new js.Array[JsElementValue]()))
        } else {
          RpcValueCodec.encodeArgs[Constructor](constructorArgs)
        }
      }
      remote <- resolveRemote(agentType.typeName, payload, phantom)
    } yield ResolvedAgent(agentType.asInstanceOf[AgentType[Trait, Any]], remote)
  }

  private def resolveRemote(
    agentTypeName: String,
    payload: JsDataValue,
    phantom: Option[Uuid]
  ): Either[String, RemoteAgentClient] =
    remoteResolverOverride match {
      case Some(custom) => custom(agentTypeName, payload)
      case None         => RemoteAgentClient.resolve(agentTypeName, payload, phantom)
    }

  final case class ResolvedAgent[Trait](agentType: AgentType[Trait, Any], client: RemoteAgentClient) {
    def agentId: String = client.agentId

    /**
     * Always invoke via "invoke-and-await" regardless of `method.invocation`.
     *
     * This enables "await/trigger/schedule for any method" APIs (TS/Rust
     * parity).
     */
    def await[In, Out](method: AgentMethod[Trait, In, Out], input: In): Future[Out] =
      runAwaitable(method, input)

    def call[In, Out](method: AgentMethod[Trait, In, Out], input: In): Future[Out] =
      method.invocation match {
        case MethodInvocation.Awaitable =>
          runAwaitable(method, input)
        case MethodInvocation.FireAndForget =>
          runFireAndForget(method, input).asInstanceOf[Future[Out]]
      }

    private[golem] def callByName[In, Out](methodName: String, input: In): Future[Out] = {
      val method =
        agentType.methods.collectFirst {
          case p if p.metadata.name == methodName =>
            p.asInstanceOf[AgentMethod[Trait, In, Out]]
        }
          .getOrElse(throw new IllegalStateException(s"Method definition for $methodName not found"))

      call(method, input)
    }

    def trigger[In](method: AgentMethod[Trait, In, _], input: In): Future[Unit] =
      runFireAndForget(method, input)

    def schedule[In](method: AgentMethod[Trait, In, _], datetime: Datetime, input: In): Future[Unit] =
      runScheduled(method, datetime, input)

    private def runAwaitable[In, Out](method: AgentMethod[Trait, In, Out], input: In): Future[Out] = {
      implicit val inSchema: GolemSchema[In] = method.inputSchema

      val encoded                     = RpcValueCodec.encodeArgs(input)
      val functionName                = method.functionName
      val result: Either[String, Out] = for {
        params <- encoded
        raw    <- client.rpc.invokeAndAwait(functionName, params)
        value  <- {
          implicit val outSchema: GolemSchema[Out] = method.outputSchema
          RpcValueCodec.decodeResult[Out](raw)
        }
      } yield value

      FutureInterop.fromEither(result)
    }

    private def runFireAndForget[In, Out0](method: AgentMethod[Trait, In, Out0], input: In): Future[Unit] = {
      implicit val inSchema: GolemSchema[In] = method.inputSchema

      val functionName                 = method.functionName
      val result: Either[String, Unit] = for {
        params <- RpcValueCodec.encodeArgs(input)
        _      <- client.rpc.invoke(functionName, params)
      } yield ()

      FutureInterop.fromEither(result)
    }

    private def runScheduled[In, Out0](
      method: AgentMethod[Trait, In, Out0],
      datetime: Datetime,
      input: In
    ): Future[Unit] = {
      implicit val inSchema: GolemSchema[In] = method.inputSchema

      val functionName                 = method.functionName
      val result: Either[String, Unit] = for {
        params <- RpcValueCodec.encodeArgs(input)
        _      <- client.rpc.scheduleInvocation(datetime, functionName, params)
      } yield ()

      FutureInterop.fromEither(result)
    }
  }

  private[rpc] object TestHooks {
    def withRemoteResolver[T](resolver: (String, JsDataValue) => Either[String, RemoteAgentClient])(thunk: => T): T = {
      val previous = remoteResolverOverride
      remoteResolverOverride = Some(resolver)
      try thunk
      finally remoteResolverOverride = previous
    }

    def withClientBinder[Trait, A](binder: AgentClientRuntime.ResolvedAgent[Trait] => Trait)(thunk: => A): A = {
      val previous = clientBinderOverride
      clientBinderOverride =
        Some((resolved: Any) => binder(resolved.asInstanceOf[AgentClientRuntime.ResolvedAgent[Trait]]))
      try thunk
      finally clientBinderOverride = previous
    }

    def bindOverride[Trait](resolved: AgentClientRuntime.ResolvedAgent[Trait]): Option[Trait] =
      clientBinderOverride.map(_.asInstanceOf[AgentClientRuntime.ResolvedAgent[Trait] => Trait](resolved))
  }
}
