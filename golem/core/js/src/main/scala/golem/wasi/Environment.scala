package golem.wasi

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facade for `wasi:cli/environment@0.2.3`.
 */
object Environment {
  @js.native
  @JSImport("wasi:cli/environment@0.2.3", JSImport.Namespace)
  private object EnvModule extends js.Object {
    def getEnvironment(): js.Array[js.Tuple2[String, String]] = js.native
  }

  def raw: Any =
    EnvModule

  def getEnvironment(): Map[String, String] =
    EnvModule
      .getEnvironment()
      .toSeq
      .map { kv =>
        kv._1 -> kv._2
      }
      .toMap
}
