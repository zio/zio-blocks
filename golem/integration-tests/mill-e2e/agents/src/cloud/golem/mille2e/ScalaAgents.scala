package cloud.golem.mille2e

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/**
 * Minimal Scala.js exports consumed by the plugin-generated bridge manifest.
 *
 * The bridge registers `@agent` classes whose implementations delegate to the functions here.
 */
@JSExportTopLevel("scalaAgents")
object ScalaAgents {

  @JSExport
  def newNameAgent(): js.Dynamic =
    js.Dynamic.literal(
      reverse = (input: js.Dynamic) => {
        val value = input.selectDynamic("value").asInstanceOf[String]
        val out   = js.Dynamic.literal("value" -> value.reverse)
        js.Dynamic.global.Promise.resolve(out).asInstanceOf[js.Promise[js.Dynamic]]
      }
    )

  @JSExport
  def newComplexShard(tableName: String, shardId: Double): js.Dynamic = {
    val store: mutable.LinkedHashMap[String, String] = mutable.LinkedHashMap.empty

    def fullKey(key: String): String =
      s"$tableName:$shardId:$key"

    js.Dynamic.literal(
      ping = () =>
        js.Dynamic.global.Promise.resolve("pong").asInstanceOf[js.Promise[String]],
      echo = (value: String) =>
        js.Dynamic.global.Promise.resolve(value).asInstanceOf[js.Promise[String]],
      echoOpt = (value: js.Any) => {
        val out: js.Any =
          if (js.isUndefined(value) || value == null) null
          else value.asInstanceOf[Double]
        js.Dynamic.global.Promise.resolve(out).asInstanceOf[js.Promise[js.Any]]
      },
      combine = (prefix: String, n: Double) =>
        js.Dynamic.global.Promise.resolve(s"$prefix-${n.toInt}").asInstanceOf[js.Promise[String]],
      set = (input: js.Dynamic) => {
        val key   = input.selectDynamic("key").asInstanceOf[String]
        val value = input.selectDynamic("value").asInstanceOf[String]
        store.update(fullKey(key), value)
        ()
      },
      get = (input: js.Dynamic) => {
        val key = input.selectDynamic("key").asInstanceOf[String]
        val out =
          store.get(fullKey(key)) match {
            case Some(v) =>
              js.Dynamic.literal("value" -> v, "status" -> "Ok")
            case None =>
              js.Dynamic.literal("value" -> null, "status" -> "Missing")
          }
        js.Dynamic.global.Promise.resolve(out).asInstanceOf[js.Promise[js.Dynamic]]
      },
      listKeys = () =>
        js.Dynamic.global.Promise.resolve(store.keys.toJSArray).asInstanceOf[js.Promise[js.Array[String]]],
      stats = () => {
        val out =
          js.Dynamic.literal(
            "count" -> store.size.toDouble,
            "keys"  -> store.keys.toJSArray
          )
        js.Dynamic.global.Promise.resolve(out).asInstanceOf[js.Promise[js.Dynamic]]
      }
    )
  }
}


