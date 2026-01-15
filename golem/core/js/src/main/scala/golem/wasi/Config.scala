package golem.wasi

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facade for WASI config store (`wasi:config/store@0.2.0-draft`).
 */
object Config {
  @js.native
  @JSImport("wasi:config/store@0.2.0-draft", JSImport.Namespace)
  private object StoreModule extends js.Object

  def raw: Any =
    StoreModule
}
