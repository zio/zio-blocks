package golem.host

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facade for `golem:api/context@1.3.0`.
 *
 * This is intentionally a thin layer: it exposes the host module and a few typed entrypoints.
 * Resource/value shapes follow the JS/WIT binding conventions and may evolve with the host.
 */
object ContextApi {

  final class Span private[golem] (private[golem] val underlying: js.Any) extends AnyVal
  final class InvocationContext private[golem] (private[golem] val underlying: js.Any) extends AnyVal

  @js.native
  @JSImport("golem:api/context@1.3.0", JSImport.Namespace)
  private object ContextModule extends js.Object {
    def startSpan(name: String): js.Any                                              = js.native
    def currentContext(): js.Any                                                     = js.native
    def allowForwardingTraceContextHeaders(allow: Boolean): Boolean                  = js.native
  }

  def startSpan(name: String): Span =
    new Span(ContextModule.startSpan(name))

  def currentContext(): InvocationContext =
    new InvocationContext(ContextModule.currentContext())

  def allowForwardingTraceContextHeaders(allow: Boolean): Boolean =
    ContextModule.allowForwardingTraceContextHeaders(allow)

  /** Low-level access to the imported host module (for forward compatibility). */
  def raw: Any =
    ContextModule
}

