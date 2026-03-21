package golem.host.js

import scala.scalajs.js

private[golem] object JsShape {
  def tagOnly[T <: js.Object](tag: String): T =
    js.Dynamic.literal("tag" -> tag).asInstanceOf[T]

  def tagged[T <: js.Object](tag: String, value: js.Any): T = {
    val obj = js.Dynamic.literal("tag" -> tag)
    obj.updateDynamic("val")(value)
    obj.asInstanceOf[T]
  }

  def taggedOptional[T <: js.Object](tag: String, value: js.UndefOr[js.Any]): T = {
    val obj = js.Dynamic.literal("tag" -> tag)
    value.foreach(v => obj.updateDynamic("val")(v))
    obj.asInstanceOf[T]
  }
}
