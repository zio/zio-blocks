package golem.wasi

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facade for `wasi:cli/environment@0.2.3`.
 */
object Environment {
  @js.native
  @JSImport("wasi:cli/environment@0.2.3", JSImport.Namespace)
  private object EnvModule extends js.Object

  def raw: Any =
    EnvModule
}
