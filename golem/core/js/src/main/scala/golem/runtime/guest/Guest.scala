package golem.runtime.guest

import golem.runtime.autowire.AgentRegistry
import golem.runtime.util.FutureInterop

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

/**
 * Scala.js implementation of the mandatory Golem JS guest exports.
 *
 * The Scala application code is responsible for registering agent definitions
 * into AgentRegistry at module initialization time (typically via
 * `AgentImplementation.register[...]` calls in a small exported value whose
 * initializer runs on module load).
 */
object Guest {
  private var resolved: js.UndefOr[Resolved] = js.undefined
  private final case class Resolved(defn: golem.runtime.autowire.AgentDefinition[Any], instance: Any)

  private def agentError(tag: String, message: String): js.Dynamic =
    js.Dynamic.literal("tag" -> tag, "val" -> message)

  private def invalidType(message: String): js.Dynamic =
    agentError("invalid-type", message)

  private def invalidAgentId(message: String): js.Dynamic =
    agentError("invalid-agent-id", message)

  private def customError(message: String): js.Dynamic = {
    val witValue = golem.runtime.autowire.WitValueBuilder.build(
      golem.data.DataType.StringType,
      golem.data.DataValue.StringValue(message)
    ) match {
      case Left(_)    => js.Dynamic.literal("tag" -> "string", "val" -> message)
      case Right(vit) => vit
    }

    val node =
      js.Dynamic.literal(
        "name"  -> (js.undefined: js.UndefOr[String]),
        "owner" -> (js.undefined: js.UndefOr[String]),
        "type"  -> js.Dynamic.literal("tag" -> "prim-string-type")
      )
    val witType = js.Dynamic.literal("nodes" -> js.Array(node))

    js.Dynamic.literal(
      "tag" -> "custom-error",
      "val" -> js.Dynamic.literal(
        "value" -> witValue,
        "typ"   -> witType
      )
    )
  }

  private def asAgentError(err: Any, fallbackTag: String): js.Dynamic =
    if (err == null) customError("null")
    else {
      val dyn       = err.asInstanceOf[js.Dynamic]
      val hasTagVal =
        try !js.isUndefined(dyn.selectDynamic("tag")) && !js.isUndefined(dyn.selectDynamic("val"))
        catch { case _: Throwable => false }

      if (hasTagVal) dyn
      else
        err match {
          case s: String => agentError(fallbackTag, s)
          case other     => customError(String.valueOf(other))
        }
    }

  private def normalizeMethodName(methodName: String): String =
    if (methodName.contains(".{") && methodName.endsWith("}")) {
      val start = methodName.indexOf(".{") + 2
      methodName.substring(start, methodName.length - 1)
    } else methodName

  private def initialize(agentTypeName: String, input: js.Dynamic): js.Promise[Unit] =
    if (!js.isUndefined(resolved)) {
      js.Promise.reject(customError("Agent is already initialized in this container")).asInstanceOf[js.Promise[Unit]]
    } else {
      AgentRegistry.get(agentTypeName) match {
        case None =>
          js.Promise.reject(invalidType("Invalid agent '" + agentTypeName + "'")).asInstanceOf[js.Promise[Unit]]
        case Some(defnAny) =>
          // Avoid calling `.then` directly (Scala 3 scaladoc / TASTy reader can error on it during `doc`).
          val initPromise              = defnAny.initializeAny(input)
          val initFuture: Future[Unit] =
            FutureInterop
              .fromPromise(initPromise)
              .map { inst =>
                resolved = Resolved(defnAny, inst)
                ()
              }
              .recoverWith { case err =>
                Future.failed(scala.scalajs.js.JavaScriptException(asAgentError(err, "invalid-input")))
              }
          FutureInterop.toPromise(initFuture).asInstanceOf[js.Promise[Unit]]
      }
    }

  private def invoke(methodName: String, input: js.Dynamic): js.Promise[js.Dynamic] =
    if (js.isUndefined(resolved)) {
      js.Promise.reject(invalidAgentId("Agent is not initialized")).asInstanceOf[js.Promise[js.Dynamic]]
    } else {
      val r                                                      = resolved.asInstanceOf[Resolved]
      val mn                                                     = normalizeMethodName(methodName)
      val onRejected: js.Function1[Any, js.Thenable[js.Dynamic]] =
        js.Any.fromFunction1((err: Any) =>
          js.Promise.reject(asAgentError(err, "invalid-method")).asInstanceOf[js.Thenable[js.Dynamic]]
        )
      r.defn
        .invokeAny(r.instance, mn, input)
        .`catch`[js.Dynamic](onRejected)
    }

  private def getDefinition(): js.Promise[js.Dynamic] =
    if (js.isUndefined(resolved)) {
      js.Promise.reject(invalidAgentId("Agent is not initialized")).asInstanceOf[js.Promise[js.Dynamic]]
    } else {
      js.Promise.resolve[js.Dynamic](resolved.asInstanceOf[Resolved].defn.agentType)
    }

  private def discoverAgentTypes(): js.Promise[js.Array[js.Dynamic]] =
    try {
      val arr = new js.Array[js.Dynamic]()
      AgentRegistry.all.foreach(d => arr.push(d.agentType))
      js.Promise.resolve[js.Array[js.Dynamic]](arr)
    } catch {
      case t: Throwable =>
        js.Promise.reject(asAgentError(t.toString, "custom-error")).asInstanceOf[js.Promise[js.Array[js.Dynamic]]]
    }

  @JSExportTopLevel("guest")
  val guest: js.Dynamic =
    js.Dynamic.literal(
      "initialize"         -> ((agentTypeName: String, input: js.Dynamic) => initialize(agentTypeName, input)),
      "invoke"             -> ((methodName: String, input: js.Dynamic) => invoke(methodName, input)),
      "getDefinition"      -> (() => getDefinition()),
      "discoverAgentTypes" -> (() => discoverAgentTypes())
    )
}
