package golem.runtime.guest

import golem.host.js._
import golem.runtime.autowire.{AgentRegistry, WitValueBuilder}
import golem.runtime.util.FutureInterop

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.annotation.unused
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

  private def invalidType(message: String): JsAgentError =
    JsAgentError.invalidType(message)

  private def invalidAgentId(message: String): JsAgentError =
    JsAgentError.invalidAgentId(message)

  private def customError(message: String): JsAgentError = {
    val witValue: JsWitValue = WitValueBuilder.build(
      golem.data.DataType.StringType,
      golem.data.DataValue.StringValue(message)
    ) match {
      case Left(_)  => JsWitValue(js.Array(JsWitNode.primString(message)))
      case Right(v) => v
    }

    val witType: JsWitType = JsWitType(js.Array(
      JsNamedWitTypeNode(JsWitTypeNode.primStringType)
    ))

    JsAgentError.customError(JsValueAndType(witValue, witType))
  }

  private def asAgentError(err: Any, fallbackTag: String): JsAgentError =
    if (err == null) customError("null")
    else {
      val dyn       = err.asInstanceOf[js.Dynamic]
      val hasTagVal =
        try !js.isUndefined(dyn.selectDynamic("tag")) && !js.isUndefined(dyn.selectDynamic("val"))
        catch { case _: Throwable => false }

      if (hasTagVal) dyn.asInstanceOf[JsAgentError]
      else
        err match {
          case s: String =>
            fallbackTag match {
              case "invalid-input"    => JsAgentError.invalidInput(s)
              case "invalid-method"   => JsAgentError.invalidMethod(s)
              case "invalid-type"     => JsAgentError.invalidType(s)
              case "invalid-agent-id" => JsAgentError.invalidAgentId(s)
              case _                  => customError(s)
            }
          case other => customError(String.valueOf(other))
        }
    }

  private def normalizeMethodName(methodName: String): String =
    if (methodName.contains(".{") && methodName.endsWith("}")) {
      val start = methodName.indexOf(".{") + 2
      methodName.substring(start, methodName.length - 1)
    } else methodName

  private def initialize(agentTypeName: String, input: js.Dynamic, @unused principal: js.Dynamic): js.Promise[Unit] =
    if (!js.isUndefined(resolved)) {
      js.Promise.reject(customError("Agent is already initialized in this container")).asInstanceOf[js.Promise[Unit]]
    } else {
      AgentRegistry.get(agentTypeName) match {
        case None =>
          js.Promise.reject(invalidType("Invalid agent '" + agentTypeName + "'")).asInstanceOf[js.Promise[Unit]]
        case Some(defnAny) =>
          // Avoid calling `.then` directly (Scala 3 scaladoc / TASTy reader can error on it during `doc`).
          val initPromise              = defnAny.initializeAny(input.asInstanceOf[JsDataValue])
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

  private def invoke(methodName: String, input: js.Dynamic, @unused principal: js.Dynamic): js.Promise[js.Dynamic] =
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
        .invokeAny(r.instance, mn, input.asInstanceOf[JsDataValue])
        .asInstanceOf[js.Promise[js.Dynamic]]
        .`catch`[js.Dynamic](onRejected)
    }

  private def getDefinition(): js.Promise[js.Any] =
    if (js.isUndefined(resolved)) {
      js.Promise.reject(invalidAgentId("Agent is not initialized")).asInstanceOf[js.Promise[js.Any]]
    } else {
      js.Promise.resolve[js.Any](resolved.asInstanceOf[Resolved].defn.agentType.asInstanceOf[js.Any])
    }

  private def discoverAgentTypes(): js.Promise[js.Array[js.Any]] =
    try {
      val arr = new js.Array[js.Any]()
      AgentRegistry.all.foreach(d => arr.push(d.agentType.asInstanceOf[js.Any]))
      js.Promise.resolve[js.Array[js.Any]](arr)
    } catch {
      case t: Throwable =>
        js.Promise.reject(asAgentError(t.toString, "custom-error")).asInstanceOf[js.Promise[js.Array[js.Any]]]
    }

  @JSExportTopLevel("guest")
  val guest: js.Dynamic =
    js.Dynamic.literal(
      "initialize"         -> ((agentTypeName: String, input: js.Dynamic, principal: js.Dynamic) => initialize(agentTypeName, input, principal)),
      "invoke"             -> ((methodName: String, input: js.Dynamic, principal: js.Dynamic) => invoke(methodName, input, principal)),
      "getDefinition"      -> (() => getDefinition()),
      "discoverAgentTypes" -> (() => discoverAgentTypes())
    )
}
