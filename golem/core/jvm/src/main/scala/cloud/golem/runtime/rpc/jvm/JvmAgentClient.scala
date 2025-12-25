package cloud.golem.runtime.rpc.jvm

import cloud.golem.runtime.plan.AgentClientPlan
import cloud.golem.runtime.rpc.jvm.internal.{GolemCliProcess, WaveTextCodec}

import java.lang.reflect.{InvocationHandler, Method, Proxy}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Repo-local JVM testing client backed by `golem-cli`.
 *
 * This is intentionally minimal and expected to be replaced by a real external client in the future.
 */
object JvmAgentClient {
  @volatile private var cfg: Option[JvmAgentClientConfig] = None

  def configure(config: JvmAgentClientConfig): Unit =
    cfg = Some(config)

  def configure(
    component: String,
    golemCli: String = "golem-cli",
    golemCliFlags: Vector[String] = Vector("--local")
  ): Unit =
    configure(JvmAgentClientConfig(component = component, golemCli = golemCli, golemCliFlags = golemCliFlags))

  private def configOrThrow: JvmAgentClientConfig =
    cfg.getOrElse(
      throw new IllegalStateException(
        "JvmAgentClient is not configured. Call cloud.golem.runtime.rpc.jvm.JvmAgentClient.configure(...) first."
      )
    )

  def connect[Trait](
    plan: AgentClientPlan[Trait, ?],
    ctorArgs: Any
  )(implicit ev: Trait <:< AnyRef): Trait = {
    val cfg0         = configOrThrow
    val agentType    = plan.traitName
    val ctorRendered =
      ctorArgs match {
        case () => ""
        case other =>
          WaveTextCodec.encodeArg(other) match {
            case Left(err)  => throw new IllegalArgumentException(err)
            case Right(txt) => txt
          }
      }
    val ctorPart = s"($ctorRendered)"
    val agentId  = s"${cfg0.component}/$agentType$ctorPart"
    val handler  = new CliInvocationHandler(cfg0, agentId, plan)
    val iface    = java.lang.Class.forName(plan.traitClassName)
    Proxy
      .newProxyInstance(
        iface.getClassLoader,
        Array(iface),
        handler
      )
      .asInstanceOf[Trait]
  }

  private final class CliInvocationHandler[Trait](
    cfg: JvmAgentClientConfig,
    agentId: String,
    plan: AgentClientPlan[Trait, ?]
  ) extends InvocationHandler {
    private implicit val ec: ExecutionContext = ExecutionContext.global

    override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
      val name = method.getName

      // Object methods
      if (name == "toString" && method.getParameterCount == 0) return s"JvmAgentClientProxy($agentId)"
      if (name == "hashCode" && method.getParameterCount == 0) return Integer.valueOf(agentId.hashCode)
      if (name == "equals" && method.getParameterCount == 1)
        return java.lang.Boolean.valueOf {
          val other = if (args == null || args.length == 0) null else args(0)
          proxy.asInstanceOf[AnyRef].eq(other)
        }

      // Convert scala method name to WIT function id:
      // full id: "<component>/<agent-type>.{<kebab-method>}"
      val methodPlanFn =
        plan.methods
          .collectFirst { case p if p.metadata.name == name => p.functionName }
          .getOrElse(s"${plan.traitName}.{${kebab(name)}}")
      val fn = s"${cfg.component}/$methodPlanFn"

      // Render args as wave literals for golem-cli
      val payloads =
        Option(args).toVector.flatMap(_.toVector).map { v =>
          WaveTextCodec.encodeArg(v) match {
            case Left(err)  => throw new IllegalArgumentException(err)
            case Right(txt) => txt
          }
        }

      // Only Future[...] methods are supported here (sufficient for quickstart).
      val returnType = method.getGenericReturnType
      val rtName     = returnType.getTypeName
      if (!rtName.startsWith("scala.concurrent.Future")) {
        throw new UnsupportedOperationException(s"JVM client only supports Future[...] methods (found: $rtName on $name)")
      }

      val fut: Future[Any] =
        Future {
          val cliBase = Vector("env", "-u", "ARGV0", cfg.golemCli) ++ cfg.golemCliFlags
          val cmd     = cliBase ++ Vector("--yes", "agent", "invoke", agentId, fn) ++ payloads

          val out = GolemCliProcess.run(new java.io.File("."), cmd) match {
            case Left(err)  => throw new RuntimeException(err)
            case Right(out) => out
          }

          val wave = WaveTextCodec.parseLastWaveResult(out).getOrElse {
            throw new RuntimeException(s"Could not find WAVE result in golem-cli output:\n$out")
          }

          WaveTextCodec.decodeWaveAny(wave).fold(err => throw new RuntimeException(err), identity)
        }

      fut.asInstanceOf[AnyRef]
    }

    private def kebab(s: String): String =
      s.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase
  }
}